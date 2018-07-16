package com.otcdlink.chiron.integration.drill;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.otcdlink.chiron.ExtendedChange;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.downend.CommandInFlightStatus;
import com.otcdlink.chiron.downend.CommandTransceiver;
import com.otcdlink.chiron.downend.Downend;
import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.downend.SignonMaterializer;
import com.otcdlink.chiron.downend.Tracker;
import com.otcdlink.chiron.fixture.tcp.TcpTransitServer;
import com.otcdlink.chiron.fixture.tcp.http.ConnectProxy;
import com.otcdlink.chiron.fixture.tcp.http.HttpProxy;
import com.otcdlink.chiron.integration.drill.eventloop.PassivatedEventLoopGroup;
import com.otcdlink.chiron.integration.drill.fakeend.FakeDownend;
import com.otcdlink.chiron.integration.drill.fakeend.FakeUpend;
import com.otcdlink.chiron.integration.echo.EchoCodecFixture;
import com.otcdlink.chiron.integration.echo.EchoDownwardCommandCrafter;
import com.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import com.otcdlink.chiron.integration.echo.EchoUpwardCommandCrafter;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.middle.AutosignerFixture;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.mockster.Mockster;
import com.otcdlink.chiron.toolbox.CollectingException;
import com.otcdlink.chiron.toolbox.TcpPortBooker;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import com.otcdlink.chiron.toolbox.concurrent.FutureTools;
import com.otcdlink.chiron.toolbox.concurrent.Lazy;
import com.otcdlink.chiron.toolbox.netty.Hypermessage;
import com.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;
import com.otcdlink.chiron.toolbox.text.Plural;
import com.otcdlink.chiron.upend.TimeKit;
import com.otcdlink.chiron.upend.UpendConnector;
import com.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.otcdlink.chiron.conductor.ConductorTools.fullWireEncode;
import static com.otcdlink.chiron.integration.drill.FeatureUnavailableException.checkFeatureAvailable;

public class RealConnectorDrill implements ConnectorDrill {

  private static final Logger LOGGER = LoggerFactory.getLogger( RealConnectorDrill.class ) ;

  private final DrillBuilder drillBuilder ;

  /**
   * Needed to perform some operations out of test thread.
   */
  private final ExecutorService administrativeExecutorService =
      ExecutorTools.singleThreadedExecutorServiceFactory( getClass().getSimpleName() + "-admin" )
          .create()
  ;

  final BlockingQueue< PassivatedEventLoopGroup.TaskCapture > taskCaptureQueue =
      new LinkedBlockingQueue<>() ;


  private final NioEventLoopGroup sharedEventLoopGroup =
      new PassivatedEventLoopGroup( ExecutorTools.newThreadFactory( "drill" ), taskCaptureQueue::add ) ;
//      new NioEventLoopGroup( 1, ExecutorTools.newThreadFactory( "drill" ) ) ;
  private final TimeKit< UpdateableClock > timeKit = TimeKit.instrumentedTimeKit( Stamp.FLOOR ) ;
  private final InternetAddressPack internetAddressPack ;
  private final Mockster mockster ;

  private final CollectingException.Collector exceptionCollector =
      CollectingException.newCollector() ;

  private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler =
      ( t, e ) -> {
        exceptionCollector.collect( e ) ;
        LOGGER.error( "Uncaught exception in " + t + ": ", e ) ;
      } ;



  private final ForCommandTransceiver forCommandTransceiver ;
  private final ForSimpleDownend forSimpleDownend ;
  private final ForFakeDownend forFakeDownend ;
  private final List< Downend > downends = Collections.synchronizedList( new ArrayList<>() ) ;

  private final ForUpendConnector forUpendConnector;
  private final ForFakeUpend forFakeUpend ;

  private final ConnectProxy connectProxy ;

//  private final ForSimpleDownend.ChangeAsConstant simpleDownendConnectoerChangeAsConstant ;
//  private final ForCommandTransceiver.ChangeAsConstant commandTransceiverChangeAsContant ;

  /**
   * @see #changeTimeBoundary(TimeBoundary.ForAll)
   */
  private TimeBoundary.ForAll timeBoundary ;

  RealConnectorDrill( final DrillBuilder drillBuilder ) throws CollectingException {
    this.drillBuilder = checkNotNull( drillBuilder ) ;

    this.mockster = new Mockster(
        drillBuilder.mocksterTimeoutDuration,
        drillBuilder.mocksterTimeoutUnit
    ) ;

    internetAddressPack = InternetAddressPack.newOnLocalhost(
        drillBuilder.tls,
        TcpPortBooker.THIS.find(),
        "/websocket",
        drillBuilder.proxy ? TcpPortBooker.THIS.find() : null
    ) ;

    if( drillBuilder.proxy ) {
      this.connectProxy = new ConnectProxy(
          internetAddressPack.internetProxyAccess().hostPort.asInetSocketAddressQuiet(),
          null,
          sharedEventLoopGroup,
          TcpTransitServer.Watcher.NULL,
          TcpTransitServer.PipelineConfigurator.Factory.NULL_FACTORY,
          10
      ) ;
    } else {
      this.connectProxy = null ;
    }

    timeBoundary = drillBuilder.timeBoundary == null ?
        TimeBoundary.NOPE : drillBuilder.timeBoundary ;

    switch( drillBuilder.forUpend.kind ) {
      case REAL :
        forUpendConnector = new DefaultForUpendConnector( sharedEventLoopGroup ) ;
        forFakeUpend = null ;
        break ;
      case FAKE :
        forUpendConnector = null ;
        forFakeUpend = new DefaultForFakeUpend() ;
        break ;
      default :
        throw new IllegalArgumentException( "Unsupported: " + drillBuilder.forUpend.kind ) ;
    }

    switch( drillBuilder.forDownend.kind ) {
      case DOWNEND_CONNECTOR :
        forSimpleDownend = new DefaultForSimpleDownend( sharedEventLoopGroup ) ;
        forCommandTransceiver = null ;
        forFakeDownend = null ;
        break ;
      case COMMAND_TRANSCEIVER :
        forSimpleDownend = null ;
        forCommandTransceiver = new DefaultForCommandTransceiver( sharedEventLoopGroup ) ;
        forFakeDownend = null ;
        break ;
      case FAKE :
        forSimpleDownend = null ;
        forCommandTransceiver = null ;
        forFakeDownend = new DefaultForDownendFake() ;
        break ;
      default :
        throw new IllegalArgumentException( "Unsupported: " + drillBuilder.forDownend.kind ) ;
    }

//    simpleDownendConnectoerChangeAsConstant = newDownendConnectorChangeAsConstant() ;
//    commandTransceiverChangeAsContant = newCommandTransceiverChangeAsConstant() ;

    startEverything() ;
  }


  /**
   * Because of asynchronous start, some startup-related exception can be collected
   * without being thrown before this method returns.
   */
  private void startEverything() throws CollectingException {

    LOGGER.info( "Starting everything in " + this + " ..." );

    final boolean startUpend = drillBuilder.forUpend.automaticLifecycle.start ;
    final boolean startDownend = drillBuilder.forDownend.automaticLifecycle.start ;
    final boolean realUpend = forUpendConnector != null ;
    final boolean realDownend = forSimpleDownend != null || forCommandTransceiver != null ;
    final boolean complexStart = startUpend && realDownend && startDownend ;

    if( connectProxy != null ) {
      connectProxy.start().join() ;
    }

    if( forFakeUpend != null && startUpend && ! complexStart ) {
      // No mock will be waiting so we can be synchronous.
      // Moreover Downend may expect Upend to be available past this point.
      forFakeUpend.start().join() ;
    }

    if( realUpend && startUpend && ! complexStart ) {
      // No mock will be waiting so we can be synchronous.
      forUpendConnector.start().join() ;
    }

    if( forSimpleDownend != null && startDownend && ! complexStart ) {
      // Be asynchronous, Mocks will be waiting for their verification.
      runAsynchronouslyInAdministrativeExecutor(
          ( ( DefaultForSimpleDownend ) forSimpleDownend ).connector::start ) ;
      final ForSimpleDownend.ChangeAsConstant changeAsConstant =
          forSimpleDownend.changeAsConstant() ;
      forSimpleDownend.changeWatcherMock().stateChanged( changeAsConstant.connecting ) ;
      forSimpleDownend.changeWatcherMock().stateChanged( changeAsConstant.connected ) ;
    }

    if( forCommandTransceiver != null && startDownend && ! complexStart ) {
      // Be asynchronous, Mocks will be waiting for their verification.
      runAsynchronouslyInAdministrativeExecutor( () ->
          ( ( DefaultForCommandTransceiver ) forCommandTransceiver ).connector.start() ) ;
    }

    if( forFakeDownend != null && startDownend ) {
      // Be all synchronous, no Mock waiting.
      forFakeDownend.start().join() ;
      forFakeDownend.connect() ;
    }

    if( complexStart ) {
      if( realUpend ) {
        exceptionCollector.execute( () -> play( SketchLibrary.START_AUTHENTICATED ) ) ;
      } else {
        exceptionCollector.execute( () -> play( SketchLibrary.START_WITH_FAKE_UPEND ) ) ;
      }
    }

    exceptionCollector.throwIfAny( "Could not start" ) ;

    LOGGER.info( "Started everything from " + this + "." );
  }

  @Override
  public void runOutOfVerifierThread( Runnable runnable ) {
    mockster.runOutOfVerifierThread( runnable ) ;
  }

// ==============
// ConnectorDrill
// ==============


  @Override
  public DrillBuilder builderBuiltWith() {
    return drillBuilder ;
  }

  @Override
  public InternetAddressPack internetAddressPack() {
    return internetAddressPack ;
  }

  @Override
  public TimeKit< UpdateableClock > timeKit() {
    return timeKit ;
  }

  @Override
  public void processNextTaskCapture(
      Function< Downend.ScheduledInternally, TaskExecution > validityChecker
  ) {
    try {
      final PassivatedEventLoopGroup.TaskCapture taskCapture = taskCaptureQueue.take() ;
      if( taskCapture.runnable instanceof Downend.ScheduledInternally ) {
        final TaskExecution taskExecution = validityChecker.apply(
            ( Downend.ScheduledInternally ) taskCapture.runnable ) ;
        switch( taskExecution ) {

          case EXECUTE :
            LOGGER.info( "Obtained " + taskCapture + ", executing ..." ) ;
            taskCapture.submitNow() ;
            return ;
          case SKIP:
            LOGGER.info( "Skipping " + taskCapture + "." ) ;
            return ;
          default :
            break ;
        }
      }
      throw new IllegalStateException( "Unexpected: " + taskCapture ) ;
    } catch( InterruptedException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  @Override
  public void dumpTaskCapture() {
    final ImmutableList< PassivatedEventLoopGroup.TaskCapture > taskCaptures =
        taskCaptureQueue.stream().collect( ImmutableList.toImmutableList() ) ;
    final String taskCaptureClassName =
        PassivatedEventLoopGroup.TaskCapture.class.getSimpleName() ;
    if( taskCaptures.isEmpty() ) {
      LOGGER.info( "No " + taskCaptureClassName + " to dump." ) ;
    } else {
      LOGGER.info( "Dumping " + taskCaptures.size() + " " +
          Plural.s( taskCaptureClassName, taskCaptures.size() ) ) ;
      taskCaptures.forEach( taskCapture ->
          LOGGER.info( taskCapture + ":" + Joiner.on( "\n  " )
              .join( taskCapture.creationPoint() ) ) ) ;
    }
  }

  @Override
  public ForSimpleDownend forSimpleDownend() {
    return checkFeatureAvailable( forSimpleDownend, DownendConnector.class.getSimpleName() ) ;
  }

  @Override
  public ForCommandTransceiver forCommandTransceiver() {
    return checkFeatureAvailable(
        forCommandTransceiver, CommandTransceiver.class.getSimpleName() ) ;
  }

  @Override
  public ForFakeDownend forFakeDownend() {
    return checkFeatureAvailable( forFakeDownend, FakeDownend.class.getSimpleName() ) ;
  }

  @Override
  public ForUpendConnector forUpendConnector() {
    return checkFeatureAvailable( forUpendConnector, UpendConnector.class.getSimpleName() ) ;
  }

  @Override
  public ForFakeUpend forFakeUpend() {
    return checkFeatureAvailable( forFakeUpend, FakeUpend.class.getSimpleName() ) ;
  }

  @Override
  public HttpProxy httpProxy() {
    return checkFeatureAvailable( connectProxy, ConnectProxy.class.getSimpleName() ) ;
  }

  @Override
  public void restartHttpProxy() {
    httpProxy().stop().join() ;
    httpProxy().start().join() ;
  }

  @Override
  public void changeTimeBoundary( final TimeBoundary.ForAll timeBoundary ) {
    this.timeBoundary = checkNotNull( timeBoundary ) ;
    forUpendConnector().changeTimeBoundary( timeBoundary ) ;
  }

  private ConnectionDescriptor connectionDescriptor() {
    return new ConnectionDescriptor(
        "someVersion",
        authentication().authenticating,
        timeBoundary()
    ) ;
  }

  @Override
  public void play( final Sketch sketch ) throws Exception {
    checkFeatureAvailable( sketch.upendKindRequirements().contains( upendKind() ),
        upendKind() + " not in " + sketch.upendKindRequirements() ) ;
    checkFeatureAvailable( sketch.downendKindRequirements().contains( downendKind() ),
        downendKind() + " not in " + sketch.downendKindRequirements() ) ;
    checkFeatureAvailable( sketch.authenticationRequirements().contains( authentication() ),
        authentication() + " not in " + sketch.authenticationRequirements() ) ;
    LOGGER.info( "Playing " + sketch + " ..." ) ;
    sketch.run( this ) ;
  }

  @Override
  public void close() throws CollectingException {

    LOGGER.info( "Closing everything in " + this + " ..." ) ;

    final boolean stopUpend = drillBuilder.forUpend.automaticLifecycle.stop ;
    final boolean stopDownend = drillBuilder.forDownend.automaticLifecycle.stop ;
    final boolean realUpend = forUpendConnector != null ;
    final boolean realDownend = forSimpleDownend != null || forCommandTransceiver != null ;
    final boolean complexStop = realUpend && stopUpend && realDownend && stopDownend ;


    if( forSimpleDownend != null && stopDownend && ! complexStop ) {
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant =
          forSimpleDownend().changeAsConstant() ;
      forSimpleDownend().stop() ;
      forSimpleDownend().changeWatcherMock().stateChanged( changeAsConstant.stopping ) ;
      forSimpleDownend().changeWatcherMock().stateChanged( changeAsConstant.stopped ) ;
    }

    if( forCommandTransceiver != null && stopDownend && ! complexStop ) {
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant =
          forCommandTransceiver().changeAsConstant() ;
      forCommandTransceiver().stop() ;
      forCommandTransceiver().changeWatcherMock().stateChanged( changeAsConstant.stopping ) ;
      forCommandTransceiver().changeWatcherMock().stateChanged( changeAsConstant.stopped ) ;
    }

    if( forFakeDownend != null && stopDownend ) {
      runSynchronouslyInAdministrativeExecutor( forFakeDownend::stop ) ;
    }

    if( forFakeUpend != null && stopUpend ) {
      if( realDownend && stopDownend ) {
        forFakeUpend().duplex().closing().next() ;
      }
      forFakeUpend().stop() ;
    }

    if( forUpendConnector != null && stopUpend && ! complexStop ) {
      runSynchronouslyInAdministrativeExecutor( forUpendConnector::stop ) ;
    }

    if( complexStop ) {
      exceptionCollector.execute( () -> SketchLibrary.STOP_AUTHENTICATED.run( this ) ) ;
    }

    if( connectProxy != null ) {
      connectProxy.stop().join() ;
    }

    // There should be no pending task so we don't wait for them to expire.
    sharedEventLoopGroup.shutdownGracefully( 0, 0, TimeUnit.MILLISECONDS ).syncUninterruptibly() ;

    administrativeExecutorService.shutdown() ;

    exceptionCollector
        .execute( mockster::close )
        .throwIfAny( "Some failure occured during the lifecycle of " + this )
    ;

    LOGGER.info( "Closed everything in " + this + "." ) ;

  }


// =====
// Upend
// =====

  @Override
  public ForUpend.Kind upendKind() {
    return drillBuilder.forUpend.kind ;
  }


  private final class DefaultForUpendConnector implements ForUpendConnector {

    private final UpendConnector< EchoUpwardDuty< Designator >, EchoDownwardDuty< Designator > >
        upendConnector ;

    private final EchoUpwardDuty< Designator > upwardDutyMock = mockster.mock(
        new TypeToken< EchoUpwardDuty< Designator > >() { } ) ;

    private final OutwardSessionSupervisor< Channel, InetAddress > sessionSupervisorMock =
        mockster.mock( new TypeToken< OutwardSessionSupervisor< Channel, InetAddress > >() {} ) ;

    private final EchoDownwardDuty< Designator > downwardDuty ;

    public DefaultForUpendConnector( final EventLoopGroup eventLoopGroup ) {
      final DrillBuilder.ForUpendConnector forUpendConnector = ( DrillBuilder.ForUpendConnector )
          drillBuilder.forUpend ;

      final Designator.Factory designatorFactory = forUpendConnector.authentication.authenticating ?
          timeKit.designatorFactory : new SessionlessDesignatorFactory() ;

      final CommandInterceptor.Factory commandInterceptorFactoryMaybe =
          forUpendConnector.commandInterceptor == null ?
          null :
          CommandInterceptor.Factory.always( forUpendConnector.commandInterceptor )
      ;
      final UpendConnector.Setup< EchoUpwardDuty< Designator > > setup = new UpendConnector.Setup<>(
          eventLoopGroup,
          internetAddressPack.upendListeningSocketAddress(),
          sslEngineFactoryForServer(),
          internetAddressPack.upendWebSocketUriPath(),
          connectionDescriptor().upendVersion,
          forUpendConnector.authentication.authenticating ? sessionSupervisorMock : null,
          command -> command.callReceiver( upwardDutyMock ),
          designatorFactory,
          new EchoCodecFixture.PartialUpendDecoder(),
          forUpendConnector.httpRequestRelayerKind.httpRequestRelayer,
          null,
          commandInterceptorFactoryMaybe,
          timeBoundary(),
          drillBuilder.webSocketFrameSizer
      ) ;
      upendConnector = new UpendConnector<>( setup ) ;
      downwardDuty =
          new EchoDownwardCommandCrafter<>( upendConnector::sendDownward ) ;
    }

    @Override
    public CompletableFuture< Void > start() {
      return ( CompletableFuture< Void > ) upendConnector.start() ;
    }

    @Override
    public CompletableFuture< Void > stop() {
      return ( CompletableFuture< Void > ) upendConnector.stop() ;
    }

    @Override
    public EchoUpwardDuty< Designator > upwardDutyMock() {
      return upwardDutyMock ;
    }

    @Override
    public EchoDownwardDuty< Designator > downwardDuty() {
      return downwardDuty ;
    }

    @Override
    public OutwardSessionSupervisor< Channel, InetAddress > sessionSupervisorMock() {
      return checkFeatureAvailable( sessionSupervisorMock,
          OutwardSessionSupervisor.class.getSimpleName() ) ;
    }

    @Override
    public void changeTimeBoundary( TimeBoundary.ForAll timeBoundary ) {
      upendConnector.timeBoundary( timeBoundary ) ;
    }

    /**
     * Needed when {@link DrillBuilder.ForUpendConnector#authentication} is {@code false},
     * to support a {@code null} {@link Designator#sessionIdentifier}.
     * This implies {@link Designator.Kind} being {@link Designator.Kind#INTERNAL}.
     */
    private class SessionlessDesignatorFactory extends Designator.Factory {
      public SessionlessDesignatorFactory() {
        super( timeKit.stampGenerator ) ;
      }
      @Override
      public Designator upward(
          final Command.Tag tag,
          final SessionIdentifier sessionIdentifier
      ) {
        return DesignatorForger.newForger()
            .session( sessionIdentifier )
            .tag( tag )
            .stamp( uniqueTimestampGenerator.generate() )
            .unchecked()
        ;
      }
    }
  }

  private class DefaultForFakeUpend implements ForFakeUpend {

    private final FakeUpend fakeUpend ;

    public DefaultForFakeUpend() {
      fakeUpend = new FakeUpend(
          internetAddressPack.upendListeningHostAddress(),
          internetAddressPack.upendListeningPort(),
          sslEngineFactoryForServer(),
          connectionDescriptor(),
          uncaughtExceptionHandler
      ) ;
    }

    @Override
    public CompletableFuture< Void > start() {
      return fakeUpend.start() ;
    }

    @Override
    public CompletableFuture< Void > stop() {
      return fakeUpend.stop() ;
    }

    @Override
    public UpendDuplex duplex() {
      return fakeUpend.upendDuplexPack ;
    }
  }


// =======
// Downend
// =======


  @Override
  public ForDownend.Kind downendKind() {
    return drillBuilder.forDownend.kind ;
  }

  private abstract class AbstractForDownend<
      CONNECTOR extends Downend< ENDPOINT_SPECIFIC, ? >,
      ENDPOINT_SPECIFIC
  > {
    protected final EventLoopGroup eventLoopGroup ;
    protected final SignonMaterializer signonMaterializerMock ;
    protected final EchoDownwardDuty< ENDPOINT_SPECIFIC > echoDownwardDutyMock ;

    public AbstractForDownend( final EventLoopGroup eventLoopGroup ) {
      signonMaterializerMock =  mockster.mock( SignonMaterializer.class ) ;
      echoDownwardDutyMock = mockster.mock(
          new TypeToken< EchoDownwardDuty< ENDPOINT_SPECIFIC > >() {} ) ;
      this.eventLoopGroup = eventLoopGroup ;
    }

    protected abstract CONNECTOR connector() ;

    public final CompletableFuture< Void > start() {
      final CompletableFuture< Void > startComplete = new CompletableFuture<>() ;
      runAsynchronouslyInAdministrativeExecutor( () ->
          FutureTools.propagate( connector().start(), startComplete ) ) ;
      return startComplete ;
    }

    public final CompletableFuture< Void > stop() {
      final CompletableFuture< Void > stopComplete = new CompletableFuture<>() ;
      runAsynchronouslyInAdministrativeExecutor( () ->
          FutureTools.propagate( connector().stop(), stopComplete ) ) ;
      return stopComplete ;
    }


    public final SignonMaterializer signonMaterializerMock() {
      return checkFeatureAvailable(
          signonMaterializerMock, SignonMaterializer.class.getSimpleName() ) ;
    }

    public final EchoDownwardDuty< ENDPOINT_SPECIFIC > echoDownwardDutyMock() {
      return echoDownwardDutyMock ;
    }

    public final void applyDirectly(
        final Consumer< CONNECTOR > connectorConsumer,
        final  boolean executeInOperativeThread
    ) {
      if( executeInOperativeThread ) {
        runOutOfVerifierThread( () -> connectorConsumer.accept( connector() ) ) ;
      } else {
        connectorConsumer.accept( connector() ) ;
      }
    }

    public final < T > T applyDirectly( final Function< CONNECTOR, T > connectorTransformer ) {
      return connectorTransformer.apply( connector() ) ;
    }
  }

  private final class DefaultForSimpleDownend
      extends AbstractForDownend<
      DownendConnector<
          Command.Tag,
          EchoDownwardDuty< Command.Tag >,
          EchoUpwardDuty< Command.Tag >
          >,
      Command.Tag
      >
      implements ConnectorDrill.ForSimpleDownend
  {
    private final DownendConnector.ChangeWatcher changeWatcherMock ;

    private final EchoDownwardDuty< Command.Tag > downwardDutyMock ;

    private final DownendConnector<
        Command.Tag,
        EchoDownwardDuty< Command.Tag >,
        EchoUpwardDuty< Command.Tag >
    > connector ;

    private final EchoUpwardCommandCrafter< Command.Tag > upwardCommandCrafter ;

    public DefaultForSimpleDownend( final EventLoopGroup eventLoopGroup ) {
      super( eventLoopGroup ) ;
      changeWatcherMock = mockster.mock( DownendConnector.ChangeWatcher.class ) ;
      downwardDutyMock = mockster.mock( new TypeToken< EchoDownwardDuty< Command.Tag > >() {} ) ;

      final DrillBuilder.ForDownendConnector builderForDownendConnector =
          ( DrillBuilder.ForDownendConnector ) drillBuilder.forDownend ;
      final CommandInterceptor.Factory commandInterceptorFactory =
          builderForDownendConnector.commandInterceptor == null ?
          null :
          CommandInterceptor.Factory.always( builderForDownendConnector.commandInterceptor )
      ;

      final DownendConnector.Setup< Command.Tag, EchoDownwardDuty< Command.Tag > > setup =
          new DownendConnector.Setup<>(
              this.eventLoopGroup,
              internetAddressPack.upendWebSocketUrlWithHttpScheme(),
              internetAddressPack.internetProxyAccess(),
              sslEngineFactoryForClient(),
              timeBoundary(),
              signonMaterializerMock,
              changeWatcherMock,
              new EchoCodecFixture.TagCodec(),
              new EchoCodecFixture.PartialDownendDecoder<>(),
              command -> command.callReceiver( echoDownwardDutyMock ),
              commandInterceptorFactory,
              drillBuilder.webSocketFrameSizer
          )
      ;
      this.connector = new DownendConnector<>( setup ) ;
      this.upwardCommandCrafter = new EchoUpwardCommandCrafter<>( connector::send ) ;
      downends.add( connector ) ;
    }

    @Override
    public DownendConnector<
        Command.Tag,
        EchoDownwardDuty< Command.Tag >,
        EchoUpwardDuty< Command.Tag >
        > connector() {
  return connector ;
    }

    @Override
    public EchoUpwardDuty< Command.Tag > upwardDuty() {
      return upwardCommandCrafter ;
    }

    @Override
    public EchoDownwardDuty< Command.Tag > downwardDutyMock() {
      return downwardDutyMock ;
    }

    @Override
    public DownendConnector.ChangeWatcher changeWatcherMock() {
      return changeWatcherMock ;
    }

    @Override
    public ForSimpleDownend clone() {
      throw new UnsupportedOperationException( "TODO" ) ;
    }

    @Override
    public ChangeAsConstant changeAsConstant() {
      return newCommandTransceiverChangeAsConstant() ;
    }
  }

  private final class DefaultForCommandTransceiver
      extends AbstractForDownend<
          CommandTransceiver<
              EchoDownwardDuty< Tracker >,
              EchoUpwardDuty< Tracker >
          >,
          Tracker
      >
      implements ForCommandTransceiver
  {
    private final Tracker trackerMock ;
    private final CommandTransceiver.ChangeWatcher changeWatcherMock ;
    private final CommandTransceiver.Setup< EchoDownwardDuty< Tracker > > commandTransceiverSetup ;

    private final CommandTransceiver< EchoDownwardDuty< Tracker >, EchoUpwardDuty< Tracker > >
        connector ;

    private final EchoUpwardCommandCrafter< Tracker > upwardCommandCrafter ;
    private final EchoDownwardDuty< Tracker > downwardDutyMock ;


    public DefaultForCommandTransceiver( final EventLoopGroup eventLoopGroup ) {
      super( eventLoopGroup ) ;
      trackerMock = mockster.mock( Tracker.class ) ;
      changeWatcherMock = mockster.mock( CommandTransceiver.ChangeWatcher.class ) ;
      commandTransceiverSetup = new CommandTransceiver.Setup<>(
          timeKit.clock,
          this.eventLoopGroup,
          internetAddressPack.upendWebSocketUrlWithHttpScheme(),
          internetAddressPack.internetProxyAccess(),
          sslEngineFactoryForClient(),
          timeBoundary(),
          signonMaterializerMock,
          changeWatcherMock,
          new EchoCodecFixture.PartialDownendDecoder<>(),
          command -> command.callReceiver( echoDownwardDutyMock ),
          null,
          drillBuilder.webSocketFrameSizer
      ) ;

      connector = new CommandTransceiver<>( commandTransceiverSetup ) ;
      upwardCommandCrafter = new EchoUpwardCommandCrafter<>( connector::send ) ;
      downwardDutyMock = mockster.mock( new TypeToken< EchoDownwardDuty< Tracker > >() {} ) ;

      downends.add( connector ) ;
    }

    @Override
    public CommandTransceiver<
        EchoDownwardDuty< Tracker >, EchoUpwardDuty<Tracker>
        > connector() {
      return connector ;
    }

    @Override
    public EchoUpwardDuty< Tracker > upwardDuty() {
      return upwardCommandCrafter ;
    }

    @Override
    public EchoDownwardDuty< Tracker > downwardDutyMock() {
      return downwardDutyMock ;
    }

    @Override
    public Tracker trackerMock() {
      return trackerMock ;
    }

    @Override
    public Tracker newTrackerMock() {
      return mockster.mock( Tracker.class ) ;
    }

    @Override
    public CommandTransceiver.ChangeWatcher changeWatcherMock() {
      return changeWatcherMock ;
    }

    @Override
    public ChangeAsConstant changeAsConstant() {
      return newCommandTransceiverChangeAsConstant() ;
    }
  }

  private final class DefaultForDownendFake implements ForFakeDownend {

    private final FakeDownend fakeDownend = new FakeDownend(
        internetAddressPack.upendListeningSocketAddress(),
        internetAddressPack.upendWebSocketUriWithHttpScheme(),
        uncaughtExceptionHandler
    ) ;

    @Override
    public CompletableFuture< Void > start() {
      return fakeDownend.start() ;
    }

    @Override
    public CompletableFuture< Void > stop() {
      return fakeDownend.stop() ;
    }

    @Override
    public DownendDuplex duplex() {
      return fakeDownend.downendDuplexPack ;
    }

    @Override
    public void connect() {
      fakeDownend.connect() ;
    }

    @Override
    public NettyHttpClient.CompleteResponse httpRequest( final Hypermessage.Request request ) {
      final NettyHttpClient nettyHttpClient =
          new NettyHttpClient( RealConnectorDrill.this.sharedEventLoopGroup, 1000 ) ;
      nettyHttpClient.start().join() ;
      try {
        return nettyHttpClient.httpRequest( request ).join() ;
      } finally {
        nettyHttpClient.stop().join() ;
      }
    }
  }



// ================
// Boring utilities
// ================

  private void runAsynchronouslyInAdministrativeExecutor( final Runnable runnable ) {
    administrativeExecutorService.submit( () -> {
      try {
        runnable.run() ;
      } catch( Exception e ) {
        exceptionCollector.collect( e ) ;
        Throwables.throwIfUnchecked( e ) ;
        throw new RuntimeException( e ) ;
      }
    } ) ;
  }

  private CompletableFuture< Void > runSynchronouslyInAdministrativeExecutor(
      final Callable< CompletableFuture< Void > > callable
  ) {
    try {
      return administrativeExecutorService.submit( callable ).get() ;
    } catch( InterruptedException e ) {
      throw new RuntimeException( e ) ;
    } catch( ExecutionException e ) {
      exceptionCollector.collect( e ) ;
      Throwables.throwIfUnchecked( e ) ;
      throw new RuntimeException( e ) ;
    }

  }

  public ForSimpleDownend.ChangeAsConstant newDownendConnectorChangeAsConstant() {
    return new ForSimpleDownend.ChangeAsConstant(
        new DownendConnector.Change<>( DownendConnector.State.STOPPED ),
        new DownendConnector.Change<>( DownendConnector.State.CONNECTING ),
        new DownendConnector.Change.SuccessfulConnection( connectionDescriptor() ),
        new DownendConnector.Change<>( DownendConnector.State.STOPPING ),
        new DownendConnector.Change.SuccessfulSignon(
            internetAddressPack.upendWebSocketUrlWithHttpScheme(),
            GOOD_CREDENTIAL.getLogin()
        )
    ) ;
  }

  public ForCommandTransceiver.ChangeAsConstant newCommandTransceiverChangeAsConstant() {
    try {
      final Constructor< ExtendedChange > extendedChangeConstructor =
          ExtendedChange.class.getDeclaredConstructor( ExtendedChange.ExtendedKind.class ) ;
      extendedChangeConstructor.setAccessible( true ) ;
      final Constructor< ExtendedChange.CommandInFlightStatusChange >
          commandInFlightStatusChangeConstructor =
          ExtendedChange.CommandInFlightStatusChange.class
              .getDeclaredConstructor( CommandInFlightStatus.class )
          ;
      commandInFlightStatusChangeConstructor.setAccessible( true ) ;
      final ForSimpleDownend.ChangeAsConstant simpleDownendConnectoerChangeAsConstant =
          newDownendConnectorChangeAsConstant() ;
      return new ForCommandTransceiver.ChangeAsConstant(
          simpleDownendConnectoerChangeAsConstant.stopped,
          simpleDownendConnectoerChangeAsConstant.connecting,
          simpleDownendConnectoerChangeAsConstant.connected,
          simpleDownendConnectoerChangeAsConstant.stopping,
          simpleDownendConnectoerChangeAsConstant.signedIn,
          extendedChangeConstructor
              .newInstance( ExtendedChange.ExtendedKind.FAILED_CONNECTION_ATTEMPT ),
          extendedChangeConstructor
              .newInstance( ExtendedChange.ExtendedKind.NO_SIGNON ),
          CommandInFlightStatus.QUIET,
          CommandInFlightStatus.IN_FLIGHT,
          commandInFlightStatusChangeConstructor.
              newInstance( CommandInFlightStatus.SOME_COMMAND_FAILED )
      ) ;
    } catch(
        NoSuchMethodException |
            IllegalAccessException |
            InvocationTargetException |
            InstantiationException e
        ) {
      throw new Error( e ) ;
    }
  }

  public static EchoDownwardDuty< Command.Tag > asDownwardDuty(
      final Consumer< TextWebSocketFrame > textWebSocketFrameConsumer
  ) {
    return new EchoDownwardCommandCrafter<>(
        command -> textWebSocketFrameConsumer.accept(
            new TextWebSocketFrame( fullWireEncode( command ) ) ) )
        ;
  }

  public static EchoUpwardDuty< Command.Tag > asUpwardDuty(
      final Consumer< TextWebSocketFrame > textWebSocketFrameConsumer
  ) {
    return new EchoUpwardCommandCrafter<>(
        command -> textWebSocketFrameConsumer.accept(
            new TextWebSocketFrame( fullWireEncode( command ) ) ) )
        ;
  }

  @Override
  public Authentication authentication() {
    final DrillBuilder.ForUpendConnector forUpendConnector = drillBuilderForUpendConnectorOrNull() ;
    final DrillBuilder.ForFakeUpend forFakeUpend = drillBuilderForFakeUpendOrNull() ;
    if( forUpendConnector == null ) {
      return forFakeUpend.authentication ;
    } else {
      return forUpendConnector.authentication ;
    }
  }

  private DrillBuilder.ForUpendConnector drillBuilderForUpendConnector() {
    final DrillBuilder.ForUpendConnector forBuilder = drillBuilderForUpendConnectorOrNull() ;
    if( forBuilder == null ) {
      throw new FeatureUnavailableException(
          "No " + DrillBuilder.ForUpendConnector.class.getName() ) ;
    }
    return forBuilder ;
  }

  private DrillBuilder.ForUpendConnector drillBuilderForUpendConnectorOrNull() {
    return drillBuilder.forUpend instanceof DrillBuilder.ForUpendConnector ?
        ( ( DrillBuilder.ForUpendConnector ) drillBuilder.forUpend ) : null ;
  }

  private DrillBuilder.ForFakeUpend drillBuilderForFakeUpendOrNull() {
    return drillBuilder.forUpend instanceof DrillBuilder.ForFakeUpend ?
        ( ( DrillBuilder.ForFakeUpend ) drillBuilder.forUpend ) : null ;
  }

  /**
   * TODO move to {@link DrillBuilder}.
   */
  private void checkAuthenticating() {
    if( ! authentication().authenticating ) {
      throw new FeatureConflictException( "Running " + UpendConnector.class.getSimpleName() +
          " and " + Downend.class.getSimpleName() + " together requires authentication " ) ;
    }
  }



  private TimeBoundary.ForAll timeBoundary() {
    return timeBoundary ;
  }

  private SslEngineFactory.ForClient sslEngineFactoryForClient() {
    return drillBuilder.tls ? SSL_ENGINE_FACTORY_FOR_CLIENT.get() : null ;
  }

  private SslEngineFactory.ForServer sslEngineFactoryForServer() {
    return drillBuilder.tls ? SSL_ENGINE_FACTORY_FOR_SERVER.get() : null ;
  }


  private final Lazy< SslEngineFactory.ForClient > SSL_ENGINE_FACTORY_FOR_CLIENT =
      new Lazy<>( AutosignerFixture::sslEngineFactoryForClient ) ;

  protected final Lazy< SslEngineFactory.ForServer > SSL_ENGINE_FACTORY_FOR_SERVER =
      new Lazy<>( AutosignerFixture::sslEngineFactoryForServer ) ;

//  private static final WebsocketFrameSizer WEBSOCKET_FRAME_SIZER =
//      WebsocketFrameSizer.tightSizer( 8192 ) ;

  static {
    NettyTools.forceNettyClassesToLoad() ;
  }

}
