package io.github.otcdlink.chiron.downend;

import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.AbstractConnectorFixture;
import io.github.otcdlink.chiron.AbstractConnectorFixture.PingTimingPresets;
import io.github.otcdlink.chiron.ConnectorChangeAssert;
import io.github.otcdlink.chiron.ExtendedChange;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.Command.Tag;
import io.github.otcdlink.chiron.fixture.BlockingMonolist;
import io.github.otcdlink.chiron.fixture.CpuStress;
import io.github.otcdlink.chiron.fixture.NettyLeakDetectorRule;
import io.github.otcdlink.chiron.integration.echo.DownwardEchoCommand;
import io.github.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import io.github.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import io.github.otcdlink.chiron.middle.CommandAssert;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle.SessionValid;
import io.github.otcdlink.chiron.middle.tier.CommandInterceptor;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import io.github.otcdlink.chiron.toolbox.Credential;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import mockit.Injectable;
import mockit.Mocked;
import mockit.StrictExpectations;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.github.otcdlink.chiron.AbstractConnectorFixture.TAG_0;
import static io.github.otcdlink.chiron.AbstractConnectorFixture.upwardBrokenEchoCommand;
import static io.github.otcdlink.chiron.downend.DownendFixture.CREDENTIAL_BAD;
import static io.github.otcdlink.chiron.downend.DownendFixture.CREDENTIAL_OK;
import static io.github.otcdlink.chiron.fixture.TestNameTools.setTestThreadName;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class DownendConnectorTest
    extends AbstractDownendTest<
        Tag,
        DownendConnector< Tag, EchoDownwardDuty< Tag >, EchoUpwardDuty< Tag > >,
        DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > >
    >
{


  @Test( timeout = TIMEOUT_MS )
  public void simpleEcho() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll() ;
    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void simpleEchoWithTls() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignon( fixture.downendSetup( true, false ), true, true ) ;
    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void simpleEchoWithProxy() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignon( fixture.downendSetup( false, true ), true, true ) ;
    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void simpleEchoWithTlsAndProxy() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignon( fixture.downendSetup( true, true ), true, true ) ;
    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void kickout() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll() ;
    assertThat( fixture.downend().state() ).isIn( DownendConnector.State.CONNECTED ) ;
    fixture.babyUpend().sendToAllNow( new CloseWebSocketFrame() ) ;
    fixture.waitForDownendConnectorState( DownendConnector.State.STOPPED ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void startingDownendAgainstNoUpend() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignon(
        fixture.downendSetup( PingTimingPresets.QUICK_RECONNECT ), false, false ) ;

    /** Don't call {@link CompletableFuture#join()}, this would never return since connection
     * can't happen yet. */
    fixture.downend().start() ;

    fixture.waitForDownendConnectorState(
        ExtendedChange.ExtendedKind.FAILED_CONNECTION_ATTEMPT ) ;

    assertThat( fixture.downend().state() ).isIn( DownendConnector.State.CONNECTING ) ;

    fixture.babyUpend().start().join() ;

    fixture.waitForDownendConnectorState( DownendConnector.State.CONNECTED ) ;

    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void simpleReconnect() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( PingTimingPresets.QUICK_RECONNECT ) ;

    LOGGER.info( "Stopping " + fixture.babyUpend() + " ..." ) ;
    fixture.babyUpend().stop().join() ;
    fixture.waitForDownendConnectorState( DownendConnector.State.CONNECTING ) ;
    LOGGER.info( "Starting " + fixture.babyUpend() + " ..." ) ;
    fixture.babyUpend().start().join() ;
    LOGGER.info( "Got " + fixture.babyUpend() + " restarted." ) ;

    fixture.waitForDownendConnectorState( DownendConnector.State.CONNECTED ) ;

    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void restart() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( PingTimingPresets.QUICK_RECONNECT ) ;

    LOGGER.info( "Restarting " + fixture.downend() + " ..." ) ;
    fixture.downend().stop().join() ;
    fixture.downend().start() ;

    fixture.waitForDownendConnectorState( DownendConnector.State.CONNECTED ) ;
    LOGGER.info( "Got " + fixture.downend() + " restarted." ) ;

    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void doubleStart() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( PingTimingPresets.QUICK_RECONNECT ) ;

    LOGGER.info( "Starting " + fixture.downend() + " again ..." ) ;
    assertThatThrownBy( () -> fixture.downend().start() )
        .isInstanceOf( IllegalStateException.class ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void doubleStop() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( PingTimingPresets.QUICK_RECONNECT ) ;
    fixture.downend().stop().join() ;
    ConnectorChangeAssert
        .assertThat( fixture.nextDownendChange() ).hasKind( DownendConnector.State.STOPPING ) ;
    ConnectorChangeAssert
        .assertThat( fixture.nextDownendChange() ).hasKind( DownendConnector.State.STOPPED ) ;

    /** {@link #tearDown()} runs it one more time but let's test everything now. */
    fixture.downend().stop().join() ;
    fixture.checkNoMoreDownendChange() ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void brokenCommand() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( PingTimingPresets.QUICK_RECONNECT ) ;
    fixture.downend().send( upwardBrokenEchoCommand( TAG_0 ) ) ;
    fixture.stopAll() ;
    // TODO: assert that we logged something or whatever.
    runTearDown = false ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void pingHappens() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( PingTimingPresets.QUICK_PING ) ;
    fixture.pingPongGuide().waitForNextInbound() ;
    fixture.pingPongGuide().waitForNextInbound() ;
    LOGGER.info( "Ping happened at least twice." ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void pongTimeoutCausesDisconnection() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( PingTimingPresets.PING_TIMEOUT ) ;
    for( int i = 0 ; i < 10 ; i ++ ) {  // Disable timeout when running more than 100 iterations.
      fixture.pingPongGuide().pongEnabled( false ) ;
      fixture.waitForDownendConnectorState( DownendConnector.State.CONNECTING ) ;
      fixture.pingPongGuide().pongEnabled( true ) ;
      fixture.waitForDownendConnectorState( DownendConnector.State.CONNECTED ) ;
    }
  }

  /**
   * Repeat {@link #pongTimeoutCausesDisconnection()} for a certain duration, while generating
   * CPU stress.
   */
  @Test
  @Ignore( "Takes too long, please run interactively" )
  public void pongTimeoutUnderStress() throws Exception {
    runTearDown = false ; // Don't tearDown current test; this doesn't apply to spawned tests.
    final AtomicReference< Exception > problem = new AtomicReference<>() ;
    final AtomicBoolean cpuStressDone = new AtomicBoolean() ;
    final CpuStress cpuStress = new CpuStress(
        Runtime.getRuntime().availableProcessors() - 2, 1, TimeUnit.MINUTES ) ;
    cpuStress.run().thenRun( () -> cpuStressDone.set( true ) ) ;

    int counter = 0 ;
    while( ! cpuStressDone.get() ) {
      LOGGER.info( "*** Starting test run " + counter + " ***" ) ;
      final Semaphore singleTestDoneSemaphore = new Semaphore( 0 ) ;

      // We need a separate thread to enforce our timeout.
      final Thread testThread = new Thread( () -> {
        try {
          final DownendConnectorTest downendConnectorTest = new DownendConnectorTest() ;
          // We could pass the test method as a parameter to some generic method.
          downendConnectorTest.pongTimeoutCausesDisconnection() ;
          downendConnectorTest.tearDown() ;
        } catch( final Exception e ) {
          problem.set( e ) ;
        }
        singleTestDoneSemaphore.release() ;
      } ) ;
      testThread.start() ;

      final boolean inTime = singleTestDoneSemaphore.tryAcquire( TIMEOUT_MS, MILLISECONDS ) ;
      final Exception problemRaised = problem.get() ;
      if( ! inTime || problemRaised != null ) {
        cpuStress.terminate() ;
        if( problemRaised == null ) {
          throw new TimeoutException( "Single test did not terminate in time" ) ;
        } else {
          throw problemRaised ;
        }
      }
      LOGGER.info( "*** Ending test run " + counter + " ***" ) ;
      counter ++ ;
    }
  }

  @Test
  public void signon( @Mocked final SignonMaterializer signonMaterializer ) throws Exception {
    setTestThreadName() ;
    initializeAndSignon( fixture, signonMaterializer ) ;

    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;

  }

  @Test( timeout = TIMEOUT_MS )
  public void sessionReuse( @Mocked final SignonMaterializer signonMaterializer ) throws Exception {
    setTestThreadName() ;
    initializeAndSignon( fixture, PingTimingPresets.QUICK_RECONNECT, signonMaterializer ) ;

    LOGGER.info( "Stopping and restarting asynchronously " + fixture.babyUpend() + "." ) ;
    fixture.babyUpend().stop().join() ;

    fixture.phaseGuide()
        .record( SessionValid.create( DownendFixture.SESSION_IDENTIFIER ) ) ;
    fixture.babyUpend().start().join() ;
    fixture.waitForDownendConnectorState( DownendConnector.State.CONNECTED ) ;
    LOGGER.info( "Reconnection happened after restarting " + fixture.babyUpend() + "." ) ;

    fixture.phaseGuide().waitForInboundMatching(
        textFrame -> textFrame.text().contains( "RESIGNON" ) ) ;

    fixture.waitForDownendConnectorState( DownendConnector.State.SIGNED_IN ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void sessionReuseFails( @Injectable final SignonMaterializer signonMaterializer )
      throws Exception
  {
    setTestThreadName() ;
    initializeAndSignon( fixture, PingTimingPresets.QUICK_RECONNECT, signonMaterializer ) ;

    LOGGER.info( "Stopping and restarting " + fixture.babyUpend() + "." ) ;
    fixture.babyUpend().stop().join() ;

    fixture.phaseGuide()
        .record( SessionLifecycle.SignonFailed.create( SIGNON_FAILURE_NOTICE_UNKNOWN_SESSION ) )
        .record( SessionValid.create( DownendFixture.SESSION_IDENTIFIER ) )
    ;

    final BlockingMonolist< Consumer< Credential > > credentialCapture = new BlockingMonolist<>() ;
    new StrictExpectations() {{
      signonMaterializer.readCredential( withCapture( credentialCapture ) ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.done() ;
    }} ;

    fixture.babyUpend().start().join() ;
    fixture.phaseGuide().waitForInboundMatching(
        textFrame -> textFrame.text().contains( "RESIGNON" ) ) ;
    LOGGER.info( "Reconnection happened after restarting " + fixture.babyUpend() + "." ) ;
    LOGGER.info( "Full Signon must happen again (skipping Secondary here for brevity)." ) ;

    credentialCapture.getOrWait().accept( DownendFixture.CREDENTIAL_OK ) ;
    fixture.waitForDownendConnectorState( DownendConnector.State.SIGNED_IN ) ;
    fixture.checkPhaseGuideOutboundQueueEmptyIfAny() ;

    final ImmutableList< TextWebSocketFrame > inboundPhaseFrames =
        fixture.phaseGuide().drainInbound() ;
    assertThat( inboundPhaseFrames ).hasSize( 1 ) ;
    assertThat( inboundPhaseFrames.get( 0 ).text() ).contains( "PRIMARY_SIGNON" ) ;

  }

  @Test( timeout = TIMEOUT_MS )
  public void badCredentialThenCancel( @Injectable final SignonMaterializer signonMaterializer )
      throws Exception
  {
    setTestThreadName() ;
    fixture.initialize( signonMaterializer, PingTimingPresets.NO_PING ) ;

    fixture.phaseGuide().record(
        SessionLifecycle.SignonFailed.create( SIGNON_FAILURE_NOTICE_INVALID_CREDENTIAL ) )
    ;

    final BlockingMonolist< Consumer< Credential > > credentialCapture1 = new BlockingMonolist<>() ;
    final BlockingMonolist< Consumer< Credential > > credentialCapture2 = new BlockingMonolist<>() ;

    new StrictExpectations() {{
      signonMaterializer.readCredential( withCapture( credentialCapture1 ) ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.setProgressMessage( null ) ;
      signonMaterializer.setProblemMessage( SIGNON_FAILURE_NOTICE_INVALID_CREDENTIAL ) ;
      signonMaterializer.readCredential( withCapture( credentialCapture2 ) ) ;
      signonMaterializer.done() ;
    }} ;

    fixture.downend().start() ;
    LOGGER.info( "Started " + fixture.downend() + ", now trying a bad " +
        Credential.class.getSimpleName() + " ..." ) ;

    credentialCapture1.getOrWait().accept( CREDENTIAL_BAD ) ;
    assertThat( fixture.phaseGuide().waitForNextInbound().text() ).contains( "PRIMARY_SIGNON" ) ;

    LOGGER.info( "Bad " + Credential.class.getSimpleName() + " sent, " +
        "asking for a new one tells to give up." ) ;

    credentialCapture2.getOrWait().accept( null ) ;
    fixture.waitForDownendConnectorState( ExtendedChange.ExtendedKind.NO_SIGNON ) ;
    fixture.checkPhaseGuideOutboundQueueEmptyIfAny() ;

    LOGGER.info( "Signon failed as supposed to." ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void signonFailsUnrecoverably( @Injectable final SignonMaterializer signonMaterializer )
      throws Exception
  {
    setTestThreadName() ;
    fixture.initialize( signonMaterializer, PingTimingPresets.NO_PING ) ;

    fixture.phaseGuide().record(
        SessionLifecycle.SignonFailed.create( SIGNON_FAILURE_NOTICE_UNEXPECTED ) )
    ;

    final BlockingMonolist< Consumer< Credential > > credentialCapture = new BlockingMonolist<>() ;
    final BlockingMonolist< Runnable > afterCancellationCapture = new BlockingMonolist<>() ;

    new StrictExpectations() {{
      signonMaterializer.readCredential( withCapture( credentialCapture ) ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.setProgressMessage( null ) ;
      signonMaterializer.setProblemMessage( SIGNON_FAILURE_NOTICE_UNEXPECTED ) ;
      signonMaterializer.waitForCancellation( withCapture( afterCancellationCapture ) ) ;
    }} ;

    fixture.downend().start()/*.join()*/ ;
    LOGGER.info( "Started " + fixture.downend() + ", now trying a bad " +
        Credential.class.getSimpleName() + " ..." ) ;

    credentialCapture.getOrWait().accept( CREDENTIAL_OK ) ;
    assertThat( fixture.phaseGuide().waitForNextInbound().text() ).contains( "PRIMARY_SIGNON" ) ;

    LOGGER.info( "Bad " + Credential.class.getSimpleName() + " sent, " +
        "asking for a new one tells to give up." ) ;

    afterCancellationCapture.getOrWait().run() ;

    fixture.waitForDownendConnectorState( ExtendedChange.ExtendedKind.NO_SIGNON ) ;
    fixture.checkPhaseGuideOutboundQueueEmptyIfAny() ;

    LOGGER.info( "Signon failed as supposed to." ) ;


  }

  @Test( timeout = TIMEOUT_MS )
  public void upwardCommandInterceptor() throws Exception {
    setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( fixture.downendSetup(
        TimeBoundary.DEFAULT,
        new CommandInterceptor() {
          @Override
          public boolean interceptUpward( final Command command, final Sink sink ) {
            if( command instanceof UpwardEchoCommand ) {
              final UpwardEchoCommand echoCommand = ( UpwardEchoCommand ) command ;
              if( echoCommand.message.startsWith( "intercept-me" ) ) {
                sink.sendBackward(
                    new DownwardEchoCommand<>( echoCommand.endpointSpecific, "intercepted!" ) ) ;
                return true ;
              }
            }
            return false ;
          }
        } )
    ) ;
    LOGGER.info( "Starting a " + Command.class.getSimpleName() +
        " roundtrip testing " + CommandInterceptor.class.getSimpleName() + " ..." ) ;

    Assertions.assertThat( fixture.commandToTextWebsocketGuide().drainInbound() )
        .asList().hasSize( 0 ) ;
    Assertions.assertThat( fixture.commandToTextWebsocketGuide().outboundQueueEmpty() )
        .isTrue() ;

    fixture.downend().send( AbstractConnectorFixture.upwardEchoCommand( TAG_0, "intercept-me" ) ) ;

    final Command< Tag, EchoDownwardDuty< Tag > > downwardCommand =
        fixture.dequeueDownwardCommandReceived() ;
    CommandAssert.assertThat( downwardCommand ).isInstanceOf( DownwardEchoCommand.class ) ;

    LOGGER.info( "Received " + downwardCommand + " as " + CommandInterceptor.class.getSimpleName() +
        "'s response." ) ;
    fixture.stopAll() ;
    runTearDown = false ;

    fixture.checkNoMoreDownwardCommand() ;
    Assertions.assertThat(
        fixture.commandToTextWebsocketGuide().drainInbound() ).asList().hasSize( 0 ) ;
    Assertions.assertThat(
        fixture.commandToTextWebsocketGuide().outboundQueueEmpty() ).isTrue() ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( DownendConnectorTest.class ) ;

  @Rule
  public final NettyLeakDetectorRule nettyLeakDetectorRule = new NettyLeakDetectorRule() ;


  public DownendConnectorTest() {
    super( DownendFixture.newDownendConnectorFixture() ) ;
  }

  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.info( "=== Test ready to run ===" ) ;
  }



}