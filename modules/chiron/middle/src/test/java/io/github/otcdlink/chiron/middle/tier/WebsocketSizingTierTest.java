package io.github.otcdlink.chiron.middle.tier;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.junit.Ignore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore( "Doesn't work yet")
public class WebsocketSizingTierTest {

  @Test
  public void noFragment() throws Exception {
    final EmbeddedChannel channel = createEmbeddedChannel( 10 ) ;
    channel.writeOutbound( new TextWebSocketFrame( byteBuf( "Hello" ) ) ) ;

    verifyTextFrame( channel.< TextWebSocketFrame >readOutbound(), true, 0, "Hello" ) ;

    checkNoMoreOutboundContent( channel ) ;
  }

  @Test
  public void fragment() throws Exception {
    final EmbeddedChannel channel = createEmbeddedChannel( 3 ) ;
    channel.writeOutbound( new TextWebSocketFrame( byteBuf( "Hello" ) ) ) ;

    final TextWebSocketFrame firstFrame = channel.readOutbound() ;
    verifyTextFrame( firstFrame, false, 0, "Hel" ) ;

    final WebSocketFrame secondAndFinalFrame = channel.readOutbound() ;
    verifyContinuationFrame( secondAndFinalFrame, true, 0, "lo" ) ;

    checkNoMoreOutboundContent( channel ) ;

    channel.writeInbound( firstFrame, secondAndFinalFrame ) ;
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
      final int maximumWebsocketContentLength
  ) {
    return new EmbeddedChannel(
        new WebsocketFrameSizingTier( maximumWebsocketContentLength )
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