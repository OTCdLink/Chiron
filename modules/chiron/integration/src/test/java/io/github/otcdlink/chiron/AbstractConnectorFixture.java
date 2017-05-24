package io.github.otcdlink.chiron;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.Command.Tag;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.downend.CommandTransceiver;
import io.github.otcdlink.chiron.downend.Downend;
import io.github.otcdlink.chiron.downend.DownendConnector;
import io.github.otcdlink.chiron.downend.DownendFixture;
import io.github.otcdlink.chiron.downend.DownendFixture.CommandTransceiverFixture;
import io.github.otcdlink.chiron.downend.SignonMaterializer;
import io.github.otcdlink.chiron.downend.TrackerCurator;
import io.github.otcdlink.chiron.downend.babyupend.BabyUpend;
import io.github.otcdlink.chiron.fixture.tcp.http.ConnectProxy;
import io.github.otcdlink.chiron.fixture.tcp.http.HttpProxy;
import io.github.otcdlink.chiron.integration.echo.DownwardEchoCommand;
import io.github.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import io.github.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import io.github.otcdlink.chiron.integration.echo.UpwardEchoCommandUntracked;
import io.github.otcdlink.chiron.integration.twoend.EndToEndFixture;
import io.github.otcdlink.chiron.middle.AutosignerFixture;
import io.github.otcdlink.chiron.middle.CommandAssert;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.tier.CommandInterceptor;
import io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import io.github.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import io.github.otcdlink.chiron.toolbox.Credential;
import io.github.otcdlink.chiron.toolbox.MultiplexingException;
import io.github.otcdlink.chiron.toolbox.TcpPortBooker;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.concurrent.Lazy;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.github.otcdlink.chiron.toolbox.internet.Hostname;
import io.github.otcdlink.chiron.toolbox.internet.InternetProxyAccess;
import io.github.otcdlink.chiron.toolbox.internet.LocalAddressTools;
import io.github.otcdlink.chiron.toolbox.netty.EventLoopGroupOwner;
import io.github.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for configuring and running a {@link Downend} under test.
 * It embeds the whole downend-upend chain, including an (optional) HTTP proxy.
 * JUnit test's constructor instantiates a {@link DownendFixture} or
 * a {@link CommandTransceiverFixture}, and uses appropriate {@link AbstractConnectorFixture}'s
 * methods to create a {@link DownendConnector.Setup} or a {@link CommandTransceiver.Setup}.
 * There are shorthands like
 * {@link CommandTransceiverFixture#initialize(SignonMaterializer, TimeBoundary.ForAll)} }
 * and {@link CommandTransceiverFixture#stopAll()}
 * to create and start everything, and to stop everything that was started.
 */
public abstract class AbstractConnectorFixture<
    ENDPOINT_SPECIFIC,
    DOWNEND extends Downend< ENDPOINT_SPECIFIC, EchoUpwardDuty< ENDPOINT_SPECIFIC > >,
    SETUP extends DownendConnector.Setup< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > >
> {

  private static final Logger LOGGER = LoggerFactory.getLogger( AbstractConnectorFixture.class ) ;

  /**
   * Need one single counter to differenciate threads when repeating the same test again and again.
   */
  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger() ;

  private static final String CHANGE_NAME = DownendConnector.ChangeDescriptor.class.getSimpleName() ;

  private final Lazy< SslEngineFactory.ForClient > SSL_ENGINE_FACTORY_FOR_CLIENT =
      new Lazy<>( AutosignerFixture::sslEngineFactoryForClient ) ;

  protected final Lazy< SslEngineFactory.ForServer > SSL_ENGINE_FACTORY_FOR_SERVER =
      new Lazy<>( AutosignerFixture::sslEngineFactoryForServer ) ;

  public final InetSocketAddress upendListenAddress ;

  protected final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(
      6,  /** Need so much because of {@link ConnectProxy}. */
      new ThreadFactoryBuilder()
          .setNameFormat( "Fixture-" + THREAD_COUNTER.getAndIncrement() )
          .setDaemon( true )
          .build()
  ) ;

  protected AbstractConnectorFixture() {
    this( TcpPortBooker.THIS.find() ) ;
  }

  private AbstractConnectorFixture(
      final int upendPort
  ) {
    checkArgument( upendPort > 0 ) ;
    try {
      this.upendListenAddress = Hostname.LOCALHOST.hostPort( upendPort ).asInetSocketAddress() ;
    } catch( final UnknownHostException e ) {
      throw new RuntimeException( "Should not happen when resolving " + Hostname.LOCALHOST, e ) ;
    }
  }


// =======
// Downend
// =======

  public final DOWNEND downend() {
    checkState( downend != null ) ;
    return downend ;
  }

  public SETUP downendSetup() {
    checkState( downendSetup != null, "Not properly initialized" ) ;
    return downendSetup ;
  }

  protected final void initializeDownendConnector( final SETUP setup ) {
    checkState( downendSetup == null ) ;
    checkState( downend == null ) ;
    downendSetup = setup ;
    downend = createConnector( setup ) ;
  }


  protected abstract DOWNEND createConnector( final SETUP setup ) ;

// ================
// Setup and Tuning
// ================

  /**
   * For a {@link DownendConnector.Setup} with minimal settings.
   */
  public Supplier< SETUP > downendSetup(
      final TimeBoundary.ForAll timeBoundary
  ) {
    return downendSetup( false, false, timeBoundary, NULL_SIGNON_MATERIALIZER, null ) ;
  }

  /**
   * For a {@link DownendConnector.Setup} with minimal settings.
   */
  public Supplier< SETUP > downendSetup(
      final TimeBoundary.ForAll timeBoundary,
      final CommandInterceptor upwardCommandInterceptor
  ) {
    return downendSetup(
        false, false, timeBoundary, NULL_SIGNON_MATERIALIZER, upwardCommandInterceptor ) ;
  }

  /**
   * For a {@link DownendConnector.Setup} with minimal settings.
   */
  public Supplier< SETUP > downendSetup(
      final boolean useTls,
      final boolean useProxy
  ) {
    return downendSetup( useTls, useProxy, TimeBoundary.DEFAULT, NULL_SIGNON_MATERIALIZER, null ) ;
  }

  /**
   * For a {@link DownendConnector.Setup} with minimal settings.
   */
  public Supplier< SETUP > downendSetup(
      final WebsocketFrameSizer websocketFrameSizer
  ) {
    return downendSetup(
        false,
        false,
        PingTimingPresets.QUIET,
        NULL_SIGNON_MATERIALIZER,
        null,
        websocketFrameSizer
    ) ;
  }

  public Supplier< SETUP > downendSetup(
      final TimeBoundary.ForAll timeBoundary,
      final SignonMaterializer signonMaterializer
  ) {
    return downendSetup( false, false, timeBoundary, signonMaterializer, null ) ;
  }

  public Supplier< SETUP > downendSetup(
      final boolean useTls,
      final boolean useProxy,
      final TimeBoundary.ForAll timeBoundary,
      final SignonMaterializer signonMaterializer,
      final CommandInterceptor commandInterceptor
  ) {
    return downendSetup(
        useTls,
        useProxy,
        timeBoundary,
        signonMaterializer,
        commandInterceptor,
        EndToEndFixture.WEBSOCKET_FRAME_SIZER
    ) ;
  }

  public Supplier< SETUP > downendSetup(
      final boolean useTls,
      final boolean useProxy,
      final TimeBoundary.ForAll timeBoundary,
      final SignonMaterializer signonMaterializer,
      final CommandInterceptor commandInterceptor,
      final WebsocketFrameSizer websocketFrameSizer
  ) {

    final InternetProxyAccess internetProxyAccess = useProxy ?
        new InternetProxyAccess(
            InternetProxyAccess.Kind.HTTP,
            HostPort.create( LocalAddressTools.LOCALHOST_HOSTNAME, TcpPortBooker.THIS.find()
        ) ) :
        null
    ;

    final SslEngineFactory.ForClient sslEngineFactory =
        useTls ? SSL_ENGINE_FACTORY_FOR_CLIENT.get() : null ;

    final URL websocketUrl = websocketUrl( useTls ) ;

    return () -> newSetup(
        timeBoundary,
        signonMaterializer,
        stateWatcher,
        internetProxyAccess,
        sslEngineFactory,
        websocketUrl,
        downwardCommandQueue::add,
        commandInterceptor,
        websocketFrameSizer
    ) ;
  }

  protected abstract SETUP newSetup(
      final TimeBoundary.ForAll timeBoundary,
      final SignonMaterializer signonMaterializer,
      final CommandTransceiver.ChangeWatcher stateWatcher,
      final InternetProxyAccess internetProxyAccess,
      final SslEngineFactory.ForClient sslEngineFactory,
      final URL websocketUrl,
      final CommandConsumer< Command< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > > >
          commandReceiver,
      final CommandInterceptor commandInterceptor,
      final WebsocketFrameSizer websocketFrameSizer
  ) ;

  private URL websocketUrl( final boolean useTls ) {
    return UrxTools.parseUrlQuiet(
        "http" + ( useTls ? "s" : "" ) + "://" +
        upendListenAddress.getHostString() + ":" + upendListenAddress.getPort() +
        BabyUpend.WEBSOCKET_PATH
    ) ;
  }


  public interface PingTimingPresets {

    /**
     * This is nice for tests, not pinging avoids to pollute logs, and immediate reconnection
     * unveiled interesting bugs in {@link DownendConnector} state transitions.
     */
    TimeBoundary.ForAll NO_PING = TimeBoundary.Builder.createNew()
        .pingInterval( 1_000_000 )
        .pongTimeoutOnDownend( 1_000_000 )
        .reconnectImmediately()
        .pingTimeoutNever()
        .sessionInactivityForever()
        .build()
    ;

    TimeBoundary.ForAll QUICK_PING = TimeBoundary.Builder.createNew()
        .pingInterval( 5 )
        .pongTimeoutOnDownend( 1000 )
        .reconnectImmediately()
        .pingTimeoutNever()
        .sessionInactivityForever()
        .build()
    ;

    TimeBoundary.ForAll PING_TIMEOUT = TimeBoundary.Builder.createNew()
        .pingInterval( 5 )
        .pongTimeoutOnDownend( 50 )
        .reconnectDelay( 50, 50 )
        .pingTimeoutOnUpend( 50 )
        .sessionInactivityForever()
        .build()
    ;

    TimeBoundary.ForAll QUICK_RECONNECT = TimeBoundary.Builder.createNew()
        .pingInterval( 1000 )
        .pongTimeoutOnDownend( 1000 )
        .reconnectDelay( 50, 50 )
        .pingTimeoutNever()
        .maximumSessionInactivity( 1 )
        .build()
    ;

    TimeBoundary.ForAll QUIET = TimeBoundary.Builder.createNew()
        .pingIntervalNever()
        .pongTimeoutNever()
        .reconnectNever()
        .pingTimeoutNever()
        .sessionInactivityForever()
        .build()
    ;
  }


  public final void restartHttpProxy() {
    checkState( httpProxyServer != null, "Not started" ) ;
    LOGGER.info( "****** Restarting " + httpProxyServer + " ... ******" );
    httpProxyServer.stop()
        .whenComplete( EventLoopGroupOwner.logStopCompletion( LOGGER ) )
        .join()
    ;
    httpProxyServer.start().join() ;
    LOGGER.info( "****** Restarted " + httpProxyServer + ". ******" ) ;

  }


// ======================
// State and verification
// ======================


  /**
   * Keep received {@link Command}s, we need a {@link BlockingQueue} to force the test to wait
   * for expected response. {@link DownendConnector} just enqueues received {@link Command}s
   * so it remains unblocked.
   */
  private final BlockingQueue< Command< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > > >
      downwardCommandQueue = new LinkedBlockingQueue<>() ;

  /**
   * Records method calls to {@link DownendConnector.ChangeWatcher}s, we need a {@link BlockingQueue}
   * to force the test to wait for expected response. {@link DownendConnector} just enqueues
   * method calls so it remains unblocked.
   */
  private final BlockingQueue< DownendConnector.Change > downendChangeQueue =
      new LinkedBlockingQueue<>() ;

  private SETUP downendSetup = null ;

  protected DOWNEND downend = null ;

  private final CommandTransceiver.ChangeWatcher stateWatcher =
      ExtendedChange.recordingDownendWatcher(
          LOGGER,
          () -> downend == null ? "undefined" : downend.toString(),
          downendChangeQueue
      )
  ;

  protected ConnectProxy httpProxyServer = null ;

  public final DownendConnector.Change nextDownendChange() throws InterruptedException {
    return downendChangeQueue.take() ;
  }

  public final void waitForMatch( final Predicate< DownendConnector.Change > matcher )
      throws InterruptedException
  {
    while( true ) {
      if( matcher.test( nextDownendChange() ) ) {
        return ;
      }
    }
  }

  public final DownendConnector.Change nextDownendChange(
      final long duration,
      final TimeUnit timeUnit
  ) throws InterruptedException, TimeoutException {
    final DownendConnector.Change poll = downendChangeQueue.poll( duration, timeUnit ) ;
    if( poll == null ) {
      throw new TimeoutException( "No " + DownendConnector.Change.class.getSimpleName() +
          " in the queue after waiting " + duration + "  " + timeUnit.name().toLowerCase() ) ;
    }
    return poll ;
  }

  public final ImmutableList< DownendConnector.Change > drainDownendChanges() {
    final Collection< DownendConnector.Change > collection = new ArrayList<>() ;
    downendChangeQueue.drainTo( collection ) ;
    return ImmutableList.copyOf( collection ) ;
  }

  public final void waitForDownendConnectorState(
      final DownendConnector.ChangeDescriptor... statesWaitedFor
  ) throws InterruptedException {
    waitForDownendConnectorState( Ø -> { }, statesWaitedFor ) ;
  }

  public final void waitForDownendConnectorState(
      final Consumer< DownendConnector.Change > changeDescriptorConsumer,
      final DownendConnector.ChangeDescriptor... statesWaitedFor
  )
      throws InterruptedException
  {
    final ImmutableSet states = ImmutableSet.copyOf( statesWaitedFor ) ;
    while( true ) {
      final DownendConnector.Change stateWatch = nextDownendChange() ;
      changeDescriptorConsumer.accept( stateWatch ) ;
      if( states.contains( stateWatch.kind ) ) {
        LOGGER.info( "Got expected " + CHANGE_NAME + ": " + stateWatch + "." ) ;
        break ;
      } else {
        LOGGER.info( "Got (not explicitely expected) " + CHANGE_NAME + ": " + stateWatch + "." ) ;
      }
    }

  }

  public final void checkNoMoreDownendChange() {
    checkQueueEmpty( downendChangeQueue ) ;
  }

  public final Command< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > >
  dequeueDownwardCommandReceived()
      throws InterruptedException
  {
    return downwardCommandQueue.take() ;
  }

  public final void checkNoMoreDownwardCommand() {
    checkQueueEmpty( this.downwardCommandQueue ) ;
  }

  private void checkQueueEmpty( final BlockingQueue queue ) {
    checkState(
        downend == null || downend.state() == DownendConnector.State.STOPPED,
        "Still running"
    ) ;
    final Collection drained = new ArrayList<>() ;
    queue.drainTo( drained ) ;
    if( ! drained.isEmpty() ) {
      Assert.fail( "There are unasserted value(s): " + ImmutableList.copyOf( drained ) ) ;
    }
  }

  public final void checkDequeuedDownwardCommandEquivalentTo(
      final DownwardEchoCommand< ENDPOINT_SPECIFIC > expected
  ) throws InterruptedException {
    final Command< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > > actual =
        dequeueDownwardCommandReceived() ;
    if( actual.endpointSpecific instanceof Tag ) {
      CommandAssert.assertThat( actual ).isEquivalentTo( expected ) ;
    } else {
      CommandAssert.assertThat( actual ).specificFieldsEquivalent( expected ) ;
    }
  }

// ==============
// Start and stop
// ==============

  protected void startDownendConnector() throws InterruptedException {
    startDownendConnector( null ) ;
  }

  /**
   * @param connectionDescriptor {@code null} if we don't care about asserting on it.
   */
  protected void startDownendConnector( final ConnectionDescriptor connectionDescriptor )
      throws InterruptedException
  {
    downend.start().join() ;
    ConnectorChangeAssert.assertThat( nextDownendChange() )
        .hasKind( DownendConnector.State.CONNECTING ) ;
    if( connectionDescriptor == null ) {
      ConnectorChangeAssert.assertThat( nextDownendChange() )
          .hasKind( DownendConnector.State.CONNECTED ) ;
    } else {
      assertThat( nextDownendChange() ).isEqualTo(
          new DownendConnector.Change.SuccessfulConnection( connectionDescriptor ) ) ;
    }

    /** We can't assert safely there is no more {@link DownendConnector.State}. */
  }

  protected void startHttpProxyServerMaybe() throws Exception {
    if( downendSetup().internetProxyAccess != null ) {
      checkArgument( LocalAddressTools.isLocalhost(
          downendSetup().internetProxyAccess.hostPort.hostname ) ) ;

      httpProxyServer = ConnectProxy.createAndStart(
          eventLoopGroup,
          downendSetup().internetProxyAccess.hostPort.asInetSocketAddress(),
          HttpProxy.Watcher.LOGGING,
          HttpProxy.PipelineConfigurator.Factory.NULL_FACTORY,
          1000
      )
      ;
    }
  }

  protected final void stopAll(
      final MultiplexingException.Collector.Task... upendStopTasks
  ) throws MultiplexingException {

    LOGGER.info( "Stopping everything ..." ) ;

    final MultiplexingException.Collector< MultiplexingException > collector =
        MultiplexingException.newCollector() ;

    if( downend != null ) {
      collector.execute(
          () -> downend().stop().join(),
          () -> downend = null
      ) ;
    }

    if( httpProxyServer != null ) {
      collector.execute(
          () -> httpProxyServer.stop(),
          () -> httpProxyServer = null
      ) ;
    }

    collector.execute( upendStopTasks ) ;

    collector.execute( eventLoopGroup::shutdownGracefully ) ;

    collector.throwIfAny( "Stopping failed" ) ;

    LOGGER.info( "Stopped everything." ) ;

  }

// ==================
// SignonMaterializer
// ==================

  static {
    NULL_SIGNON_MATERIALIZER = ( SignonMaterializer ) java.lang.reflect.Proxy.newProxyInstance(
        DownendFixture.class.getClassLoader(),
        new Class<?>[]{ SignonMaterializer.class },
        new AbstractInvocationHandler() {
          @Override
          protected Object handleInvocation(
              final Object proxy,
              final Method method,
              final Object[] args
          ) throws Throwable {
            throw new UnsupportedOperationException( "Not implemented, do not call" ) ;
          }

          @Override
          public String toString() {
            return SignonMaterializer.class.getSimpleName() + "#NULL{}" ;
          }
        }
    ) ;

  }


// ========================
// Constants for test cases
// ========================

  public static < ENDPOINT_SPECIFIC > UpwardEchoCommand< ENDPOINT_SPECIFIC >
  upwardEchoCommand( final ENDPOINT_SPECIFIC endpointSpecific ) {
    return upwardEchoCommand( endpointSpecific, "World" ) ;
  }

  public static < ENDPOINT_SPECIFIC > UpwardEchoCommand< ENDPOINT_SPECIFIC >
  upwardEchoCommand( final ENDPOINT_SPECIFIC endpointSpecific, final boolean tracked ) {
    return upwardEchoCommand( endpointSpecific, "World", tracked ) ;
  }

  public static < ENDPOINT_SPECIFIC > UpwardEchoCommand< ENDPOINT_SPECIFIC >
  upwardEchoCommand( final ENDPOINT_SPECIFIC endpointSpecific, final String message ) {
    return upwardEchoCommand( endpointSpecific, message, true ) ;
  }

  public static < ENDPOINT_SPECIFIC > UpwardEchoCommand< ENDPOINT_SPECIFIC >
  upwardEchoCommand(
      final ENDPOINT_SPECIFIC endpointSpecific,
      final String message,
      final boolean tracked
  ) {
    if( tracked ) {
      return new UpwardEchoCommand<>( endpointSpecific, message ) ;
    } else {
      return new UpwardEchoCommandUntracked<>( endpointSpecific, message ) ;
    }
  }

  public static < ENDPOINT_SPECIFIC > UpwardEchoCommand< ENDPOINT_SPECIFIC >
  upwardBrokenEchoCommand( final ENDPOINT_SPECIFIC endpointSpecific ) {
    return new UpwardEchoCommand< ENDPOINT_SPECIFIC >( endpointSpecific, "World" ) {
      @Override
      public void encodeBody( final PositionalFieldWriter positionalFieldWriter ) {
        throw new RuntimeException( "Boom" ) ;
      }
    } ;
  }

  public static < ENDPOINT_SPECIFIC > DownwardEchoCommand< ENDPOINT_SPECIFIC >
  downwardEchoCommand( final ENDPOINT_SPECIFIC endpointSpecific, final String message ) {
    return new DownwardEchoCommand<>( endpointSpecific, message ) ;
  }

  public static < ENDPOINT_SPECIFIC > DownwardEchoCommand< ENDPOINT_SPECIFIC >
  downwardEchoCommand( final ENDPOINT_SPECIFIC endpointSpecific ) {
    return downwardEchoCommand( endpointSpecific, "Hello World" ) ;
  }

  public static final Tag TAG_0 = new Tag( TrackerCurator.TAG_PREFIX + "0" ) ;

  public static final Tag TAG_1 = new Tag( TrackerCurator.TAG_PREFIX + "1" ) ;

  public static final SignonMaterializer NULL_SIGNON_MATERIALIZER ;

  public static final Credential CREDENTIAL_OK = new Credential( "TheLogin", "ThePassword" ) ;

  public static final Credential CREDENTIAL_BAD = new Credential( "TheLogin", "BadPassword" ) ;

  public static final SecondaryToken SECONDARY_TOKEN = new SecondaryToken( "Token123" ) ;

  public static final SecondaryCode SECONDARY_CODE_OK = new SecondaryCode( "Code123" ) ;

  public static final SessionIdentifier SESSION_IDENTIFIER = new SessionIdentifier( "Session123" ) ;

}
