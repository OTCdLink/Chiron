package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class AuthenticationStory {

  @Test
  public void downendConnectorSignonAndEcho() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )  // Good for debugging.
        .forDownendConnector()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
            .done()
        .build()
    ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  public void downendConnectorSignonAndEchoWithProxy() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withProxy( true )
        .withMocksterTimeout( 1, TimeUnit.HOURS )  // Good for debugging.
        .forDownendConnector()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
            .done()
        .build()
    ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  public void downendConnector2FaSignonAndEcho() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )  // Good for debugging.
        .forDownendConnector()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.TWO_FACTOR )
            .done()
        .build()
    ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  public void commandTransceiverPrimarySignon() throws Exception {
    LOGGER.info( "*** Test starts here ***" ) ;
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forCommandTransceiver()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
            .done()
        .build()
    ) {
      LOGGER.info( "*** Let " + drill + "perform start and stop ***" ) ;
    }
  }



// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( AuthenticationStory.class ) ;

  private static final Command.Tag TAG_T0 = new Command.Tag( "T0" ) ;


}
