package io.github.otcdlink.chiron.downend.tier;

import io.github.otcdlink.chiron.downend.DownendConnector;
import io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Initializes a WebSocket connection, and listens to {@link Channel} events.
 * The {@link DownendConnector} uses and reuses a single instance of
 * {@link DownendSupervisionTier} across (re)connections.
 *
 * Reuse of this {@link ChannelHandler} when reconnecting requires {@link Sharable} annotation.
 * We must keep the same {@link ChannelHandler} instance because WebSocket handshake is stateful.
 */
@ChannelHandler.Sharable
public class DownendSupervisionTier extends SimpleChannelInboundHandler< Object > {

  private static final Logger LOGGER = LoggerFactory.getLogger( DownendSupervisionTier.class ) ;

  public interface Claim extends ClientHandshakerEnhancer.PipelineReconfigurator {

    /**
     * Delegating creation to {@link DownendConnector} avoids exposing some parameters from
     * {@link DownendConnector.Setup}.
     */
    WebSocketClientHandshaker createHandshaker() ;

    void afterWebsocketHandshake( ConnectionDescriptor connectionDescriptor ) ;

    void disconnectionHappened( ChannelHandlerContext channelHandlerContext ) ;

    void kickoutHappened( ChannelHandlerContext channelHandlerContext ) ;

    void problem( final Throwable cause ) ;
  }

  private final Claim claim ;
  private WebSocketClientHandshaker handshaker = null ;
  private ChannelPromise handshakeFuture = null ;
  private boolean notifyOfDisconnection = true ;

  public DownendSupervisionTier( final Claim claim ) {
    this.claim = checkNotNull( claim ) ;
  }

  @Override
  public void channelRegistered( final ChannelHandlerContext channelHandlerContext ) {
    LOGGER.debug( "Channel " + channelHandlerContext.channel().id() + " is registered." ) ;
  }

  @Override
  public void channelUnregistered( final ChannelHandlerContext channelHandlerContext ) {
    LOGGER.debug( "Channel " + channelHandlerContext.channel().id() + " is unregistered." ) ;
  }

  /**
   * Called after TCP connection succeeded.
   */
  @Override
  public void channelActive( final ChannelHandlerContext channelHandlerContext ) {
    LOGGER.debug( "Channel " + channelHandlerContext.channel().id() +
        " is active, handshake happens." ) ;
    notifyOfDisconnection = true ;
    handshaker = claim.createHandshaker() ;
    handshakeFuture = channelHandlerContext.newPromise() ;
    handshaker.handshake( channelHandlerContext.channel() ) ;
  }

  /**
   * Called after TCP connection closed.
   */
  @Override
  public void channelInactive( final ChannelHandlerContext channelHandlerContext ) {
    LOGGER.debug(
        "Channel " + channelHandlerContext.channel().id() +
            " got inactive. Notifying of state change, and scheduling next connection ..."
    ) ;
    disconnectedForAnyReason( channelHandlerContext ) ;
  }

  @Override
  public void channelRead0(
      final ChannelHandlerContext context,
      final Object messageAsObject
  ) throws Exception {
    final Channel channel = context.channel() ;
    if( handshaker != null && ! handshaker.isHandshakeComplete() ) {
      final FullHttpResponse fullHttpResponse = ( FullHttpResponse ) messageAsObject ;
      final ConnectionDescriptor connectionDescriptor =
          ConnectionDescriptor.from( fullHttpResponse.headers() ) ;
      new ClientHandshakerEnhancer( handshaker, claim )
          .finishHandshake( channel, connectionDescriptor, fullHttpResponse ) ;
      handshakeFuture.setSuccess() ;
      claim.afterWebsocketHandshake( connectionDescriptor ) ;
      LOGGER.debug( "Handshake complete, now HTTP responses must be about WebSockets." ) ;
      // ChannelTools.dumpPipeline( context.pipeline() ) ;
      return ;
    }

    if( messageAsObject instanceof FullHttpResponse ) {
      final FullHttpResponse fullHttpResponse = ( FullHttpResponse ) messageAsObject ;
      if( HttpResponseStatus.SWITCHING_PROTOCOLS.code() == fullHttpResponse.status().code() ) {
        return ;
      } else {
        throw new IllegalStateException(
            "Unexpected FullHttpResponse (getStatus=" + fullHttpResponse.status() +
                ", content=" + fullHttpResponse.content().toString( CharsetUtil.UTF_8 ) + ')' ) ;
      }
    }

    if( messageAsObject instanceof CloseWebSocketFrame ) {
      LOGGER.info( "Received " + messageAsObject + ", closing " + context.channel() + "." ) ;
      notifyOfDisconnection = false ;
      claim.kickoutHappened( context ) ;
      context.close() ;
      return ;
    }

    context.fireChannelRead( messageAsObject ) ;
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext,
      final Throwable cause
  ) {
    if( handshakeFuture != null && ! handshakeFuture.isDone() ) {
      handshakeFuture.setFailure( cause ) ;
    }

    if( cause instanceof IOException ) {
      /** TODO: factor with {@link #channelInactive(io.netty.channel.ChannelHandlerContext)} */
      disconnectedForAnyReason( channelHandlerContext );
    } else {
      channelHandlerContext.close() ;
      LOGGER.error( "Exeption occured within " + channelHandlerContext + ", now closed.", cause ) ;
      claim.problem( cause ) ;

    }

  }

  private void disconnectedForAnyReason( final ChannelHandlerContext channelHandlerContext ) {
    handshaker = null ;
    handshakeFuture = null ;
    if( notifyOfDisconnection ) {
      try {
        claim.disconnectionHappened( channelHandlerContext ) ;
      } catch( final Exception e ) {
        LOGGER.error( "Problem while notifying " + claim + ".", e ) ;
      }
    }
  }

}
