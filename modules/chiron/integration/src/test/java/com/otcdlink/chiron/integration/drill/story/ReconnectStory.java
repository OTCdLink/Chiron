package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.downend.CommandInFlightStatus;
import com.otcdlink.chiron.downend.CommandTransceiver;
import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.downend.Tracker;
import com.otcdlink.chiron.downend.TrackerCurator;
import com.otcdlink.chiron.fixture.NettyLeakDetectorRule;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import com.otcdlink.chiron.integration.drill.fakeend.FakeUpend;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.toolbox.Credential;
import com.otcdlink.chiron.upend.UpendConnector;
import com.otcdlink.chiron.upend.session.SessionSupervisor;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.otcdlink.chiron.integration.drill.ConnectorDrill.EXECUTE_PING;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.EXECUTE_PONG_TIMEOUT;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.EXECUTE_RECONNECT;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.GOOD_CREDENTIAL;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.SESSION_IDENTIFIER;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.SKIP_PING;
import static com.otcdlink.chiron.mockster.Mockster.any;
import static com.otcdlink.chiron.mockster.Mockster.exactly;
import static com.otcdlink.chiron.mockster.Mockster.withCapture;
import static com.otcdlink.chiron.mockster.Mockster.withNull;

public class ReconnectStory {

  @Test
  public void downendConnectorResignonWithHttpProxy() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .forDownendConnector().done()
        .forUpendConnector().done()
        .withProxy( true )
        .withTimeBoundary( TimeBoundary.newBuilder()
            .pingIntervalNever()
            .pongTimeoutNever()
            .reconnectDelay( 10 )
            .pingTimeoutNever()
            .maximumSessionInactivity( 10_000 )
            .build()
        )
        .build()
    ) {
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant =
          drill.forSimpleDownend().changeAsConstant() ;

      drill.restartHttpProxy() ;

      drill.processNextTaskCapture( SKIP_PING ) ;
      drill.processNextTaskCapture( EXECUTE_RECONNECT ) ;
      drill.forSimpleDownend().changeWatcherMock().stateChanged( changeAsConstant.connecting ) ;

      drill.forUpendConnector().sessionSupervisorMock()
          .closed( any(), exactly( SESSION_IDENTIFIER ), exactly( false ) ) ;

      drill.forSimpleDownend().changeWatcherMock().stateChanged( changeAsConstant.connected ) ;

      final SessionSupervisor.ReuseCallback reuseCallback ;
      drill.forUpendConnector().sessionSupervisorMock()
          .tryReuse( exactly( SESSION_IDENTIFIER ), any(), reuseCallback = withCapture() ) ;

      drill.runOutOfVerifierThread( () -> reuseCallback.reuseOutcome( null ) ) ;

      drill.forSimpleDownend().changeWatcherMock().stateChanged( changeAsConstant.signedIn ) ;

      LOGGER.info( "*** Successfully re-signed on ***" ) ;

      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;

    }
  }

  @Test
  public void reconnectThroughProxyWithOutdatedSession() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .forDownendConnector().done()
        .forUpendConnector().done()
        .withProxy( true )
        .withTimeBoundary( TimeBoundary.newBuilder()
            .pingIntervalNever()
            .pongTimeoutOnDownend( 10 )
            .reconnectDelay( 10 )
            .pingTimeoutNever()
            .maximumSessionInactivity( 10_000 )
            .build()
        )
        .build()
    ) {
      final ConnectorDrill.ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final ConnectorDrill.ForUpendConnector forUpend = drill.forUpendConnector() ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant change =
          forDownend.changeAsConstant() ;

      final CompletableFuture< Void > stopFuture = forUpend.stop() ;
      forUpend.sessionSupervisorMock()
          .closed( any(), exactly( SESSION_IDENTIFIER ), exactly( false ) ) ;
      stopFuture.join() ;
      drill.processNextTaskCapture( EXECUTE_PING ) ;
      drill.processNextTaskCapture( EXECUTE_PONG_TIMEOUT ) ;
      forDownend.changeWatcherMock().stateChanged( change.connecting ) ;
      drill.processNextTaskCapture( EXECUTE_RECONNECT ) ;
      forUpend.start().join() ;
      LOGGER.info( "Done restarting " + UpendConnector.class.getSimpleName() + "." ) ;
      forDownend.changeWatcherMock().stateChanged( change.connected ) ;

      final SessionSupervisor.ReuseCallback reuseCallback ;
      forUpend.sessionSupervisorMock().tryReuse(
          exactly( SESSION_IDENTIFIER ), any(), reuseCallback = withCapture() ) ;
      drill.runOutOfVerifierThread( () ->
          reuseCallback.reuseOutcome( SketchLibrary.SIGNON_FAILURE_NOTICE_UNKNOWN_SESSION ) ) ;

      final Consumer< Credential > credentialConsumer ;
      forDownend.signonMaterializerMock().readCredential(
          credentialConsumer = withCapture() ) ;
      drill.runOutOfVerifierThread( () -> credentialConsumer.accept( GOOD_CREDENTIAL ) ) ;
      forDownend.signonMaterializerMock().setProgressMessage( any() ) ;

      final SessionSupervisor.PrimarySignonAttemptCallback primarySignonAttemptCallback ;
      forUpend.sessionSupervisorMock().attemptPrimarySignon(
          exactly( GOOD_CREDENTIAL.getLogin() ),
          exactly( GOOD_CREDENTIAL.getPassword() ),
          any(),
          any(),
          primarySignonAttemptCallback = withCapture()
      ) ;

      primarySignonAttemptCallback.sessionAttributed( SESSION_IDENTIFIER ) ;
      forDownend.signonMaterializerMock().done() ;
      forDownend.changeWatcherMock().stateChanged( change.signedIn ) ;
    }
  }

  @Test
  public void reconnectThroughProxyWithSessionReuse() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .forDownendConnector().done()
        .forUpendConnector().done()
        .withProxy( true )
        .withTimeBoundary( TimeBoundary.newBuilder()
            .pingIntervalNever()
            .pongTimeoutOnDownend( 10 )
            .reconnectDelay( 10 )
            .pingTimeoutNever()
            .maximumSessionInactivity( 10_000 )
            .build()
        )
        .build()
    ) {
      final ConnectorDrill.ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final ConnectorDrill.ForUpendConnector forUpend = drill.forUpendConnector() ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant change =
          forDownend.changeAsConstant() ;

      final CompletableFuture< Void > stopFuture = forUpend.stop() ;
      forUpend.sessionSupervisorMock()
          .closed( any(), exactly( SESSION_IDENTIFIER ), exactly( false ) ) ;
      stopFuture.join() ;
      drill.processNextTaskCapture( EXECUTE_PING ) ;
      drill.processNextTaskCapture( EXECUTE_PONG_TIMEOUT ) ;
      forDownend.changeWatcherMock().stateChanged( change.connecting ) ;
      drill.processNextTaskCapture( EXECUTE_RECONNECT ) ;
      forUpend.start().join() ;
      LOGGER.info( "Done restarting " + UpendConnector.class.getSimpleName() + "." ) ;
      forDownend.changeWatcherMock().stateChanged( change.connected ) ;

      final SessionSupervisor.ReuseCallback reuseCallback ;
      forUpend.sessionSupervisorMock().tryReuse(
          exactly( SESSION_IDENTIFIER ), any(), reuseCallback = withCapture() ) ;
      drill.runOutOfVerifierThread( () -> reuseCallback.reuseOutcome( null ) ) ;

      forDownend.changeWatcherMock().stateChanged( change.signedIn ) ;
    }
  }

  @Test
  public void downendConnectorKickout() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forDownendConnector()
            .automaticLifecycle( ConnectorDrill.AutomaticLifecycle.START )
            .done()
        .fakeUpend().done()
        .withProxy( true )
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .build()
    ) {
      final DownendConnector.Change change = drill.forSimpleDownend().changeAsConstant().stopped ;
      drill.forFakeUpend().duplex().closing().emit( new CloseWebSocketFrame() ) ;
      drill.forSimpleDownend().changeWatcherMock().stateChanged( change ) ;
    }
  }

  @Test
  public void noUpend() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector()
            .automaticLifecycle( ConnectorDrill.AutomaticLifecycle.STOP )
            .done()
        .fakeUpend()
            .automaticLifecycle( ConnectorDrill.AutomaticLifecycle.STOP )
            .done()
        .build()
    ) {
      final ConnectorDrill.ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant =
          forDownend.changeAsConstant() ;
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;

      LOGGER.info( "Starting " + DownendConnector.class.getSimpleName() + ", " +
          "should fail against non-started " + FakeUpend.class.getSimpleName() + " ..." ) ;
      forDownend.start() ;
      forDownend.changeWatcherMock().stateChanged( changeAsConstant.connecting ) ;
      forDownend.changeWatcherMock().failedConnectionAttempt() ;

      LOGGER.info( "Now starting " + FakeUpend.class.getSimpleName() + " ..." ) ;

      forFakeUpend.start().join() ;

      drill.processNextTaskCapture( EXECUTE_RECONNECT ) ;
      drill.processNextTaskCapture( SKIP_PING ) ;
      forDownend.changeWatcherMock().stateChanged( changeAsConstant.connected ) ;

      LOGGER.info( "Connected." ) ;
      drill.dumpTaskCapture() ;

      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }
  @Test
  public void downendRestart() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector().done()
        .fakeUpend().done()
        .build()
    ) {
      final ConnectorDrill.ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant =
          forDownend.changeAsConstant() ;

      forDownend.stop() ;
      forDownend.changeWatcherMock().stateChanged( changeAsConstant.stopping ) ;
      drill.forFakeUpend().duplex().closing().next() ;
      forDownend.changeWatcherMock().stateChanged( changeAsConstant.stopped ) ;

      forDownend.start() ;
      forDownend.changeWatcherMock().stateChanged( changeAsConstant.connecting ) ;
      forDownend.changeWatcherMock().stateChanged( changeAsConstant.connected ) ;

      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  public void simpleReconnect() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector().done()
        .fakeUpend().done()
        .build()
    ) {
      final ConnectorDrill.ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant =
          forDownend.changeAsConstant() ;
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;

      forFakeUpend.stop().join() ;
      forFakeUpend.start().join() ;

      drill.processNextTaskCapture( SKIP_PING ) ;
      drill.processNextTaskCapture( EXECUTE_RECONNECT ) ;
      forDownend.changeWatcherMock().stateChanged( changeAsConstant.connecting ) ;
      forDownend.changeWatcherMock().stateChanged( changeAsConstant.connected ) ;

      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  public void commandTransceiverPingTimeoutAndReconnect() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .withTimeBoundary( TimeBoundary.newBuilder()
            .pingInterval( 5 )
            .pongTimeoutOnDownend( 50 )  /** {@link TrackerCurator} using this. */
            .reconnectDelay( 50, 50 )
            .pingTimeoutOnUpend( 50 )
            .sessionInactivityForever()
            .build()
        )
        .forCommandTransceiver().done()
        .fakeUpend().done()
        .build()
    ) {
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;
      final ConnectorDrill.ForCommandTransceiver forDownend = drill.forCommandTransceiver() ;
      final Tracker trackerMock = forDownend.trackerMock() ;
      final ConnectorDrill.ForCommandTransceiver.ChangeAsConstant change =
          forDownend.changeAsConstant() ;

      forDownend.upwardDuty().requestEcho( trackerMock, "Yay" ) ;
      forDownend.changeWatcherMock().inFlightStatusChange( change.commandInFlightStatusInFlight ) ;
      forFakeUpend.duplex().texting().next() ;

      drill.processNextTaskCapture( EXECUTE_PING ) ;
      forFakeUpend.duplex().ponging().next() ;

      /** Pong timeout triggers {@link TrackerCurator}'s timeout evaluation. */
      drill.processNextTaskCapture( EXECUTE_PONG_TIMEOUT ) ;

      drill.processNextTaskCapture( EXECUTE_RECONNECT ) ;
      trackerMock.onConnectionLost() ;
      forDownend.changeWatcherMock().stateChanged( change.connecting ) ;
      trackerMock.onConnectionRestored() ;
      forDownend.changeWatcherMock().inFlightStatusChange( change.commandInFlightStatusQuiet ) ;
      forDownend.changeWatcherMock().stateChanged( change.connected ) ;

    }
  }

  @Test
  public void trackerTimeout() throws Exception {
    final int delay = 50 ;
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .withTimeBoundary( TimeBoundary.newBuilder()
            .pingInterval( 5 )
            .pongTimeoutOnDownend( delay )  /** {@link TrackerCurator} using this. */
            .reconnectDelay( delay )
            .pingTimeoutNever()
            .sessionInactivityForever()
            .build()
        )
        .forCommandTransceiver().done()
        .fakeUpend().done()
        .build()
    ) {
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;
      final ConnectorDrill.ForCommandTransceiver forDownend = drill.forCommandTransceiver() ;
      final Tracker trackerMock = forDownend.trackerMock() ;
      final ConnectorDrill.ForCommandTransceiver.ChangeAsConstant change =
          forDownend.changeAsConstant() ;

      final CommandTransceiver.ScavengeActivator.Voluntary scavengeActivator =
          drill.forCommandTransceiver().applyDirectly(
              CommandTransceiver.ScavengeActivator::voluntary )
      ;

      forDownend.upwardDuty().requestEcho( trackerMock, "Yay" ) ;
      forDownend.changeWatcherMock().inFlightStatusChange( change.commandInFlightStatusInFlight ) ;
      forFakeUpend.duplex().texting().next() ;

      drill.timeKit().clock.increment( delay + 1 ) ;

      drill.runOutOfVerifierThread( scavengeActivator::scavengeNow ) ;

      forDownend.changeWatcherMock().inFlightStatusChange(
          CommandInFlightStatus.SOME_COMMAND_FAILED ) ;

      forDownend.changeWatcherMock().inFlightStatusChange( change.commandInFlightStatusQuiet ) ;

      trackerMock.afterTimeout() ;
    }
  }

  @Test
  public void sessionReuse() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector().done()
        .fakeUpend().withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR ).done()
        .build()
    ) {
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;
      final ConnectorDrill.ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant =
          forDownend.changeAsConstant() ;
      final DownendConnector.ChangeWatcher changeWatcherMock =
          forDownend.changeWatcherMock() ;

      LOGGER.info( "Restarting " + FakeUpend.class.getSimpleName() + " ..." ) ;

      forFakeUpend.stop().join() ;
      forFakeUpend.start().join() ;

      LOGGER.info( "Restarted " + FakeUpend.class.getSimpleName() + "." ) ;

      drill.processNextTaskCapture( SKIP_PING ) ;
      drill.processNextTaskCapture( EXECUTE_RECONNECT ) ;
      changeWatcherMock.stateChanged( changeAsConstant.connecting ) ;
      changeWatcherMock.stateChanged( changeAsConstant.connected ) ;

      forFakeUpend.duplex().texting().assertThatNext().hasTextContaining( "RESIGNON" ) ;

      forFakeUpend.duplex().texting().emitPhase(
          SessionLifecycle.SessionValid.create( SESSION_IDENTIFIER ) ) ;

      changeWatcherMock.stateChanged( changeAsConstant.signedIn ) ;

    }
  }

  @Test
  public void sessionReuseFails() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector().done()
        .fakeUpend().withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR ).done()
        .build()
    ) {
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;
      final ConnectorDrill.ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant =
          forDownend.changeAsConstant() ;
      final DownendConnector.ChangeWatcher changeWatcherMock =
          forDownend.changeWatcherMock() ;

      LOGGER.info( "Restarting " + FakeUpend.class.getSimpleName() + " ..." ) ;

      forFakeUpend.stop().join() ;
      forFakeUpend.start().join() ;

      LOGGER.info( "Restarted " + FakeUpend.class.getSimpleName() + "." ) ;

      drill.processNextTaskCapture( SKIP_PING ) ;
      drill.processNextTaskCapture( EXECUTE_RECONNECT ) ;
      changeWatcherMock.stateChanged( changeAsConstant.connecting ) ;
      changeWatcherMock.stateChanged( changeAsConstant.connected ) ;

      forFakeUpend.duplex().texting().assertThatNext().hasTextContaining( "RESIGNON" ) ;

      forFakeUpend.duplex().texting().emitPhase(
          SessionLifecycle.SignonFailed.create( SketchLibrary.SIGNON_FAILURE_NOTICE_UNKNOWN_SESSION ) ) ;

      /** From here we copy-paste from {@link SketchLibrary#START_WITH_FAKE_UPEND}. */

      final Consumer< Credential > credentialConsumer ;
      forDownend.signonMaterializerMock().readCredential( credentialConsumer = withCapture() ) ;

      // Removed from the copy-paste:
      // changeWatcherMock.stateChanged( changeAsConstant.connected ) ;
      // forDownend.signonMaterializerMock().setProgressMessage( withNull() ) ;

      drill.runOutOfVerifierThread( () ->
          credentialConsumer.accept( ConnectorDrill.GOOD_CREDENTIAL ) ) ;

      forDownend.signonMaterializerMock().setProgressMessage( "Signing in …" ) ;
      forFakeUpend.duplex().texting().assertThatNext().hasTextContaining( "PRIMARY_SIGNON" ) ;

      forFakeUpend.duplex().texting().emitPhase(
          SessionLifecycle.SessionValid.create( SESSION_IDENTIFIER ) ) ;

      forDownend.signonMaterializerMock().done() ;

      changeWatcherMock.stateChanged( changeAsConstant.signedIn ) ;

    }
  }


  @Test
  public void newTimeBoundary() throws Exception {

    final TimeBoundary.ForAll timeBoundary1 = TimeBoundary.newBuilder()
        .pingInterval( 888_888_888 )
        .pongTimeoutNever()
        .reconnectNever()
        .pingTimeoutNever()
        .sessionInactivityImmediate()
        .build()
    ;
    final TimeBoundary.ForAll timeBoundary2 = TimeBoundary.newBuilder()
        .pingInterval( 999_999_999 )
        .pongTimeoutNever()
        .reconnectNever()
        .pingTimeoutNever()
        .sessionInactivityImmediate()
        .build()
    ;

    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .withTimeBoundary( timeBoundary1 )
        .forDownendConnector()
        .done()
        .forUpendConnector().done()
        .build()
    ) {

      final ConnectorDrill.ForUpendConnector forUpend = drill.forUpendConnector() ;
      final ConnectorDrill.ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant change1 =
          forDownend.changeAsConstant() ;
      final DownendConnector.ChangeWatcher changeWatcherMock =
          forDownend.changeWatcherMock() ;

      final CompletableFuture< Void > stopFuture = forDownend.stop() ;
      changeWatcherMock.stateChanged( change1.stopping ) ;
      changeWatcherMock.stateChanged( change1.stopped ) ;
      forUpend.sessionSupervisorMock().closed( any(), any(), exactly( true ) ) ;
      stopFuture.join() ;

      drill.changeTimeBoundary( timeBoundary2 ) ;
      LOGGER.info( "Now using " + timeBoundary2 + "." ) ;

      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant change2 =
          forDownend.changeAsConstant() ;

      LOGGER.info( "Restarting " + forDownend + "..." ) ;

      forDownend.start() ;

      changeWatcherMock.stateChanged( change2.connecting ) ;

      final Consumer< Credential > credentialConsumer ;
      forDownend.signonMaterializerMock().readCredential( credentialConsumer = withCapture() ) ;

      /** Asserting on new {@link ConnectionDescriptor}. */
      changeWatcherMock.stateChanged( change2.connected ) ;

      forDownend.signonMaterializerMock().setProgressMessage( withNull() ) ;

      drill.runOutOfVerifierThread( () ->
          credentialConsumer.accept( ConnectorDrill.GOOD_CREDENTIAL ) ) ;

      forDownend.signonMaterializerMock().setProgressMessage( "Signing in …" ) ;

      final SessionSupervisor.PrimarySignonAttemptCallback primarySignonAttemptCallback ;
      forUpend.sessionSupervisorMock().attemptPrimarySignon(
          exactly( ConnectorDrill.GOOD_CREDENTIAL.getLogin() ),
          exactly( ConnectorDrill.GOOD_CREDENTIAL.getPassword() ),
          any(),
          any(),
          primarySignonAttemptCallback = withCapture()
      ) ;
      drill.runOutOfVerifierThread( () ->
          primarySignonAttemptCallback.sessionAttributed( SESSION_IDENTIFIER ) ) ;

      forDownend.signonMaterializerMock().done() ;

      changeWatcherMock.stateChanged( change2.signedIn ) ;

      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;

    }

  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( ReconnectStory.class ) ;

  @Rule
  public final NettyLeakDetectorRule nettyLeakDetectorRule = new NettyLeakDetectorRule() ;

}
