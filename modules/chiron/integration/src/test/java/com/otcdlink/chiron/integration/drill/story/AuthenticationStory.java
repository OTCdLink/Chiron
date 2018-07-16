package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.fixture.NettyLeakDetectorRule;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationStory {

  @Test
  public void downendConnectorSignonAndEcho() throws Exception {
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
  public void downendConnectorSignonAndEchoWithProxy() throws Exception {
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
  public void downendConnector2FaSignonAndEcho() throws Exception {
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
  public void commandTransceiverPrimarySignon() throws Exception {
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

  @Rule
  public final NettyLeakDetectorRule nettyLeakDetectorRule = new NettyLeakDetectorRule() ;


}
