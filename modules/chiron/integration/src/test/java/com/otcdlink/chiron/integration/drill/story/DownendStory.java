package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.downend.CommandInFlightStatus;
import com.otcdlink.chiron.downend.CommandTransceiver;
import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.downend.Tracker;
import com.otcdlink.chiron.downend.TrackerCurator;
import com.otcdlink.chiron.fixture.NettyLeakDetectorExtension;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.ConnectorDrill.ForSimpleDownend;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import com.otcdlink.chiron.integration.drill.SketchLibrary.Interceptor;
import com.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.toolbox.Credential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.otcdlink.chiron.integration.drill.ConnectorDrill.AutomaticLifecycle.NONE;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.AutomaticLifecycle.START;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.EXECUTE_PING;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.EXECUTE_PONG_TIMEOUT;
import static com.otcdlink.chiron.integration.drill.SketchLibrary.Interceptor.MAGIC_INTERCEPTED;
import static com.otcdlink.chiron.integration.drill.SketchLibrary.Interceptor.MAGIC_PLEASE_INTERCEPT;
import static com.otcdlink.chiron.integration.drill.SketchLibrary.TAG_TR0;
import static com.otcdlink.chiron.integration.drill.SketchLibrary.TAG_TR1;
import static com.otcdlink.chiron.mockster.Mockster.exactly;
import static com.otcdlink.chiron.mockster.Mockster.withCapture;
import static com.otcdlink.chiron.mockster.Mockster.withNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith( NettyLeakDetectorExtension.class )
class DownendStory {

  @Test
  void doubleStart() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forDownendConnector().done()
        .fakeUpend().done()
        .build()
    ) {
      assertThatThrownBy( () ->
          drill.forSimpleDownend().applyDirectly( DownendConnector::start, false )
      )   .isInstanceOf( IllegalStateException.class ) ;
    }
  }

  @Test
  void doubleStop() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forDownendConnector().automaticLifecycle( START ).done()
        .fakeUpend().done()
        .build()
    ) {
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;
      final ForSimpleDownend forSimpleDownend = drill.forSimpleDownend() ;
      final ForSimpleDownend.ChangeAsConstant change = forSimpleDownend.changeAsConstant() ;

      forSimpleDownend.stop() ;
      forSimpleDownend.changeWatcherMock().stateChanged( change.stopping ) ;
      forSimpleDownend.changeWatcherMock().stateChanged( change.stopped ) ;
      forFakeUpend.duplex().closing().next() ;

      drill.forSimpleDownend().applyDirectly( DownendConnector::stop, false ) ; // Does nothing.
    }
  }

  @Test
  void brokenCommand() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forDownendConnector().done()
        .fakeUpend().done()
        .build()
    ) {
      final ForSimpleDownend forSimpleDownend = drill.forSimpleDownend();

      final UpwardEchoCommand< Command.Tag > brokenEchoCommand =
          SketchLibrary.upwardBrokenEchoCommand( SketchLibrary.TAG_TR0 ) ;
      forSimpleDownend.applyDirectly( connector -> connector.send( brokenEchoCommand ), false ) ;
    }
  }

  @Test
  void brokenCommandInCommandTransceiver() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forCommandTransceiver().done()
        .fakeUpend().done()
        .build()
    ) {
      final ConnectorDrill.ForCommandTransceiver forDownend = drill.forCommandTransceiver() ;
      final ConnectorDrill.ForCommandTransceiver.ChangeAsConstant change =
          forDownend.changeAsConstant() ;

      final UpwardEchoCommand< Tracker > brokenEchoCommand =
          SketchLibrary.upwardBrokenEchoCommand( forDownend.trackerMock() ) ;
      forDownend.applyDirectly( connector -> connector.send( brokenEchoCommand ), false ) ;
      forDownend.changeWatcherMock().inFlightStatusChange( change.commandInFlightStatusInFlight ) ;
      // TODO signal the problem, which appeared during migration to ConnectorDrill.
    }
  }

  @Test
  void ping() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withTimeBoundary( TimeBoundary.newBuilder()
            .pingInterval( 1 )
            .pongTimeoutNever()
            .reconnectImmediately()
            .pingTimeoutNever()
            .sessionInactivityForever()
            .build()
        )
        .forDownendConnector().done()
        .fakeUpend().done()
        .build()
    ) {
      drill.processNextTaskCapture( EXECUTE_PING ) ;
      drill.forFakeUpend().duplex().ponging().next() ;
    }
  }

  @Test
  void pingTimeoutCausesDisconnection() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withTimeBoundary( TimeBoundary.newBuilder()
            .pingInterval( 1 )
            .pongTimeoutOnDownend( 1 )
            .reconnectImmediately()
            .pingTimeoutNever()
            .sessionInactivityForever()
            .build()
        )
        .forDownendConnector().automaticLifecycle( START ).done()
        .fakeUpend().done()
        .build()
    ) {
      final ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final ForSimpleDownend.ChangeAsConstant change = forDownend.changeAsConstant() ;
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;

      drill.processNextTaskCapture( EXECUTE_PING ) ;
      forFakeUpend.duplex().ponging().next() ;
      drill.processNextTaskCapture( EXECUTE_PONG_TIMEOUT ) ;
      forDownend.changeWatcherMock().stateChanged( change.connecting ) ;

      /** Automatic stop doesn't expect to be {@link DownendConnector.State#CONNECTING}. */
      forDownend.applyDirectly( DownendConnector::stop, true ) ;
      forDownend.changeWatcherMock().stateChanged( change.stopping ) ;
      forDownend.changeWatcherMock().stateChanged( change.stopped ) ;

    }
  }


  @Test
  void echo() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forDownendConnector().done()
        .fakeUpend().done()
        .build()
    ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  void signonAndEcho() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forDownendConnector().done()
        .fakeUpend().withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR ).done()
        .build()
    ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }


  @Test
  void echoWithCommandTransceiver() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forCommandTransceiver().done()
        .fakeUpend().done()
        .build()
    ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  void trackerSegregation() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .forCommandTransceiver().done()
        .fakeUpend().done()
        .build()
    ) {
      final Tracker trackerMock1 = drill.forCommandTransceiver().trackerMock() ;
      final Tracker trackerMock2 = drill.forCommandTransceiver().newTrackerMock() ;
      final CommandTransceiver.ChangeWatcher changeWatcherMock =
          drill.forCommandTransceiver().changeWatcherMock() ;

      drill.forCommandTransceiver().upwardDuty().requestEcho( trackerMock1, "One" ) ;
      changeWatcherMock.inFlightStatusChange( CommandInFlightStatus.IN_FLIGHT ) ;
      drill.forCommandTransceiver().upwardDuty().requestEcho( trackerMock2, "Two" ) ;

      drill.forFakeUpend().duplex().texting().next() ;
      drill.forFakeUpend().duplex().texting().emitWithDutyCall().echoResponse( TAG_TR0, "111" ) ;
      final Tracker trackerCapture1 ;
      drill.forCommandTransceiver().downwardDutyMock().echoResponse(
          trackerCapture1 = withCapture(), exactly( "111" ) ) ;
      assertThat( extractTracker( trackerCapture1 ) ).isSameAs( trackerMock1 ) ;
      trackerMock1.afterResponseHandled() ;

      drill.forFakeUpend().duplex().texting().next() ;
      drill.forFakeUpend().duplex().texting().emitWithDutyCall().echoResponse( TAG_TR1, "222" ) ;
      final Tracker trackerCapture2 ;
      drill.forCommandTransceiver().downwardDutyMock().echoResponse(
          trackerCapture2 = withCapture(), exactly( "222" ) ) ;
      assertThat( extractTracker( trackerCapture2 ) ).isSameAs( trackerMock2 ) ;

      changeWatcherMock.inFlightStatusChange( CommandInFlightStatus.QUIET ) ;
      trackerMock2.afterResponseHandled() ;
    }
  }

  @Test
  void signonAndEchoWithCommandTransceiver() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forCommandTransceiver().done()
        .fakeUpend().withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR ).done()
        .build()
    ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  void badCredentialThenCancel() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector()
            .automaticLifecycle( NONE )
            .done()
        .fakeUpend()
            .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
            .done()
        .build()
    ) {
      final ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final DownendConnector.ChangeWatcher changeWatcherMock = forDownend.changeWatcherMock() ;
      final ForSimpleDownend.ChangeAsConstant change = forDownend.changeAsConstant() ;
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;

      forDownend.start() ;

      changeWatcherMock.stateChanged( change.connecting ) ;

      final Consumer< Credential > credentialConsumer1 ;
      forDownend.signonMaterializerMock().readCredential( credentialConsumer1 = withCapture() ) ;

      changeWatcherMock.stateChanged( change.connected ) ;

      forDownend.signonMaterializerMock().setProgressMessage( withNull() ) ;

      drill.runOutOfVerifierThread( () ->
          credentialConsumer1.accept( SketchLibrary.BAD_CREDENTIAL ) ) ;

      forDownend.signonMaterializerMock().setProgressMessage( "Signing in …" ) ;
      forFakeUpend.duplex().texting().assertThatNext().hasTextContaining( "PRIMARY_SIGNON" ) ;

      forFakeUpend.duplex().texting().emitPhase(
          SessionLifecycle.SignonFailed.create( SketchLibrary.INVALID_CREDENTIAL ) ) ;

      forDownend.signonMaterializerMock().setProblemMessage(
          SketchLibrary.INVALID_CREDENTIAL ) ;

      final Consumer< Credential > credentialConsumer2 ;
      forDownend.signonMaterializerMock().readCredential( credentialConsumer2 = withCapture() ) ;

      drill.runOutOfVerifierThread( () -> credentialConsumer2.accept( null ) ) ;

      forDownend.signonMaterializerMock().done() ;

      forDownend.stop() ;

      changeWatcherMock.noSignon() ;

      changeWatcherMock.stateChanged( change.stopping ) ;

      changeWatcherMock.stateChanged( change.stopped ) ;

      forFakeUpend.duplex().closing().next() ;
    }
  }

  @Test
  void signonFailsUnrecoverably() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector()
            .automaticLifecycle( NONE )
            .done()
        .fakeUpend()
            .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
            .done()
        .build()
    ) {
      final ForSimpleDownend forDownend = drill.forSimpleDownend() ;
      final DownendConnector.ChangeWatcher changeWatcherMock = forDownend.changeWatcherMock() ;
      final ForSimpleDownend.ChangeAsConstant change = forDownend.changeAsConstant() ;
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;

      forDownend.start() ;

      changeWatcherMock.stateChanged( change.connecting ) ;

      final Consumer< Credential > credentialConsumer1 ;
      forDownend.signonMaterializerMock().readCredential( credentialConsumer1 = withCapture() ) ;

      changeWatcherMock.stateChanged( change.connected ) ;

      forDownend.signonMaterializerMock().setProgressMessage( withNull() ) ;

      drill.runOutOfVerifierThread( () ->
          credentialConsumer1.accept( SketchLibrary.BAD_CREDENTIAL ) ) ;

      forDownend.signonMaterializerMock().setProgressMessage( "Signing in …" ) ;

      forFakeUpend.duplex().texting().next() ;

      forFakeUpend.duplex().texting().emitPhase(
          SessionLifecycle.SignonFailed.create( SketchLibrary.UNEXPECTED_FAILURE ) ) ;

      forDownend.signonMaterializerMock().setProblemMessage( SketchLibrary.UNEXPECTED_FAILURE ) ;

      final Runnable cancellationSignaller ;
      forDownend.signonMaterializerMock().waitForCancellation(
          cancellationSignaller = withCapture() ) ;

      drill.runOutOfVerifierThread( cancellationSignaller ) ;

      changeWatcherMock.noSignon() ;

      forDownend.stop() ;

      changeWatcherMock.stateChanged( change.stopping ) ;

      changeWatcherMock.stateChanged( change.stopped ) ;

      forFakeUpend.duplex().closing().next() ;
    }
  }

  @Test
  void commandInterceptor() throws Exception {

    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector()
            .withCommandInterceptor( Interceptor.commandInterceptor( LOGGER ) )
            .done()
        .fakeUpend().done()
        .build()
    ) {
      drill.forSimpleDownend().upwardDuty().requestEcho( TAG_TR0, MAGIC_PLEASE_INTERCEPT ) ;
      drill.forSimpleDownend().downwardDutyMock().echoResponse( TAG_TR0, MAGIC_INTERCEPTED ) ;

      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }



// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( DownendStory.class ) ;

  private static Tracker extractTracker( final Tracker enhanced ) {
    try {
      final Class enhancedTrackerClass = Class.forName(
          TrackerCurator.class.getName() + "$TrackerEnhancer" ) ;
      final Field privateTracker = enhancedTrackerClass.getDeclaredField( "tracker" ) ;
      privateTracker.setAccessible( true ) ;
      return ( Tracker ) privateTracker.get( enhanced ) ;
    } catch( ClassNotFoundException | NoSuchFieldException | IllegalAccessException e ) {
      throw new RuntimeException( e ) ;
    }
  }
}
