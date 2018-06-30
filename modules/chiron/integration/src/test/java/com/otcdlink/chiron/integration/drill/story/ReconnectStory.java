package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.upend.session.SessionSupervisor;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.otcdlink.chiron.integration.drill.ConnectorDrill.SESSION_IDENTIFIER;
import static com.otcdlink.chiron.mockster.Mockster.any;
import static com.otcdlink.chiron.mockster.Mockster.exactly;
import static com.otcdlink.chiron.mockster.Mockster.withCapture;

public class ReconnectStory {

  @Test
  public void downendConnectorResignonWithHttpProxy() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forDownendConnector().done()
        .forUpendConnector().done()
        .withProxy( true )
        .withTimeBoundary( TimeBoundary.newBuilder()
            .pingIntervalNever()
            .pongTimeoutNever()
            .reconnectDelay( 10, 10 )
            .pingTimeoutNever()
            .maximumSessionInactivity( 10_000 )
            .build()
        )
        .build()
    ) {
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant =
          drill.forSimpleDownend().changesAsConstant() ;

      drill.restartHttpProxy() ;

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

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( ReconnectStory.class ) ;


}
