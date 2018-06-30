package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.downend.Tracker;
import com.otcdlink.chiron.downend.TrackerCurator;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.ConnectorDrill.ForSimpleDownend;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

import static com.otcdlink.chiron.integration.drill.ConnectorDrill.AutomaticLifecycle.NONE;
import static com.otcdlink.chiron.mockster.Mockster.exactly;
import static com.otcdlink.chiron.mockster.Mockster.withCapture;
import static org.assertj.core.api.Assertions.assertThat;

public class DownendStory {

  @Test
  public void connectDownendAndEcho() throws Exception {
    LOGGER.info( "*** Test starts here ***" ) ;
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forDownendConnector()
            .automaticLifecycle( NONE )
            .done()
        .fakeUpend().done()
        .build()
    ) {
      final ForSimpleDownend forSimpleDownend = drill.forSimpleDownend() ;
      final ForSimpleDownend.ChangeAsConstant change = forSimpleDownend.changesAsConstant() ;

      forSimpleDownend.start() ;
      forSimpleDownend.changeWatcherMock().stateChanged( change.connecting ) ;
      forSimpleDownend.changeWatcherMock().stateChanged( change.connected ) ;
      LOGGER.info( "*** Connected, now echoing ***" ) ;

      forSimpleDownend.upwardDuty().requestEcho( TAG_TR0, "Hello" ) ;
      drill.forFakeUpend().halfDuplex().texting().assertThatNext().hasTextContaining( "Hello" ) ;
      drill.forFakeUpend().halfDuplex().texting().emitWithDutyCall().echoResponse( TAG_TR1, "Yo!" ) ;
      forSimpleDownend.downwardDutyMock().echoResponse( TAG_TR1, "Yo!" ) ;
      LOGGER.info( "*** Echoed, now stopping ***" ) ;

      forSimpleDownend.stop() ;
      forSimpleDownend.changeWatcherMock().stateChanged( change.stopping ) ;
      drill.forFakeUpend().halfDuplex().closing().next() ;
      forSimpleDownend.changeWatcherMock().stateChanged( change.stopped ) ;
      LOGGER.info( "*** Stopped, let cleanup happen ***" ) ;
    }
  }


  @Test
  public void connectCommandTransceiverAndEcho() throws Exception {
    LOGGER.info( "*** Test starts here ***" ) ;
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forCommandTransceiver()
            .automaticLifecycle( NONE )
            .done()
        .fakeUpend().done()
        .build()
    ) {
      final ConnectorDrill.ForCommandTransceiver forCommandTransceiver =
          drill.forCommandTransceiver() ;
      final ConnectorDrill.ForCommandTransceiver.ChangeAsConstant change =
          forCommandTransceiver.changesAsConstants() ;

      forCommandTransceiver.start() ;
      forCommandTransceiver.changeWatcherMock().stateChanged( change.connecting ) ;
      forCommandTransceiver.changeWatcherMock().stateChanged( change.connected ) ;
      LOGGER.info( "*** Connected, now echoing ***" ) ;

      forCommandTransceiver.upwardDuty().requestEcho(
          forCommandTransceiver.trackerMock(), "Hey" ) ;
      forCommandTransceiver.changeWatcherMock().inFlightStatusChange(
          change.commandInFlightStatusInFlight ) ;
      drill.forFakeUpend().halfDuplex().texting().assertThatNext().hasTextContaining( "Hey" ) ;

      drill.forFakeUpend().halfDuplex().texting().emitWithDutyCall().echoResponse( TAG_TR0, "Uh" ) ;
      final Tracker capturedTracker ;
      forCommandTransceiver.downwardDutyMock()
          .echoResponse( capturedTracker = withCapture(), exactly( "Uh" ) ) ;
      assertThat( extractTracker( capturedTracker ) )
          .isSameAs( forCommandTransceiver.trackerMock() ) ;
      forCommandTransceiver.changeWatcherMock().inFlightStatusChange(
          change.commandInFlightStatusQuiet ) ;
      forCommandTransceiver.trackerMock().afterResponseHandled() ;

      LOGGER.info( "*** Echoed, now stopping ***" ) ;
      forCommandTransceiver.stop() ;

      forCommandTransceiver.changeWatcherMock().stateChanged( change.stopping ) ;
      drill.forFakeUpend().halfDuplex().closing().next() ;
      forCommandTransceiver.changeWatcherMock().stateChanged( change.stopped ) ;
      LOGGER.info( "*** Stopped, let cleanup happen ***" ) ;
    }
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( DownendStory.class ) ;

  private static final Command.Tag TAG_TR0 = new Command.Tag( TrackerCurator.TAG_PREFIX + 0 ) ;
  private static final Command.Tag TAG_TR1 = new Command.Tag( TrackerCurator.TAG_PREFIX + 1 ) ;


  private static Tracker extractTracker( final Tracker enhanced ) {
    try {
      final Class enhancedTrackerClass = Class.forName(
          TrackerCurator.class.getName() + "$TrackerEnhancer" ) ;
      final Field privateTracker = enhancedTrackerClass.getDeclaredField( "tracker" ) ;
      privateTracker.setAccessible( true ) ;
      return ( Tracker ) privateTracker.get( enhanced );
    } catch( ClassNotFoundException | NoSuchFieldException | IllegalAccessException e ) {
      throw new RuntimeException( e );
    }
  }
}
