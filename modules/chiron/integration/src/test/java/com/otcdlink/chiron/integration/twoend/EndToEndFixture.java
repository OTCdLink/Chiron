package com.otcdlink.chiron.integration.twoend;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.otcdlink.chiron.AbstractConnectorFixture;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Command.Tag;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.conductor.Conductor;
import com.otcdlink.chiron.conductor.Extractor;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.downend.CommandTransceiver;
import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.downend.DownendFixture;
import com.otcdlink.chiron.downend.SignonMaterializer;
import com.otcdlink.chiron.integration.echo.DownwardEchoCommand;
import com.otcdlink.chiron.integration.echo.EchoCodecFixture;
import com.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.middle.CommandAssert;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import com.otcdlink.chiron.toolbox.MultiplexingException;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import com.otcdlink.chiron.toolbox.internet.InternetProxyAccess;
import com.otcdlink.chiron.toolbox.internet.LocalAddressTools;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;
import com.otcdlink.chiron.upend.TimeKit;
import com.otcdlink.chiron.upend.UpendConnector;
import com.otcdlink.chiron.upend.UpendConnectorFixture;
import com.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Aggregates common features of {@link DownendFixture} (using common base class) and
 * {@link UpendConnectorFixture} (using delegation to common ancillary classes).
 */
public class EndToEndFixture
    extends AbstractConnectorFixture<
        Tag,
        DownendConnector< Tag, EchoDownwardDuty< Tag >, EchoUpwardDuty< Tag >>,
        DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > >
    >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( EndToEndFixture.class ) ;

  public static final WebsocketFrameSizer WEBSOCKET_FRAME_SIZER =
      WebsocketFrameSizer.tightSizer( 8192 ) ;


  private final UpendConnectorFixture.InitializationGround upendInitializationGround ;

  private UpendConnectorFixture.InitializationResult upendInitializationResult = null ;

  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger() ;
  private final EventLoopGroup upendEventLoopGroup = new NioEventLoopGroup(
      2,
      new ThreadFactoryBuilder()
          .setNameFormat( UpendConnector.class.getSimpleName() + "-" +
              THREAD_COUNTER.getAndIncrement() )
          .setDaemon( true )
          .build()
  ) ;

  private final Conductor.RecordingOnly<
      Command<Designator, EchoUpwardDuty<Designator> >
  > upwardRecordingGuide = new Conductor.RecordingOnly<>() ;

  public EndToEndFixture() {
    upendInitializationGround = new UpendConnectorFixture.InitializationGround(
        TimeKit.instrumentedTimeKit( Stamp.FLOOR ), null ) ;
  }

  @Override
  protected DownendConnector< Tag, EchoDownwardDuty< Tag >, EchoUpwardDuty< Tag > >
  createConnector( final DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > > setup ) {
    return new DownendConnector<>( setup ) ;
  }

  @Override
  protected DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > > newSetup(
      final TimeBoundary.ForAll timeBoundary,
      final SignonMaterializer signonMaterializer,
      final CommandTransceiver.ChangeWatcher stateWatcher,
      final InternetProxyAccess internetProxyAccess,
      final SslEngineFactory.ForClient sslEngineFactory,
      final URL websocketUrl,
      final CommandConsumer< Command< Tag, EchoDownwardDuty< Tag > > > commandReceiver,
      final CommandInterceptor commandInterceptor,
      final WebsocketFrameSizer websocketFrameSizer
  ) {
    return new DownendConnector.Setup<>(
        eventLoopGroup,
        websocketUrl,
        internetProxyAccess,
        sslEngineFactory,
        timeBoundary,
        signonMaterializer,
        stateWatcher,
        new EchoCodecFixture.TagCodec(),
        new EchoCodecFixture.PartialDownendDecoder(),
        commandReceiver,
        CommandInterceptor.Factory.always( commandInterceptor ),
        websocketFrameSizer
    ) ;
  }

// =====
// Setup
// =====

  public UpendConnector.Setup< EchoUpwardDuty< Designator > >
  websocketUnauthenticatedUpendSetup(
      final DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > > downendSetup
  ) {
    return websocketUnauthenticatedUpendSetup(
        upwardRecordingGuide::justRecord,
        null
    ).apply( downendSetup ) ;
  }

  public Function<
      DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > >,
      UpendConnector.Setup< EchoUpwardDuty<Designator> >
  > websocketUnauthenticatedUpendSetup(
      final CommandConsumer< Command< Designator, EchoUpwardDuty< Designator > > > commandConsumer,
      final CommandInterceptor commandInterceptor
  ) {
    return downendSetup -> new UpendConnector.Setup<>(
        upendEventLoopGroup,
        new InetSocketAddress( LocalAddressTools.LOCAL_ADDRESS, downendSetup.port() ),
        downendSetup.usingTls() ? SSL_ENGINE_FACTORY_FOR_SERVER.get() : null,
        WEBSOCKET_PATH,
        UpendConnectorFixture.APPLICATION_VERSION,
        null,
        commandConsumer,
        upendInitializationGround.designatorFactory,
        new EchoCodecFixture.PartialUpendDecoder(),
        null,
        null,
        CommandInterceptor.Factory.always( commandInterceptor ),
        ( TimeBoundary.ForAll ) downendSetup.primingTimeBoundary,
        downendSetup.websocketFrameSizer
    ) ;
  }

  public Function<
      DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > >,
      UpendConnector.Setup< EchoUpwardDuty<Designator> >
  > websocketAuthenticatedUpendSetup(
      final OutwardSessionSupervisor< Channel, InetAddress > outboundSessionSupervisor
  ) {
    return downendSetup -> new UpendConnector.Setup<>(
        upendEventLoopGroup,
        upendListenAddress,
        downendSetup.usingTls() ? SSL_ENGINE_FACTORY_FOR_SERVER.get() : null,
        WEBSOCKET_PATH,
        UpendConnectorFixture.APPLICATION_VERSION,
        outboundSessionSupervisor,
        upwardRecordingGuide::justRecord,
        upendInitializationGround.designatorFactory,
        new EchoCodecFixture.PartialUpendDecoder(),
        null,
        null,
        null,
        ( TimeBoundary.ForAll ) downendSetup.primingTimeBoundary,
        WEBSOCKET_FRAME_SIZER
    ) ;
  }


// =========
// Lifecycle
// =========

  public void initialize(
      final Supplier< DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > > >
          downendSetupSupplier,
      final Function<
          DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > >,
          UpendConnector.Setup< EchoUpwardDuty<Designator> >
      > upendSetupSupplier
  ) throws Exception {
    initialize( downendSetupSupplier, upendSetupSupplier, true, true ) ;
  }

  public void initialize(
      final Supplier< DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > > >
          downendSetupSupplier,
      final Function<
          DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > >,
          UpendConnector.Setup< EchoUpwardDuty< Designator > >
      > upendSetupSupplier,
      final boolean startDownend,
      final boolean startUpend
  ) throws Exception {
    checkState( upendInitializationResult == null ) ;
    final DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > > downendSetup =
        downendSetupSupplier.get() ;
    initializeDownendConnector( downendSetup ) ;
    final UpendConnector.Setup< EchoUpwardDuty< Designator > > upendSetup =
        upendSetupSupplier.apply( downendSetup ) ;
    checkArgument(
        downendSetup.port() == upendSetup.listenAddress.getPort(),
        "Port mismatch, this can only happen with crazy tweaking of setup-creating functions"
    ) ;

    upendInitializationResult = UpendConnectorFixture.InitializationResult.from( upendSetup ) ;

    if( startUpend ) {
      upendConnector().start().join() ;
    }
    startHttpProxyServerMaybe() ;
    if( startDownend ) {
      startDownendConnector() ;
    }
  }


  public void stopAll() throws MultiplexingException {
    stopAll(
        () -> {
          /** Some test may already have called {@link UpendConnector#stop()} so we avoid doing
           * it again to avoid polluting the log with some complain. */
          final UpendConnector.State state = upendInitializationResult.upendConnector.state() ;
          if( state != UpendConnector.State.STOPPING && state != UpendConnector.State.STOPPED ) {
            upendInitializationResult.upendConnector.stop().join() ;
          }
        },
        upendEventLoopGroup::shutdownGracefully
    ) ;

  }

// ========
// Features
// ========

  private void checkUpendInitialized() {
    checkState( upendInitializationResult != null, "Not initialized" );
  }

  /**
   * The dummy {@link SessionIdentifier} to register a {@link Channel} without authentication stuff.
   * There can be at most one {@link Channel} for this unique {@link SessionIdentifier}.
   */
  public SessionIdentifier dummySessionIdentifier() {
    checkState( upendInitializationResult.sessionIdentifierNoSession != null,
        "Initialized with real session support" ) ;
    return upendInitializationResult.sessionIdentifierNoSession ;
  }

  public TimeKit<UpdateableClock> timeKit() {
    checkState( upendInitializationGround.timeKit != null );
    return upendInitializationGround.timeKit;
  }

  public UpendConnector.Setup< EchoUpwardDuty<Designator> > upendSetup() {
    checkUpendInitialized();
    return upendInitializationResult.setup;
  }

  public UpendConnectorFixture.UrlPrebuilder uriPrebuilder() {
    checkUpendInitialized();
    return upendInitializationResult.urlPrebuilder;
  }

  public UpendConnector<
      EchoUpwardDuty< Designator >,
      EchoDownwardDuty< Designator >
  > upendConnector() {
    checkUpendInitialized() ;
    return upendInitializationResult.upendConnector;
  }

  public Extractor< Command<Designator, EchoUpwardDuty<Designator> > >
  upwardCommandExtractor() {
    return upwardRecordingGuide.guide();
  }

  private static final String WEBSOCKET_PATH = "/websocket";


// =========
// Scenarios
// =========

  public void commandRoundtrip( final SessionIdentifier sessionIdentifier )
      throws InterruptedException
  {
    commandRoundtrip( sessionIdentifier, "World", "Hello World" ) ;
  }

  public void commandRoundtrip(
      final SessionIdentifier sessionIdentifier,
      final String helloMessage,
      final String helloResponse
  )
      throws InterruptedException
  {
    LOGGER.info( "Starting a " + Command.class.getSimpleName() +
        " roundtrip testing connection ..." ) ;
    downend().send( upwardEchoCommand( TAG_0, helloMessage ) ) ;
    CommandAssert.assertThat( upwardCommandExtractor().waitForNextInbound() )
        .specificFieldsEquivalent( upwardEchoCommand( TAG_0, helloMessage ) )
        .endpointSpecificIs( Designator.class )
    ;

    final DownwardEchoCommand< Designator > downwardEchoCommandOutbound =
        downwardEchoCommand( TAG_0 ).withEndpointSpecific(
            DesignatorForger.newForger()
                .session( sessionIdentifier )
                .tag( TAG_0 )
                .instant( timeKit().clock.getCurrentDateTime() )
                .counter( 1 )
                .downward()
        )
    ;
    upendConnector().sendDownward( downwardEchoCommandOutbound ) ;

    CommandAssert.assertThat( dequeueDownwardCommandReceived() )
        .isEquivalentTo( downwardEchoCommand( TAG_0, helloResponse ) ) ;

    LOGGER.info( "Roundtrip successful." ) ;

  }
}
