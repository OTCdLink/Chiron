package io.github.otcdlink.chiron.middle.tier;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.internal.RecyclableArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Breaks down a {@link WebSocketFrame} into fragments.
 * Compatible with {@link io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator}.
 */
public class WebsocketFragmenterTier extends ChannelOutboundHandlerAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger( WebsocketFragmenterTier.class ) ;

  private final int maximumContentLength ;
  private final int maximumPayloadLength ;

  public WebsocketFragmenterTier( final int maximumWebsocketLength ) {
    checkArgument( maximumWebsocketLength > 0 ) ;
    this.maximumContentLength = maximumWebsocketLength ;
    this.maximumPayloadLength = WebsocketFrameSizer.payloadSizeInt( maximumWebsocketLength ) ;
  }


  @Override
  public void write(
      final ChannelHandlerContext channelHandlerContext,
      final Object outbound,
      final ChannelPromise promise
  ) throws Exception {
    checkState( maximumContentLength > 0, "Not initialized" ) ;
    if( outbound instanceof TextWebSocketFrame || outbound instanceof BinaryWebSocketFrame ) {
      final WebSocketFrame webSocketFrame = ( WebSocketFrame ) outbound ;
      if( webSocketFrame.content().readableBytes() > maximumPayloadLength ) {
        writeFragmented( channelHandlerContext, webSocketFrame,
            maximumPayloadLength, promise ) ;
        return ;
      }
    } else if( outbound instanceof ContinuationWebSocketFrame ) {
      throw new IllegalArgumentException( "Unexpected: " + outbound ) ;
    }

    /** Process all {@link WebSocketFrame}s which are not content frames to fragment,
     * and other stuff as well. */
    channelHandlerContext.write( outbound, promise ) ;
  }

  private static void writeFragmented(
      final ChannelHandlerContext channelHandlerContext,
      final WebSocketFrame webSocketFrame,
      final int maximumPayloadLength,
      final ChannelPromise promise
  ) {
    webSocketFrame.retain() ;
    final RecyclableArrayList out = RecyclableArrayList.newInstance() ;
    try {
      long totalRead = 0 ;
      long totalWritten = 0 ;
      long fragmentCount = 0 ;
      while( true ) {
        final int readableBytes = webSocketFrame.content().readableBytes() ;
        if( readableBytes > 0 ) {
          final boolean first = totalRead == 0 ;
          final int sliceLength = Math.min( readableBytes, maximumPayloadLength ) ;
          final ByteBuf slice = webSocketFrame.content().readSlice( sliceLength ) ;
          totalRead += sliceLength ;
          if( first ) {
            final WebSocketFrame firstFrame =
                WebsocketTools.reconstruct( false, webSocketFrame, slice ) ;
            firstFrame.retain() ;
            out.add( firstFrame ) ;
          } else {
            final boolean finalFrame = sliceLength == readableBytes ;
            final WebSocketFrame continuationFrame = new ContinuationWebSocketFrame(
                finalFrame, webSocketFrame.rsv(), slice ) ;
            continuationFrame.retain() ;
            out.add( continuationFrame ) ;
          }
          totalWritten = totalWritten + WebsocketFrameSizer.frameSize( sliceLength ) ;
          fragmentCount ++ ;
        } else {
          break ;
        }
      }
      LOGGER.debug(
          "Fragmented a total of " + totalRead + " bytes " +
          "into " + fragmentCount + " Frames (" + totalWritten + " bytes including headers)" +
          ") for " + webSocketFrame + "."
      ) ;
    } finally {
      final int sizeMinusOne = out.size() - 1 ;
      for( int i = 0 ; i <= sizeMinusOne ; i++ ) {
        // TODO: check if this is the correct approach for handling the Promise.
        // TODO: chain Promises instead of calling writes sequentially.
        if( i < sizeMinusOne ) {
          channelHandlerContext.write( out.get( i ), channelHandlerContext.voidPromise() ) ;
        } else {
          channelHandlerContext.writeAndFlush( out.get( i ), promise ) ;
          promise.addListener( future -> webSocketFrame.release() ) ;
        }
      }
      out.recycle() ;
    }
  }


}
