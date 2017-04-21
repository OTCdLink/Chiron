package io.github.otcdlink.chiron.fixture.httpproxy;

import com.google.common.base.Charsets;
import io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ForwardingRoute;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Edge.INITIATOR;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Edge.TARGET;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ChannelHandlerName.INITIAL_CONNECT_TO_PROXY;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ChannelHandlerName.INITIAL_HTTP_AGGREGATOR;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ChannelHandlerName.INITIAL_HTTP_CODEC;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ChannelHandlerName.INITIATOR_SIDE_IN;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ChannelHandlerName.INITIATOR_SIDE_OUT;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ChannelHandlerName.INITIATOR_STATE_SUPERVISION;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ChannelHandlerName.TARGET_SIDE_IN;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ChannelHandlerName.TARGET_SIDE_OUT;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.ChannelHandlerName.TARGET_STATE_SUPERVISION;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.addLoggingHandlerFirst;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxyTools.addLoggingHandlerLast;
import static io.netty.channel.ChannelFutureListener.CLOSE;

/**
 * Establishes the connection with the Target, then creates a {@link Junction}.
 * The {@link Junctor} sends an "OK" response before upgrading the {@link ChannelPipeline}s.
 */
class Junctor {

  private final Logger logger ;

  /**
   * May differ from {@link Channel#localAddress()} as set in {@link #proxyListenAddress}?
   */

  private final InetSocketAddress proxyListenAddress ;

  private final Channel initiatorChannel ;
  private final HttpProxy.Watcher watcher ;
   private final HttpProxy.PipelineConfigurator.Factory pipelineConfiguratorFactory ;
  private final int connectTimeoutMs ;

  Junctor(
      final Logger logger,
      final InetSocketAddress proxyListenAddress,
      final Channel initiatorChannel,
      final HttpProxy.Watcher watcher,
       final HttpProxy.PipelineConfigurator.Factory pipelineConfiguratorFactory,
      final int connectTimeoutMs
  ) {
    this.logger = checkNotNull( logger ) ;
    this.proxyListenAddress = checkNotNull( proxyListenAddress ) ;
    this.initiatorChannel = checkNotNull( initiatorChannel ) ;
    this.watcher = checkNotNull( watcher ) ;
     this.pipelineConfiguratorFactory = checkNotNull( pipelineConfiguratorFactory ) ;
    checkArgument( connectTimeoutMs >= 0 ) ;
    this.connectTimeoutMs = connectTimeoutMs ;
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
                new HttpProxyTools.UnsupportedMessageException( message ) ) ;
          }
        }
    ) ;
    this.initiatorChannel.read() ;

    return junctionFuture ;
  }

  private void connectToTarget(
      final ChannelHandlerContext initiatorContext,
      final InetSocketAddress targetAddress,
      final CompletableFuture< Junction > junctionFuture
  ) {
    final EventLoopGroup eventLoopGroup = ( EventLoopGroup ) initiatorContext.executor() ;
    final Bootstrap bootstrap = new Bootstrap()
        .group( eventLoopGroup )
        .channel( NioSocketChannel.class )
        .option( ChannelOption.AUTO_READ, false )
        .option( ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs )
        .handler( HttpProxyTools.VOID_CHANNEL_INITIALIZER )  // Netty angry if no handler.
        .remoteAddress( targetAddress )
    ;

    final ChannelFuture connectFuture = bootstrap.connect() ;
    connectFuture.addListener( ( ChannelFutureListener ) future -> {
      if( future.cause() == null ) {
        final Channel targetChannel = future.channel() ;

        final Junction junction = createJunction( logger, targetChannel ) ;

        logger.debug( "Created " + junction + " after successful connection." ) ;
        // ChannelTools.dumpPipeline( initiatorContext.pipeline() ) ;
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
      } else {
        final HttpProxyTools.ConnectionFailedException connectionFailedException =
            new HttpProxyTools.ConnectionFailedException( targetAddress, future.cause() ) ;
        junctionFuture.completeExceptionally( connectionFailedException ) ;
        // throw connectionFailedException ;
      }
    } ) ;

  }

  private Junction createJunction( final Logger logger, final Channel targetChannel ) {
    final ForwardingRoute forwardingRoute = new ForwardingRoute(
        ( InetSocketAddress ) this.initiatorChannel.remoteAddress(),
        proxyListenAddress,
        ( InetSocketAddress ) targetChannel.localAddress(),
        ( InetSocketAddress ) targetChannel.remoteAddress()
    ) ;

    return new Junction(
        logger,
        forwardingRoute,
        this.initiatorChannel,
        targetChannel,
        this.watcher
    ) ;
  }

  private void configureJunction( final Channel targetChannel, final Junction junction ) {

    final HttpProxy.PipelineConfigurator pipelineConfigurator =
        pipelineConfiguratorFactory.createNew( logger ) ;

    junction.configure( INITIATOR, pipelineConfigurator ) ;

    final boolean bytesInDetail = this.watcher.bytesInDetail() ;
    junction.addIngressConfiguration(
        initiatorChannel.pipeline(), INITIATOR, bytesInDetail ) ;

    junction.addEgressConfiguration( this.initiatorChannel.pipeline(), INITIATOR, bytesInDetail ) ;

    initiatorChannel.pipeline().addLast(
        INITIATOR_STATE_SUPERVISION.handlerName(),
        new ChannelDuplexHandler() {
          @Override
          public void channelInactive(
              final ChannelHandlerContext channelHandlerContext
          ) throws Exception {
            junction.disconnect() ;
            super.channelInactive( channelHandlerContext ) ;
          }

          @Override
          public void exceptionCaught(
              final ChannelHandlerContext channelHandlerContext,
              final Throwable cause
          ) throws Exception {
            logger.error( "Exception caught in " + channelHandlerContext + ".", cause ) ;
          }
        }
    ) ;

    /** Logging for {@link #INITIATOR_SIDE_OUT} already set in {@link ConnectProxy#start()}
     * but no longer at at the right place because of other additions, so we clean it up.*/
    HttpProxyTools.removeAllLoggingHandlers( initiatorChannel.pipeline() ) ;

    if( ConnectProxy.DUMP_PAYLOADS ) {
      addLoggingHandlerFirst( initiatorChannel.pipeline(), INITIATOR_SIDE_OUT.handlerName() ) ;
      addLoggingHandlerLast( initiatorChannel.pipeline(), INITIATOR_SIDE_IN.handlerName() ) ;
    }


    logger.debug( "Configured for " + INITIATOR + ": " + this.initiatorChannel.pipeline() + "." ) ;


    junction.configure( TARGET, pipelineConfigurator ) ;

    junction.addIngressConfiguration(
        targetChannel.pipeline(), TARGET, bytesInDetail ) ;

    junction.addEgressConfiguration( targetChannel.pipeline(), TARGET, bytesInDetail ) ;

    targetChannel.pipeline().addLast(
        TARGET_STATE_SUPERVISION.handlerName(),
        new ChannelDuplexHandler() {
          @Override
          public void channelInactive( final ChannelHandlerContext channelHandlerContext )
              throws Exception
          {
            junction.disconnect() ;
            super.channelInactive( channelHandlerContext ) ;
          }
        }
    ) ;

    if( ConnectProxy.DUMP_PAYLOADS ) {
      addLoggingHandlerFirst( targetChannel.pipeline(), TARGET_SIDE_IN.handlerName() ) ;
      addLoggingHandlerLast( targetChannel.pipeline(), TARGET_SIDE_OUT.handlerName() ) ;
    }

    logger.debug( "Configured for " + TARGET + ": " + targetChannel.pipeline() ) ;
  }


  private static DefaultFullHttpResponse createHttpResponseMethodNotAllowed() {
    final ByteBuf html = Unpooled.wrappedBuffer( (
        "<html><body>" +
        "This is an HTTP proxy supporting only CONNECT method from local addresses." +
        "</body></html>"
    ).getBytes( Charsets.US_ASCII ) ) ;
    return new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.METHOD_NOT_ALLOWED,
        html
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

  private static final String HOSTNAME ;

  static {
    String hostName ;
    try {
      hostName = InetAddress.getLocalHost().getHostName() ;
    } catch( final UnknownHostException e ) {
      hostName = "localhost" ;
    }
    HOSTNAME = hostName ;
  }


  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + "{" + proxyListenAddress + "}" ;
  }


  private static InetSocketAddress inetSocketAddress( final String host ) {
    final int colonIndex = host.indexOf( ':' ) ;
    if( colonIndex > 0 ) {
      final int port = Integer.parseInt( host.substring( colonIndex + 1 ) ) ;
      return new InetSocketAddress( host.substring( 0, colonIndex ), port ) ;
    } else {
      /** Using default HTTP port because we don't plan to support HTTPS in {@link ConnectProxy}. */
      return new InetSocketAddress( host, 80 ) ;
    }

  }

}
