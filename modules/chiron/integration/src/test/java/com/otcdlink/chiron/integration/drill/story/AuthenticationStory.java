package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.fixture.NettyLeakDetectorExtension;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith( NettyLeakDetectorExtension.class )
class AuthenticationStory {

  @Test
  void downendConnectorSignonAndEcho() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
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
  void downendConnectorSignonAndEchoWithProxy() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withProxy( true )
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
  void downendConnector2FaSignonAndEcho() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
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
  void commandTransceiverPrimarySignon() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forCommandTransceiver()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
            .done()
        .build()
    ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }



// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( AuthenticationStory.class ) ;



}
