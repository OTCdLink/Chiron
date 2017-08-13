package com.otcdlink.chiron.fixture.tcp.http;

import com.otcdlink.chiron.fixture.tcp.Junction;
import com.otcdlink.chiron.fixture.tcp.Junctor;
import com.otcdlink.chiron.fixture.tcp.TcpTransitTools;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static com.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.INITIAL_CONNECT_TO_PROXY;
import static com.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.INITIAL_HTTP_AGGREGATOR;
import static com.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.INITIAL_HTTP_CODEC;
import static io.netty.channel.ChannelFutureListener.CLOSE;

/**
 * Establishes the connection with the Target, then creates a {@link Junction}.
 * The {@link HttpAwareJunctor} sends an "OK" response before upgrading the {@link ChannelPipeline}s.
 */
class HttpAwareJunctor extends Junctor {

  HttpAwareJunctor(
      final Logger logger,
      final InetSocketAddress proxyListenAddress,
      final Channel initiatorChannel,
      final HttpProxy.Watcher watcher,
       final HttpProxy.PipelineConfigurator.Factory pipelineConfiguratorFactory,
      final int connectTimeoutMs
  ) {
    super(
        logger,
        proxyListenAddress,
        initiatorChannel,
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
  }


  public CompletableFuture< Junction > initializeInitiatorChannel() {
    final CompletableFuture< Junction > junctionFuture = new CompletableFuture<>() ;
    this.initiatorChannel.pipeline().addLast(
        INITIAL_HTTP_CODEC.handlerName(), new HttpServerCodec() ) ;
    this.initiatorChannel.pipeline().addLast(
        INITIAL_HTTP_AGGREGATOR.handlerName(), new HttpObjectAggregator( 1024 * 1024 ) ) ;
    this.initiatorChannel.pipeline().addLast(
        INITIAL_CONNECT_TO_PROXY.handlerName(),
        new ChannelDuplexHandler() {
          @Override
          public void channelRead(
              final ChannelHandlerContext initiatorContext,
              final Object message
          ) throws Exception {
            try {
              if( message instanceof FullHttpRequest ) {
                final FullHttpRequest fullHttpRequest = ( FullHttpRequest ) message ;
                if( fullHttpRequest.method().equals( HttpMethod.CONNECT ) &&
                    NettyTools.remoteAddressIsLocal( initiatorContext.channel() )
                ) {
                  final String host = fullHttpRequest.headers().get( HttpHeaderNames.HOST ) ;
                  // It could make sense to check other headers:
                  //   connection: keep-alive
                  //   proxy-connection: keep-alive
                  connectToTarget( initiatorContext, inetSocketAddress( host ), junctionFuture ) ;
                  return ;
                }
              }
              final DefaultFullHttpResponse httpResponse = createHttpResponseMethodNotAllowed() ;
              initiatorContext.channel().writeAndFlush( httpResponse ).addListener( CLOSE ) ;
              junctionFuture.completeExceptionally(
                  new TcpTransitTools.UnsupportedMessageException( message ) ) ;
            } finally {
              ReferenceCountUtil.release( message ) ;
            }
          }
        }
    ) ;
    this.initiatorChannel.read() ;
    return junctionFuture ;
  }

  @Override
  protected void junctionEstablished(
      final ChannelHandlerContext initiatorContext,
      final Channel targetChannel,
      final CompletableFuture< Junction > junctionFuture,
      final Junction junction
  ) {
    initiatorContext.writeAndFlush( createHttpResponseMethodConnectVia() ).addListener(
        ( ChannelFutureListener ) writeAndFlushFuture -> {
          initiatorContext.pipeline().remove( INITIAL_HTTP_CODEC.handlerName() ) ;
          initiatorContext.pipeline().remove( INITIAL_HTTP_AGGREGATOR.handlerName() ) ;
          initiatorContext.pipeline().remove( INITIAL_CONNECT_TO_PROXY.handlerName() ) ;
          configureJunction( targetChannel, junction ) ;
          logger.debug( "Sent HTTP response to initiator and configured " + junction + "." ) ;
          junctionFuture.complete( junction ) ;
        }
    ) ;
  }

  private DefaultFullHttpResponse createHttpResponseMethodConnectVia() {
    final DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        new HttpResponseStatus( 200, "Connection established" )
    ) ;
    httpResponse.headers()
        .add( HttpHeaderNames.VIA, "1.1 " + proxyListenAddress.getHostString() )
    ;
    return httpResponse ;
  }





}
