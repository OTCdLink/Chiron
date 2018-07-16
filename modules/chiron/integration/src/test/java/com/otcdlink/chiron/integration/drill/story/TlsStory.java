package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.fixture.NettyLeakDetectorRule;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.DrillBuilder;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TlsStory {

  @Test
  public void downendConnectorWithTls() throws Exception {
    try( final ConnectorDrill drill = BUILDER_WITH_DOWNEND_CONNECTOR.build() ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  public void downendConnectorWithTlsAndProxy() throws Exception {
    try( final ConnectorDrill drill = BUILDER_WITH_DOWNEND_CONNECTOR.withProxy( true ).build() ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  public void commandTransceiverWithTls() throws Exception {
    try( final ConnectorDrill drill = BUILDER_WITH_COMMAND_TRANSCEIVER.build() ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }

  @Test
  public void commandTransceiverWithTlsAndProxy() throws Exception {
    try( final ConnectorDrill drill = BUILDER_WITH_COMMAND_TRANSCEIVER.withProxy( true ).build() ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }







// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( TlsStory.class ) ;

  private static final Command.Tag TAG_T0 = new Command.Tag( "T0" ) ;

  private static final DrillBuilder BUILDER_WITH_DOWNEND_CONNECTOR = ConnectorDrill.newBuilder()
      .withTls( true )
      .forDownendConnector()
      .done()
      .forUpendConnector()
      .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
      .done()
  ;

  private static final DrillBuilder BUILDER_WITH_COMMAND_TRANSCEIVER = ConnectorDrill.newBuilder()
      .withTls( true )
      .forCommandTransceiver()
      .done()
      .forUpendConnector()
      .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
      .done()
  ;

  @Rule
  public final NettyLeakDetectorRule nettyLeakDetectorRule = new NettyLeakDetectorRule() ;

}
