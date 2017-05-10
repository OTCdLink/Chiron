package io.github.otcdlink.chiron.fixture.tcp;

import com.google.common.base.Charsets;
import io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ForwardingRoute;
import io.github.otcdlink.chiron.fixture.tcp.http.ConnectProxy;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.INITIATOR_SIDE_IN;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.INITIATOR_SIDE_OUT;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.INITIATOR_STATE_SUPERVISION;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.TARGET_SIDE_IN;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.TARGET_SIDE_OUT;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.TARGET_STATE_SUPERVISION;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.addLoggingHandlerFirst;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.addLoggingHandlerLast;

/**
 * Establishes the connection with the Target, then creates a {@link Junction}.
 * The {@link Junctor} sends an "OK" response before upgrading the {@link ChannelPipeline}s.
 */
public abstract class Junctor {

  protected final Logger logger ;

  /**
   * May differ from {@link Channel#localAddress()} as set in {@link #proxyListenAddress}?
   */
  protected final InetSocketAddress proxyListenAddress ;

  protected final Channel initiatorChannel ;
  protected final TcpTransitServer.Watcher watcher ;
  protected final TcpTransitServer.PipelineConfigurator.Factory pipelineConfiguratorFactory ;
  protected final int connectTimeoutMs ;

  protected Junctor(
      final Logger logger,
      final InetSocketAddress proxyListenAddress,
      final Channel initiatorChannel,
      final TcpTransitServer.Watcher watcher,
      final TcpTransitServer.PipelineConfigurator.Factory pipelineConfiguratorFactory,
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


  public abstract CompletableFuture< Junction > initializeInitiatorChannel() ;

  protected final void connectToTarget(
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
        .handler( TcpTransitTools.VOID_CHANNEL_INITIALIZER )  // Netty angry if no handler.
        .remoteAddress( targetAddress )
    ;

    final ChannelFuture connectFuture = bootstrap.connect() ;
    connectFuture.addListener( ( ChannelFutureListener ) future -> {
      if( future.cause() == null ) {
        final Channel targetChannel = future.channel() ;

        final Junction junction = createJunction( logger, targetChannel ) ;

        logger.debug( "Created " + junction + " after successful connection." ) ;
        // ChannelTools.dumpPipeline( initiatorContext.pipeline() ) ;
        junctionEstablished( initiatorContext, targetChannel, junctionFuture, junction ) ;
      } else {
        final TcpTransitTools.ConnectionFailedException connectionFailedException =
            new TcpTransitTools.ConnectionFailedException( targetAddress, future.cause() ) ;
        junctionFuture.completeExceptionally( connectionFailedException ) ;
        // throw connectionFailedException ;
      }
    } ) ;

  }

  protected abstract void junctionEstablished(
      final ChannelHandlerContext initiatorContext,
      final Channel targetChannel,
      final CompletableFuture< Junction > junctionFuture,
      final Junction junction
  ) ;

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

  protected final void configureJunction( final Channel targetChannel, final Junction junction ) {

    final TcpTransitServer.PipelineConfigurator pipelineConfigurator =
        pipelineConfiguratorFactory.createNew( logger ) ;

    junction.configure( TcpTransitServer.Edge.INITIATOR, pipelineConfigurator ) ;

    final boolean bytesInDetail = this.watcher.bytesInDetail() ;
    junction.addIngressConfiguration(
        initiatorChannel.pipeline(), TcpTransitServer.Edge.INITIATOR, bytesInDetail ) ;

    junction.addEgressConfiguration( this.initiatorChannel.pipeline(), TcpTransitServer.Edge.INITIATOR, bytesInDetail ) ;

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
    TcpTransitTools.removeAllLoggingHandlers( initiatorChannel.pipeline() ) ;

    if( TcpTransitTools.DUMP_PAYLOADS ) {
      addLoggingHandlerFirst( initiatorChannel.pipeline(), INITIATOR_SIDE_OUT.handlerName() ) ;
      addLoggingHandlerLast( initiatorChannel.pipeline(), INITIATOR_SIDE_IN.handlerName() ) ;
    }


    logger.debug( "Configured for " + TcpTransitServer.Edge.INITIATOR + ": " + this.initiatorChannel.pipeline() + "." ) ;


    junction.configure( TcpTransitServer.Edge.TARGET, pipelineConfigurator ) ;

    junction.addIngressConfiguration(
        targetChannel.pipeline(), TcpTransitServer.Edge.TARGET, bytesInDetail ) ;

    junction.addEgressConfiguration( targetChannel.pipeline(), TcpTransitServer.Edge.TARGET, bytesInDetail ) ;

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

    if( TcpTransitTools.DUMP_PAYLOADS ) {
      addLoggingHandlerFirst( targetChannel.pipeline(), TARGET_SIDE_IN.handlerName() ) ;
      addLoggingHandlerLast( targetChannel.pipeline(), TARGET_SIDE_OUT.handlerName() ) ;
    }

    logger.debug( "Configured for " + TcpTransitServer.Edge.TARGET + ": " + targetChannel.pipeline() ) ;
  }


  protected static DefaultFullHttpResponse createHttpResponseMethodNotAllowed() {
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


  @Override
  public final String toString() {
    return ToStringTools.nameAndCompactHash( this ) + "{" + proxyListenAddress + "}" ;
  }


  protected static InetSocketAddress inetSocketAddress( final String host ) {
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
