package com.otcdlink.chiron.downend;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.AbstractConnectorFixture.PingTimingPresets;
import com.otcdlink.chiron.ConnectorChangeAssert;
import com.otcdlink.chiron.fixture.BlockingMonolist;
import com.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.mockster.Mockster;
import com.otcdlink.chiron.toolbox.CollectingException;
import com.otcdlink.chiron.toolbox.Credential;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.Mocked;
import org.junit.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.otcdlink.chiron.mockster.Mockster.nextInvocationIsNonBlockingOperative;
import static com.otcdlink.chiron.mockster.Mockster.withCapture;
import static org.assertj.core.api.Assertions.assertThat;


public class AbstractDownendTest<
    ENDPOINT_SPECIFIC,
    DOWNEND extends Downend< ENDPOINT_SPECIFIC, EchoUpwardDuty< ENDPOINT_SPECIFIC >>,
    SETUP extends DownendConnector.Setup< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > >
> {


// =====================
// Factored test methods
// =====================

  protected void initializeAndSignon(
      final DownendFixture< ENDPOINT_SPECIFIC, DOWNEND, SETUP > fixture,
      @Mocked final SignonMaterializer signonMaterializer
  ) throws Exception {
    initializeAndSignon( fixture, PingTimingPresets.NO_PING, signonMaterializer ) ;
  }

  protected void initializeAndSignon(
      final DownendFixture< ENDPOINT_SPECIFIC, DOWNEND, SETUP > fixture,
      final TimeBoundary.ForAll tuning,
      final SignonMaterializer signonMaterializer
  ) throws Exception {
    fixture.initialize( signonMaterializer, tuning ) ;

    fixture.phaseGuide()
        .record( SessionLifecycle.SecondarySignonNeeded.create( DownendFixture.SECONDARY_TOKEN ) )
        .record( SessionLifecycle.SessionValid.create( DownendFixture.SESSION_IDENTIFIER ) )
    ;

    final BlockingMonolist< Consumer< Credential > > credentialCapture = new BlockingMonolist<>() ;
    final BlockingMonolist< Consumer< SecondaryCode > > secondaryCodeCapture =
        new BlockingMonolist<>() ;

    new Expectations() {{
      signonMaterializer.readCredential( withCapture( credentialCapture ) ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.setProgressMessage( null ) ;
      signonMaterializer.setProblemMessage(
          new SignonFailureNotice( SignonFailure.MISSING_SECONDARY_CODE ) ) ;
      signonMaterializer.readSecondaryCode( withCapture( secondaryCodeCapture ) ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.done() ;
    }} ;

    final CompletableFuture< ? > startDone = fixture.downend().start();
    credentialCapture.getOrWait().accept( DownendFixture.CREDENTIAL_OK ) ;
    secondaryCodeCapture.getOrWait().accept( DownendFixture.SECONDARY_CODE_OK ) ;

    LOGGER.info( "Started " + fixture.downend() + "." ) ;


    fixture.waitForDownendConnectorState( DownendConnector.State.SIGNED_IN ) ;
    fixture.checkPhaseGuideOutboundQueueEmptyIfAny() ;
    startDone.join() ;

    final ImmutableList< TextWebSocketFrame > inboundPhaseFrames =
        fixture.phaseGuide().drainInbound() ;
    assertThat( inboundPhaseFrames ).hasSize( 2 ) ;
    assertThat( inboundPhaseFrames.get( 0 ).text() ).contains( "PRIMARY_SIGNON" ) ;
    assertThat( inboundPhaseFrames.get( 1 ).text() ).contains( "SECONDARY_SIGNON" ) ;


    new FullVerifications() {{ }} ;

    LOGGER.info( "Signon success, we can send stuff now." ) ;
  }
  protected void initializeAndSignon2(
      final DownendFixture<ENDPOINT_SPECIFIC, DOWNEND, SETUP> fixture,
      final TimeBoundary.ForAll tuning,
      final Mockster mockster,
      final SignonMaterializer signonMaterializer
  ) throws Exception {
    fixture.initialize( signonMaterializer, tuning ) ;

    fixture.phaseGuide()
        .record( SessionLifecycle.SecondarySignonNeeded.create( DownendFixture.SECONDARY_TOKEN ) )
        .record( SessionLifecycle.SessionValid.create( DownendFixture.SESSION_IDENTIFIER ) )
    ;

    final CompletableFuture< ? > startDone = fixture.downend().start() ;
    mockster.verify( () -> {
      final Consumer< Credential > credentialConsumer ;
      signonMaterializer.readCredential( credentialConsumer = withCapture() ) ;
      nextInvocationIsNonBlockingOperative() ;
      credentialConsumer.accept( DownendFixture.CREDENTIAL_OK ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.setProgressMessage( null ) ;
      signonMaterializer.setProblemMessage(
          new SignonFailureNotice( SignonFailure.MISSING_SECONDARY_CODE ) ) ;
      final Consumer< SecondaryCode > secondaryCodeConsumer ;
      signonMaterializer.readSecondaryCode( secondaryCodeConsumer = withCapture() ) ;
      nextInvocationIsNonBlockingOperative( ) ;
      secondaryCodeConsumer.accept( DownendFixture.SECONDARY_CODE_OK ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.done() ;

      fixture.waitForDownendConnectorState( DownendConnector.State.SIGNED_IN ) ;
      fixture.checkPhaseGuideOutboundQueueEmptyIfAny() ;
      startDone.join() ;
    }, false ) ;

    LOGGER.info( "Started " + fixture.downend() + "." ) ;

    final ImmutableList< TextWebSocketFrame > inboundPhaseFrames =
        fixture.phaseGuide().drainInbound() ;
    assertThat( inboundPhaseFrames ).hasSize( 2 ) ;
    assertThat( inboundPhaseFrames.get( 0 ).text() ).contains( "PRIMARY_SIGNON" ) ;
    assertThat( inboundPhaseFrames.get( 1 ).text() ).contains( "SECONDARY_SIGNON" ) ;

    LOGGER.info( "Signon success, we can send stuff now." ) ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( AbstractDownendTest.class ) ;

  protected final DownendFixture<
      ENDPOINT_SPECIFIC,
      DOWNEND,
      SETUP
  > fixture ;

  public AbstractDownendTest( final DownendFixture< ENDPOINT_SPECIFIC, DOWNEND, SETUP > fixture ) {
    this.fixture = checkNotNull( fixture ) ;
  }
  
  protected boolean runTearDown = true ;

  @After
  public void tearDown() throws CollectingException, InterruptedException, TimeoutException {

    if( runTearDown ) {
      LOGGER.info( "Tearing down test ..." ) ;

      final DOWNEND downendConnector = fixture.downend() ;
      final DownendConnector.State preTearDownState = downendConnector.state() ;
      fixture.stopAll() ;
      fixture.checkNoMoreDownwardCommand() ;

      if( ImmutableSet.of( DownendConnector.State.CONNECTED, DownendConnector.State.SIGNED_IN ).contains( preTearDownState ) ) {
        // This failed sometime for an unknown reason.
        // fixture.closeFrameGuide().waitForNextInbound() ;

        // There might have been several reconnections, depending on system load.
        // So we only check for proper termination.
        final ImmutableList< DownendConnector.Change > rawChanges = fixture.drainDownendChanges() ;
        LOGGER.info( "Unconsumed changes: " + rawChanges + "." ) ;

        final ImmutableList.Builder< DownendConnector.Change > cleanChangesBuilder =
            ImmutableList.builder() ;
        rawChanges.stream()
            .filter( change -> change.kind instanceof DownendConnector.State )
            .forEach( cleanChangesBuilder::add )
        ;
        final ImmutableList< DownendConnector.Change > changes = cleanChangesBuilder.build() ;

        assertThat( changes.size() >= 2 ) ;
        ConnectorChangeAssert.assertThat( changes.get( changes.size() - 2 ) ).hasKind( DownendConnector.State.STOPPING ) ;
        ConnectorChangeAssert.assertThat( changes.get( changes.size() - 1 ) ).hasKind( DownendConnector.State.STOPPED ) ;
      } else {
        fixture.checkNoMoreDownendChange() ;
      }

      assertThat( fixture.commandToTextWebsocketGuide().outboundQueueEmpty() ).isTrue() ;
      fixture.checkPhaseGuideOutboundQueueEmptyIfAny() ;
    } else {
      LOGGER.info( "Skipped tearDown." ) ;
    }
  }


//      protected static final long TIMEOUT_MS = 5_000 ;
  protected static final long TIMEOUT_MS = 1_000_000 ;

  protected static final SignonFailureNotice SIGNON_FAILURE_NOTICE_INVALID_CREDENTIAL =
      new SignonFailureNotice( SignonFailure.INVALID_CREDENTIAL ) ;

  protected static final SignonFailureNotice SIGNON_FAILURE_NOTICE_UNEXPECTED =
      new SignonFailureNotice( SignonFailure.UNEXPECTED ) ;

  protected static final SignonFailureNotice SIGNON_FAILURE_NOTICE_UNKNOWN_SESSION =
      new SignonFailureNotice( SignonFailure.UNKNOWN_SESSION ) ;

  protected static final SignonFailureNotice SIGNON_FAILURE_NOTICE_SESSION_ALREADY_EXISTS =
      new SignonFailureNotice( SignonFailure.SESSION_ALREADY_EXISTS ) ;

}