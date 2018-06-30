package com.otcdlink.chiron.downend;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.AbstractConnectorFixture;
import com.otcdlink.chiron.ConnectorChangeAssert;
import com.otcdlink.chiron.ExtendedChange;
import com.otcdlink.chiron.Multicaptor;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Command.Tag;
import com.otcdlink.chiron.fixture.BlockingMonolist;
import com.otcdlink.chiron.fixture.CpuStress;
import com.otcdlink.chiron.fixture.NettyLeakDetectorRule;
import com.otcdlink.chiron.fixture.TestNameTools;
import com.otcdlink.chiron.integration.echo.DownwardEchoCommand;
import com.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import com.otcdlink.chiron.middle.CommandAssert;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.mockster.Mockster;
import com.otcdlink.chiron.toolbox.Credential;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import mockit.Delegate;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.Injectable;
import mockit.Mocked;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.otcdlink.chiron.downend.DownendFixture.CREDENTIAL_BAD;
import static com.otcdlink.chiron.downend.DownendFixture.CREDENTIAL_OK;
import static com.otcdlink.chiron.mockster.Mockster.nextInvocationIsNonBlockingOperative;
import static com.otcdlink.chiron.mockster.Mockster.withCapture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class DownendConnectorTest
    extends AbstractDownendTest<
    Command.Tag,
        DownendConnector<Command.Tag, EchoDownwardDuty<Command.Tag>, EchoUpwardDuty< Tag >>,
        DownendConnector.Setup<Command.Tag, EchoDownwardDuty<Command.Tag> >
    >
{


  @Test( timeout = TIMEOUT_MS )
  public void simpleEcho() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll() ;
    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void simpleEchoWithTls() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignon( fixture.downendSetup( true, false ), true, true ) ;
    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void simpleEchoWithProxy() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignon( fixture.downendSetup( false, true ), true, true ) ;
    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void simpleEchoWithTlsAndProxy() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignon( fixture.downendSetup( true, true ), true, true ) ;
    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void kickout() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll() ;
    assertThat( fixture.downend().state() ).isIn( DownendConnector.State.CONNECTED ) ;
    fixture.babyUpend().sendToAllNow( new CloseWebSocketFrame() ) ;
    fixture.waitForDownendConnectorState( DownendConnector.State.STOPPED ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void startingDownendAgainstNoUpend() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignon(
        fixture.downendSetup( AbstractConnectorFixture.PingTimingPresets.QUICK_RECONNECT ), false, false ) ;

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
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( AbstractConnectorFixture.PingTimingPresets.QUICK_RECONNECT ) ;

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
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( AbstractConnectorFixture.PingTimingPresets.QUICK_RECONNECT ) ;

    LOGGER.info( "Restarting " + fixture.downend() + " ..." ) ;
    fixture.downend().stop().join() ;
    fixture.downend().start() ;

    fixture.waitForDownendConnectorState( DownendConnector.State.CONNECTED ) ;
    LOGGER.info( "Got " + fixture.downend() + " restarted." ) ;

    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void doubleStart() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( AbstractConnectorFixture.PingTimingPresets.QUICK_RECONNECT ) ;

    LOGGER.info( "Starting " + fixture.downend() + " again ..." ) ;
    assertThatThrownBy( () -> fixture.downend().start() )
        .isInstanceOf( IllegalStateException.class ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void doubleStop() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( AbstractConnectorFixture.PingTimingPresets.QUICK_RECONNECT ) ;
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
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( AbstractConnectorFixture.PingTimingPresets.QUICK_RECONNECT ) ;
    fixture.downend().send( AbstractConnectorFixture.upwardBrokenEchoCommand( AbstractConnectorFixture.TAG_0 ) ) ;
    fixture.stopAll() ;
    // TODO: assert that we logged something or whatever.
    runTearDown = false ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void pingHappens() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( AbstractConnectorFixture.PingTimingPresets.QUICK_PING ) ;
    fixture.pingPongGuide().waitForNextInbound() ;
    fixture.pingPongGuide().waitForNextInbound() ;
    LOGGER.info( "Ping happened at least twice." ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void pongTimeoutCausesDisconnection() throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( AbstractConnectorFixture.PingTimingPresets.PING_TIMEOUT ) ;
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
    TestNameTools.setTestThreadName() ;
    initializeAndSignon( fixture, signonMaterializer ) ;

    fixture.commandRoundtrip( AbstractConnectorFixture.TAG_0 ) ;

  }

  @Test( timeout = TIMEOUT_MS )
  public void sessionReuse( @Mocked final SignonMaterializer signonMaterializer ) throws Exception {
    TestNameTools.setTestThreadName() ;
    initializeAndSignon( fixture, AbstractConnectorFixture.PingTimingPresets.QUICK_RECONNECT, signonMaterializer ) ;

    LOGGER.info( "Stopping and restarting asynchronously " + fixture.babyUpend() + "." ) ;
    fixture.babyUpend().stop().join() ;

    fixture.phaseGuide()
        .record( SessionLifecycle.SessionValid.create( DownendFixture.SESSION_IDENTIFIER ) ) ;
    fixture.babyUpend().start().join() ;
    fixture.waitForDownendConnectorState( DownendConnector.State.CONNECTED ) ;
    LOGGER.info( "Reconnection happened after restarting " + fixture.babyUpend() + "." ) ;

    fixture.phaseGuide().waitForInboundMatching(
        textFrame -> textFrame.text().contains( "RESIGNON" ) ) ;

    fixture.waitForDownendConnectorState( DownendConnector.State.SIGNED_IN ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void sessionReuseFails()
      throws Exception
  {
    TestNameTools.setTestThreadName() ;

    final Mockster mockster = new Mockster() ;
    final SignonMaterializer signonMaterializer = mockster.mock( SignonMaterializer.class ) ;

    initializeAndSignon2(
        fixture,
        AbstractConnectorFixture.PingTimingPresets.QUICK_RECONNECT,
        mockster,
        signonMaterializer
    ) ;

    LOGGER.info( "Stopping and restarting " + fixture.babyUpend() + "." ) ;
    fixture.babyUpend().stop().join() ;

    fixture.phaseGuide()
        .record( SessionLifecycle.SignonFailed.create( SIGNON_FAILURE_NOTICE_UNKNOWN_SESSION ) )
        .record( SessionLifecycle.SessionValid.create( DownendFixture.SESSION_IDENTIFIER ) )
    ;

    fixture.babyUpend().start().join() ;
    fixture.phaseGuide().waitForInboundMatching(
        textFrame -> textFrame.text().contains( "RESIGNON" ) ) ;
    LOGGER.info( "Reconnection happened after restarting " + fixture.babyUpend() + "." ) ;
    LOGGER.info( "Full Signon must happen again (skipping Secondary here for brevity)." ) ;

    mockster.verify( () -> {
      final Consumer< Credential > credentialCapture ;
      signonMaterializer.readCredential( credentialCapture = withCapture() ) ;
      nextInvocationIsNonBlockingOperative() ;
      credentialCapture.accept( DownendFixture.CREDENTIAL_OK ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.done() ;

      fixture.waitForDownendConnectorState( DownendConnector.State.SIGNED_IN ) ;
      fixture.checkPhaseGuideOutboundQueueEmptyIfAny() ;
    } ) ;


    final ImmutableList< TextWebSocketFrame > inboundPhaseFrames =
        fixture.phaseGuide().drainInbound() ;
    assertThat( inboundPhaseFrames ).hasSize( 1 ) ;
    assertThat( inboundPhaseFrames.get( 0 ).text() ).contains( "PRIMARY_SIGNON" ) ;

  }

  @Test( timeout = TIMEOUT_MS )
  public void badCredentialThenCancel( @Injectable final SignonMaterializer signonMaterializer )
      throws Exception
  {
    TestNameTools.setTestThreadName() ;
    fixture.initialize( signonMaterializer, AbstractConnectorFixture.PingTimingPresets.NO_PING ) ;

    fixture.phaseGuide().record(
        SessionLifecycle.SignonFailed.create( SIGNON_FAILURE_NOTICE_INVALID_CREDENTIAL ) )
    ;

    final Multicaptor< Consumer< Credential > > credentialCapture = new Multicaptor<>( 2 ) ;

    new Expectations() {{
      signonMaterializer.readCredential( withCapture( credentialCapture ) ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.setProgressMessage( null ) ;
      signonMaterializer.setProblemMessage( SIGNON_FAILURE_NOTICE_INVALID_CREDENTIAL ) ;
      signonMaterializer.done() ;
    }} ;

    fixture.downend().start() ;
    LOGGER.info( "Starting " + fixture.downend() + ", now trying a bad " +
        Credential.class.getSimpleName() + " ..." ) ;

    credentialCapture.get( 0 ).accept( CREDENTIAL_BAD ) ;
    assertThat( fixture.phaseGuide().waitForNextInbound().text() ).contains( "PRIMARY_SIGNON" ) ;

    LOGGER.info( "Bad " + Credential.class.getSimpleName() + " sent, " +
        "asking for a new one tells to give up." ) ;

    credentialCapture.get( 1 ).accept( null ) ;
    fixture.waitForDownendConnectorState( ExtendedChange.ExtendedKind.NO_SIGNON ) ;
    fixture.checkPhaseGuideOutboundQueueEmptyIfAny() ;
    new FullVerifications() {{ }} ;
    LOGGER.info( "Signon failed as supposed to." ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void signonFailsUnrecoverably( @Injectable final SignonMaterializer signonMaterializer )
      throws Exception
  {
    TestNameTools.setTestThreadName() ;
    fixture.initialize( signonMaterializer, AbstractConnectorFixture.PingTimingPresets.NO_PING ) ;

    fixture.phaseGuide().record(
        SessionLifecycle.SignonFailed.create( SIGNON_FAILURE_NOTICE_UNEXPECTED ) )
    ;

    final BlockingMonolist< Consumer< Credential > > credentialCapture = new BlockingMonolist<>() ;
    final BlockingMonolist< Runnable > afterCancellationCapture = new BlockingMonolist<>() ;

    new Expectations() {{
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
    new FullVerifications() {{ }} ;

    fixture.waitForDownendConnectorState( ExtendedChange.ExtendedKind.NO_SIGNON ) ;
    fixture.checkPhaseGuideOutboundQueueEmptyIfAny() ;

    LOGGER.info( "Signon failed as supposed to." ) ;


  }

  @Test( timeout = TIMEOUT_MS )
  public void upwardCommandInterceptor() throws Exception {
    TestNameTools.setTestThreadName() ;
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

    fixture.downend().send( AbstractConnectorFixture.upwardEchoCommand( AbstractConnectorFixture.TAG_0, "intercept-me" ) ) ;

    final Command<Command.Tag, EchoDownwardDuty<Command.Tag> > downwardCommand =
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

  private static < CAPTURED > Delegate< CAPTURED > delegate(
      final List< CAPTURED > captureds
  ) {
    return new Delegate< CAPTURED >() {
      @SuppressWarnings( "unused" )
      void delegate( final CAPTURED consumer ) {
        captureds.add( consumer ) ;
      }
    } ;
  }



}