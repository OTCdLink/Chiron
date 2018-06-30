package com.otcdlink.chiron.integration.drill;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.Test;

public class RealConnectorDrillTest {

  @Test
  public void fakeConversation() throws Exception {
    try( final ConnectorDrill connectorDrill = ConnectorDrill.newBuilder()
        .fakeUpend().done()
        .fakeDownend().done()
        .build()
    ) {
      final ConnectorDrill.DownendHalfDuplex downendHalfDuplex =
          connectorDrill.forFakeDownend().halfDuplex() ;
      final ConnectorDrill.UpendHalfDuplex upendHalfDuplex =
          connectorDrill.forFakeUpend().halfDuplex() ;

      downendHalfDuplex.texting().emit( new TextWebSocketFrame( "Boo" ) ) ;
      upendHalfDuplex.texting().assertThatNext().textIsEqualTo( "Boo" ) ;
      upendHalfDuplex.texting().emit( new TextWebSocketFrame( "Goo" ) ) ;
      downendHalfDuplex.texting().assertThatNext().textIsEqualTo( "Goo" ) ;
    }
  }
}