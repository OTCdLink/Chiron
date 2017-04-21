package io.github.otcdlink.chiron.upend;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.downend.DownendConnector;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import io.github.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import io.github.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;
import mockit.Injectable;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Semaphore;

/**
 * Test if {@link UpendConnector} did not forget to setup the
 * {@link io.github.otcdlink.chiron.upend.tier.PongTier}.
 * We use a custom WebSocket client here because:
 * <ul><li>
 *   {@link NettyHttpClient} is not supposed to support WebSockets.
 * </li><li>
 *   We don't want to hack {@link DownendConnector} to notify of a successful ping.
 * </li><li>
 *   We don't want to rely on a ping timeout which would increase test duration if we can
 *   do better.
 * </li></ul>
 */
@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class UpendConnectorPongTest {

  @Test( timeout = TIMEOUT )
  public void pong(
      @Injectable final CommandConsumer<
          Command< Designator, EchoUpwardDuty< Designator > >
      > commandConsumer
  ) throws Exception {
    fixture.initializeAndStart(
        () -> fixture.pingResponderSetup( commandConsumer, PING_TIMING )
    ) ;

    final EventLoopGroup eventLoopGroup = eventLoopGroup() ;

    final InetSocketAddress listenAddress = fixture.upendConnectorSetup().listenAddress ;
    final URI uri = fixture.upendConnectorSetup().websocketUrl.toURI() ;

    final WebSocketClientHandler webSocketClientHandler = new WebSocketClientHandler(
        WebSocketClientHandshakerFactory.newHandshaker(
            uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders() ) )
    ;

    final Bootstrap bootstrap = new Bootstrap() ;
    bootstrap.group( eventLoopGroup )
        .channel( NioSocketChannel.class )
        .remoteAddress( listenAddress )
        .handler( new ChannelInitializer< SocketChannel >() {
          @Override
          protected void initChannel( final SocketChannel socketChannel ) {
            final ChannelPipeline channelPipeline = socketChannel.pipeline() ;
            channelPipeline.addLast(
                new HttpClientCodec(),
                new HttpObjectAggregator( 8192 ),
                webSocketClientHandler
            ) ;
          }
        } )
    ;

    final Channel channel = bootstrap.connect( uri.getHost(), uri.getPort() ).sync().channel() ;
    webSocketClientHandler.handshakeFuture().sync() ;

    final Semaphore pingSentSemaphore = new Semaphore( 0 ) ;
    for( int i = 0 ; i < 3 ; i ++ ) {
      final PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame() ;
      final long counter = i ;
      pingWebSocketFrame.content().writeLong( counter ) ;
      channel.writeAndFlush( pingWebSocketFrame ).addListener( future -> {
        if( future.isSuccess() ) {
          LOGGER.info( "Wrote and flushed Ping[ " + counter + " ], now waiting for Pong ..." ) ;
          pingSentSemaphore.release() ;
        } else {
          LOGGER.error( "Error while writing/flushing.", future.cause() ) ;
        }
      } ) ;
      pingSentSemaphore.acquire() ;
      webSocketClientHandler.waitForPong() ;
      LOGGER.info( "Pong[ " + i + " ] received." ) ;
      // Uninterruptibles.sleepUninterruptibly( 1, TimeUnit.SECONDS ) ;
    }

  }



// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( UpendConnectorPongTest.class ) ;
  
  private final UpendConnectorFixture fixture = new UpendConnectorFixture() ;

  @After
  public void tearDown() throws Exception {
    fixture.stopAll() ;
  }

  private static class WebSocketClientHandler extends SimpleChannelInboundHandler< Object > {

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
    public void channelRead0( final ChannelHandlerContext ctx, final Object msg ) throws Exception {
      final Channel ch = ctx.channel() ;
      if( ! handshaker.isHandshakeComplete() ) {
        handshaker.finishHandshake( ch, ( FullHttpResponse ) msg ) ;
        LOGGER.info( "WebSocket Client connected." ) ;
        handshakeFuture.setSuccess() ;
        return ;
      }

      if( msg instanceof FullHttpResponse ) {
        final FullHttpResponse response = ( FullHttpResponse ) msg ;
        throw new IllegalStateException(
            "Unexpected FullHttpResponse (getStatus=" + response.status() +
                ", content=" + response.content().toString( CharsetUtil.UTF_8 ) + ')' ) ;
      }

      final WebSocketFrame frame = ( WebSocketFrame ) msg ;
      if( frame instanceof TextWebSocketFrame ) {
        final TextWebSocketFrame textFrame = ( TextWebSocketFrame ) frame ;
        LOGGER.info( "WebSocket Client received message: " + textFrame.text() ) ;
      } else if( frame instanceof PongWebSocketFrame ) {
        LOGGER.info( "WebSocket Client received pong" ) ;
        pongReceivedSemaphore.release() ;
      } else if( frame instanceof CloseWebSocketFrame ) {
        LOGGER.info( "WebSocket Client received closing" ) ;
        ch.close() ;
      }
    }

    @Override
    public void exceptionCaught(
        final ChannelHandlerContext channelHandlerContext,
        final Throwable cause
    ) {
      cause.printStackTrace() ;
      if( ! handshakeFuture.isDone() ) {
        handshakeFuture.setFailure( cause ) ;
      }
      channelHandlerContext.close() ;
    }

    public void waitForPong() throws InterruptedException {
      pongReceivedSemaphore.acquire() ;
    }
  }

  private static NioEventLoopGroup eventLoopGroup() {
    return new NioEventLoopGroup(
        2, ExecutorTools.newCountingDaemonThreadFactory( "TinyWebsocketClient" ) ) ;
  }

  private static final long TIMEOUT = 1000 ;
//  private static final long TIMEOUT = 1_000_000 ;

  private static final TimeBoundary.ForAll PING_TIMING = TimeBoundary.Builder.createNew()
      .pingIntervalNever()
      .pongTimeoutNever()
      .reconnectNever()
      .pingTimeoutNever()
      .sessionInactivityForever()
      .build()
  ;

}