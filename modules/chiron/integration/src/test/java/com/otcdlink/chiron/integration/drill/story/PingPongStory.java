package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.otcdlink.chiron.mockster.Mockster.any;
import static com.otcdlink.chiron.mockster.Mockster.exactly;
import static org.assertj.core.api.Assertions.assertThat;

public class PingPongStory {

  @Test
  public void upendSendsPong() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )  // Good for debugging.
        .fakeDownend()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.NONE )
            .done()
        .build()
    ) {
      drill.forFakeDownend().halfDuplex().texting().emitWithDutyCall().requestEcho( TAG_T0, "Hi" ) ;
      drill.forUpendConnector().upwardDutyMock().requestEcho( any(), exactly( "Hi" ) ) ;
      final PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame() ;
      pingWebSocketFrame.content().writeLong( 12345 ) ;
      drill.forFakeDownend().halfDuplex().pinging().emit( pingWebSocketFrame ) ;
      drill.forFakeDownend().halfDuplex().pinging().expect( pongWebSocketFrame ->
          assertThat( pongWebSocketFrame.content().readLong() ).isEqualTo( 12345 ) ) ;
    }
  }




// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( PingPongStory.class ) ;

  private static final Command.Tag TAG_T0 = new Command.Tag( "T0" ) ;


}
