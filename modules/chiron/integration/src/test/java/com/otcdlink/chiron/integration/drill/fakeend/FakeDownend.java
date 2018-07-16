package com.otcdlink.chiron.integration.drill.fakeend;

import com.otcdlink.chiron.toolbox.netty.NettySocketClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class FakeDownend extends NettySocketClient {

  private static final Logger LOGGER = LoggerFactory.getLogger( FakeDownend.class ) ;

  private final InetSocketAddress remoteAddress ;
  public final DownendDuplexPack downendDuplexPack;
  private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler ;

  private Channel channel ;

  public FakeDownend(
      final InetSocketAddress remoteAddress,
      final URI uri,
      final Thread.UncaughtExceptionHandler uncaughtExceptionHandler
  ) {
    this.remoteAddress = checkNotNull( remoteAddress ) ;
    checkNotNull( uri.getHost(), "Null host in " + uri + ", full URI needed" ) ;
    webSocketClientHandler = new WebSocketClientHandler(
        WebSocketClientHandshakerFactory.newHandshaker(
            checkNotNull( uri ), WebSocketVersion.V13, null, false, new DefaultHttpHeaders() ) ) ;
    downendDuplexPack = new DownendDuplexPack( this::writeToChannel ) ;
    this.uncaughtExceptionHandler = checkNotNull( uncaughtExceptionHandler ) ;
  }

  private ChannelFuture writeToChannel( Object message ) {
    return channel.writeAndFlush( message ) ;
  }

  private final WebSocketClientHandler webSocketClientHandler ;

  public void connect() {
    LOGGER.debug( "Connecting to " + remoteAddress + " ..." );
    channel = connect(
        remoteAddress,
        new ChannelInitializer< Channel >() {
          @Override
          protected void initChannel( final Channel channel ) {
            final ChannelPipeline channelPipeline = channel.pipeline() ;
            channelPipeline.addLast(
                new HttpClientCodec(),
                new HttpObjectAggregator( 8192 ),
                webSocketClientHandler
            ) ;
          }
        }
    ).syncUninterruptibly().channel() ;

    webSocketClientHandler.handshakeFuture().syncUninterruptibly() ;
  }

  @Override
  protected void customStop() {
    if( channel != null ) {
      channel.close().syncUninterruptibly() ;
    }
    channel = null ;
    downendDuplexPack.shutdown() ;
  }

  private class WebSocketClientHandler extends SimpleChannelInboundHandler< Object > {

    private final Semaphore pongReceivedSemaphore = new Semaphore( 0 ) ;
    private final WebSocketClientHandshaker handshaker ;
    private ChannelPromise handshakeFuture = null ;

    public WebSocketClientHandler( final WebSocketClientHandshaker handshaker ) {
      this.handshaker = handshaker ;
    }

    public ChannelFuture handshakeFuture() {
      return handshakeFuture ;
    }

    @Override
    public void handlerAdded( final ChannelHandlerContext channelHandlerContext ) {
      handshakeFuture = channelHandlerContext.newPromise() ;
    }

    @Override
    public void channelActive( final ChannelHandlerContext channelHandlerContext ) {
      handshaker.handshake( channelHandlerContext.channel() ) ;
    }

    @Override
    public void channelInactive( final ChannelHandlerContext channelHandlerContext ) {
      LOGGER.info( "WebSocket Client disconnected." ) ;
    }

    @Override
    public void channelRead0(
        final ChannelHandlerContext channelHandlerContext,
        final Object message
    ) {
      final Channel channel = channelHandlerContext.channel() ;
      checkState( FakeDownend.this.channel == channel,
          "Got " + channel + " instead of " + FakeDownend.this.channel ) ;
      if( ! handshaker.isHandshakeComplete() ) {
        handshaker.finishHandshake( channel, ( FullHttpResponse ) message ) ;
        LOGGER.info( "WebSocket Client connected." ) ;
        handshakeFuture.setSuccess() ;
        return ;
      }

      if( message instanceof FullHttpResponse ) {
        final FullHttpResponse response = ( FullHttpResponse ) message ;
        throw new IllegalStateException(
            "Unexpected FullHttpResponse (getStatus=" + response.status() +
                ", content=" + response.content().toString( CharsetUtil.UTF_8 ) + ')' ) ;
      }

      final WebSocketFrame frame = ( WebSocketFrame ) message ;
      if( frame instanceof TextWebSocketFrame ) {
        final TextWebSocketFrame textFrame = ( TextWebSocketFrame ) frame ;
        LOGGER.info( "WebSocket Client received message: " + textFrame.text() ) ;
        downendDuplexPack.textWebSocketFrameDuplex.receive(
            channelHandlerContext, textFrame ) ;
      } else if( frame instanceof PongWebSocketFrame ) {
        LOGGER.info( "WebSocket Client received pong" ) ;
        final PongWebSocketFrame pongWebSocketFrame = ( PongWebSocketFrame ) frame ;
        downendDuplexPack.pingPongWebSocketFrameDuplex
            .receive( channelHandlerContext, pongWebSocketFrame ) ;
      } else if( frame instanceof CloseWebSocketFrame ) {
        LOGGER.info( "WebSocket Client received closing" ) ;
        final CloseWebSocketFrame closeWebSocketFrame = ( CloseWebSocketFrame ) frame ;
        downendDuplexPack.closeWebSocketFrameDuplex.receive(
            channelHandlerContext, closeWebSocketFrame ) ;
        channel.close() ;
      }
    }

    @Override
    public void exceptionCaught(
        final ChannelHandlerContext channelHandlerContext,
        final Throwable cause
    ) {
      LOGGER.error( "Caught: ", cause ) ;
      if( ! handshakeFuture.isDone() ) {
        handshakeFuture.setFailure( cause ) ;
      }
      channelHandlerContext.close() ;
      uncaughtExceptionHandler.uncaughtException( Thread.currentThread(), cause ) ;
    }

  }


}
