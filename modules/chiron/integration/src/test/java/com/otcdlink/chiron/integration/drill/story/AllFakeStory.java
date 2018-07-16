package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.Test;

public class AllFakeStory {

  @Test
  public void fakeConversation() throws Exception {
    try( final ConnectorDrill connectorDrill = ConnectorDrill.newBuilder()
        .fakeUpend().done()
        .fakeDownend().done()
        .build()
    ) {
      final ConnectorDrill.DownendDuplex downendDuplex =
          connectorDrill.forFakeDownend().duplex() ;
      final ConnectorDrill.UpendDuplex upendDuplex =
          connectorDrill.forFakeUpend().duplex() ;

      downendDuplex.texting().emit( new TextWebSocketFrame( "Boo" ) ) ;
      upendDuplex.texting().assertThatNext().textIsEqualTo( "Boo" ) ;
      upendDuplex.texting().emit( new TextWebSocketFrame( "Goo" ) ) ;
      downendDuplex.texting().assertThatNext().textIsEqualTo( "Goo" ) ;
    }
  }
}