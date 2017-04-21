package io.github.otcdlink.chiron.middle.tier;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebsocketFragmenterTierTest {

  @Test
  public void noFragment() throws Exception {
    final EmbeddedChannel channel = createEmbeddedChannel( 10 ) ;
    channel.writeOutbound( new TextWebSocketFrame( byteBuf( "Hello" ) ) ) ;

    verifyTextFrame( channel.< TextWebSocketFrame >readOutbound(), true, 0, "Hello" ) ;

    checkNoMoreOutboundContent( channel ) ;
  }

  /**
   * Testing with 3 fragments, there was a bug where 2nd fragment was always marked final.
   */
  @Test
  public void fragment() throws Exception {
    final EmbeddedChannel channel = createEmbeddedChannel( 2 ) ;
    channel.writeOutbound( new TextWebSocketFrame( byteBuf( "Hello" ) ) ) ;

    final TextWebSocketFrame firstFrame = channel.readOutbound() ;
    verifyTextFrame( firstFrame, false, 0, "He" ) ;

    final WebSocketFrame secondFrame = channel.readOutbound() ;
    verifyContinuationFrame( secondFrame, false, 0, "ll" ) ;

    final WebSocketFrame thirdAndFinalFrame = channel.readOutbound() ;
    verifyContinuationFrame( thirdAndFinalFrame, true, 0, "o" ) ;

    checkNoMoreOutboundContent( channel ) ;

    channel.writeInbound( firstFrame, secondFrame, thirdAndFinalFrame ) ;
    verifyTextFrame( channel.< TextWebSocketFrame >readInbound(), true, 0, "Hello" ) ;
  }


  @Test
  public void passthrough() throws Exception {
    final EmbeddedChannel channel = createEmbeddedChannel( 3 ) ;
    final CloseWebSocketFrame frameWritten = new CloseWebSocketFrame( 1000, "OK" ) ;
    channel.writeOutbound( frameWritten ) ;

    final CloseWebSocketFrame frameRead = channel.readOutbound() ;
    assertThat( frameRead ).isSameAs( frameWritten ) ;

    checkNoMoreOutboundContent( channel ) ;
  }


// =======
// Fixture
// =======

  private static EmbeddedChannel createEmbeddedChannel(
      final int maximumWebsocketPayloadLength
  ) {
    final int maximumWebsocketLength =
        WebsocketFrameSizer.frameSizeInt( maximumWebsocketPayloadLength ) ;

    return new EmbeddedChannel(
        new WebsocketFragmenterTier( maximumWebsocketLength ),
        new WebSocketFrameAggregator( 10 )
    ) ;
  }

  private static void checkNoMoreOutboundContent( final EmbeddedChannel channel ) {
    assertThat( ( Object ) channel.readOutbound() ).isNull() ;
  }

  private static void verifyContinuationFrame(
      final WebSocketFrame webSocketFrame,
      final boolean finalFragment,
      final int rsv,
      final String textContent
  ) {
    verify( webSocketFrame, ContinuationWebSocketFrame.class, finalFragment, rsv, textContent ) ;
  }

  private static void verifyTextFrame(
      final WebSocketFrame webSocketFrame,
      final boolean finalFragment,
      final int rsv,
      final String textContent
  ) {
    verify( webSocketFrame, TextWebSocketFrame.class, finalFragment, rsv, textContent ) ;
  }

  private static void verify(
      final WebSocketFrame webSocketFrame,
      final Class< ? extends WebSocketFrame > webSocketFrameClass,
      final boolean finalFragment,
      final int rsv,
      final String textContent
  ) {
    assertThat( webSocketFrame ).isInstanceOf( webSocketFrameClass ) ;
    assertThat( webSocketFrame.isFinalFragment() ).isEqualTo( finalFragment ) ;
    assertThat( webSocketFrame.rsv() ).isEqualTo( rsv ) ;
    assertThat( string( webSocketFrame ) ).isEqualTo( textContent ) ;
  }


  private static ByteBuf byteBuf( final String string ) {
    final ByteBuf byteBuf = Unpooled.copiedBuffer( string.getBytes( Charsets.US_ASCII ) ) ;

    /** {@link EmbeddedChannel} automatically releases. */
    byteBuf.retain() ;

    return byteBuf ;
  }

  private static String string( final WebSocketFrame webSocketFrame ) {
    return webSocketFrame.content().toString( Charsets.US_ASCII ) ;
  }


}