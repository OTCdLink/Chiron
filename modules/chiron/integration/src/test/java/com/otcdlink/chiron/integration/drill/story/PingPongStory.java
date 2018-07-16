package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.fixture.NettyLeakDetectorRule;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.junit.Rule;
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
      drill.forFakeDownend().duplex().texting().emitWithDutyCall()
          .requestEcho( SketchLibrary.TAG_TR0, "Hi" ) ;
      drill.forUpendConnector().upwardDutyMock().requestEcho( any(), exactly( "Hi" ) ) ;
      final PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame() ;
      pingWebSocketFrame.content().writeLong( 12345 ) ;
      drill.forFakeDownend().duplex().pinging().emit( pingWebSocketFrame ) ;
      drill.forFakeDownend().duplex().pinging().expect( pongWebSocketFrame ->
          assertThat( pongWebSocketFrame.content().readLong() ).isEqualTo( 12345 ) ) ;
    }
  }

  @Test
  public void pongTimeout() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withTimeBoundary( TimeBoundary.newBuilder()
            .pingIntervalNever()
            .pongTimeoutOnDownend( 10 )
            .reconnectImmediately()
            .pingTimeoutNever()
            .sessionInactivityForever()
            .build()
        )
        .forDownendConnector()
            .done()
        .fakeUpend()
            .withAuthentication( ConnectorDrill.Authentication.NONE )
            .done()
        .build()
    ) {
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant change =
          drill.forSimpleDownend().changeAsConstant() ;

      drill.processNextTaskCapture( ConnectorDrill.EXECUTE_PING ) ;
      drill.forFakeUpend().duplex().ponging().next() ;
      drill.processNextTaskCapture( ConnectorDrill.EXECUTE_PONG_TIMEOUT ) ;
      drill.processNextTaskCapture( ConnectorDrill.EXECUTE_RECONNECT ) ;
      drill.forSimpleDownend().changeWatcherMock().stateChanged( change.connecting ) ;
      drill.forSimpleDownend().changeWatcherMock().stateChanged( change.connected ) ;

      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }
  }




// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( PingPongStory.class ) ;

  @Rule
  public final NettyLeakDetectorRule nettyLeakDetectorRule = new NettyLeakDetectorRule() ;


}
