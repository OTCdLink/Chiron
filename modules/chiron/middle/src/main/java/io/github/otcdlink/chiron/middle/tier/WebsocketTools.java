package io.github.otcdlink.chiron.middle.tier;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import static com.google.common.base.Preconditions.checkNotNull;

public final class WebsocketTools {

  private WebsocketTools() { }

  public static WebSocketFrame replaceContent(
      final WebSocketFrame webSocketFrame,
      final ByteBuf content
  ) {
    final WebSocketFrame augmentedWebsocketFrame = reconstruct( webSocketFrame, content ) ;
    return augmentedWebsocketFrame ;
  }

  public static WebSocketFrame prepend(
      final ByteBuf prepended,
      final WebSocketFrame webSocketFrame
  ) {
    checkNotNull( prepended ) ;
    final ByteBuf bodyBuffer = webSocketFrame.content() ;
    final CompositeByteBuf aggregatingBuffer = webSocketFrame.content().alloc().compositeBuffer() ;
    aggregatingBuffer.addComponent( prepended ) ;
    aggregatingBuffer.addComponent( bodyBuffer ) ;

    final WebSocketFrame augmentedWebsocketFrame =
        reconstruct( webSocketFrame, aggregatingBuffer ) ;

    return augmentedWebsocketFrame ;
  }


  private static WebSocketFrame reconstruct(
      final WebSocketFrame webSocketFrame,
      final ByteBuf newContent
  ) {
    return reconstruct( webSocketFrame.isFinalFragment(), webSocketFrame, newContent ) ;
  }

  public static WebSocketFrame reconstruct(
      final boolean finalFragment,
      final WebSocketFrame webSocketFrame,
      final ByteBuf newContent
  ) {
    return constructorFor( webSocketFrame )
        .create( finalFragment, webSocketFrame.rsv(), newContent ) ;
  }

  private static WebsocketFrameConstructor constructorFor( final WebSocketFrame webSocketFrame ) {
    final WebsocketFrameConstructor websocketFrameConstructor =
        WEBSOCKETFRAME_CONSTRUCTORS.get( webSocketFrame.getClass() ) ;
    if( websocketFrameConstructor == null ) {
      throw new IllegalArgumentException( "No constructor for " + webSocketFrame ) ;
    }
    return websocketFrameConstructor ;
  }

  private static final ImmutableMap<
      Class< ? extends WebSocketFrame >,
      WebsocketFrameConstructor
  > WEBSOCKETFRAME_CONSTRUCTORS = new ImmutableMap.Builder<
      Class< ? extends WebSocketFrame >,
      WebsocketFrameConstructor
  >()
      .put( TextWebSocketFrame.class, TextWebSocketFrame::new )
      .put( BinaryWebSocketFrame.class, BinaryWebSocketFrame::new )
      .put( ContinuationWebSocketFrame.class, ContinuationWebSocketFrame::new )
      .put( PingWebSocketFrame.class, PingWebSocketFrame::new )
      .put( PongWebSocketFrame.class, PongWebSocketFrame::new )
      /** There is no such constructor for {@link CloseWebSocketFrame}. */
      .build()
  ;

  private interface WebsocketFrameConstructor {
    WebSocketFrame create( final boolean finalFragment, final int rsv, final ByteBuf byteBuf ) ;
  }

  /**
   * Specification says that client should mask them, but this messes up the ASCII representation
   * of a TCP capture. Anyways we don't need masking in production thanks to HTTPS.
   * Such a representation is useful for grep'ing on files too big for Wireshark.
   * Something breaks Maven tests when the value is {@code false}.
   */
  public static final boolean MASK_WEBSOCKET_FRAMES_FROM_CLIENT = true ;
}
