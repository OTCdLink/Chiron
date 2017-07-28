package io.github.otcdlink.chiron.upend.tier;

import io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import io.github.otcdlink.chiron.middle.tier.WebsocketTools;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class UpendUpgradeTier extends SimpleChannelInboundHandler< Object > {

  private static final Logger LOGGER = LoggerFactory.getLogger( UpendUpgradeTier.class ) ;

  public interface Claim {
    void afterWebsocketHandshake( ChannelPipeline channelPipeline ) ;
  }

  private final Claim claim ;
  private final ConnectionDescriptor connectionDescriptor ;
  private final URL websocketUrl ;
  private final int maximumFramePayloadLength ;

  public UpendUpgradeTier(
      final Claim claim,
      final ConnectionDescriptor connectionDescriptor,
      final URL websocketUrl,
      final int maximumFramePayloadLength
  ) {
    super( false ) ;
    this.claim = checkNotNull( claim ) ;
    this.connectionDescriptor = checkNotNull( connectionDescriptor ) ;
    this.websocketUrl = UrxTools.checkValidWebsocketUrl( websocketUrl ) ;
    checkArgument( maximumFramePayloadLength > 0 ) ;
    this.maximumFramePayloadLength = maximumFramePayloadLength ;
  }


  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final Object inboundMessage
  ) throws Exception {
    if( inboundMessage instanceof FullHttpRequest ) {
      final FullHttpRequest fullHttpRequest = ( FullHttpRequest ) inboundMessage ;
      if( websocketUrl.getPath().equals( fullHttpRequest.uri() ) ) {
        handshake( channelHandlerContext, fullHttpRequest ) ;
        fullHttpRequest.release() ;
        return ;
      }
    }
    channelHandlerContext.fireChannelRead( inboundMessage ) ;
  }

  private void handshake(
      final ChannelHandlerContext channelHandlerContext,
      final FullHttpRequest fullHttpRequest
  ) {
    final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
        websocketUrl.toExternalForm(),
        null,
        false,
        maximumFramePayloadLength,
        ! WebsocketTools.MASK_WEBSOCKET_FRAMES_FROM_CLIENT
    ) ;
    final WebSocketServerHandshaker handshaker = wsFactory.newHandshaker( fullHttpRequest ) ;
    if( handshaker == null ) {
      WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(
          channelHandlerContext.channel() ) ;
    } else {
      handshaker.handshake(
          channelHandlerContext.channel(),
          fullHttpRequest,
          connectionDescriptor.httpHeaders(),
          channelHandlerContext.newPromise()
      ).addListener( future -> {
        if( future.isSuccess() ) {
          LOGGER.info( "Handshook '" + fullHttpRequest.uri() + "' " + " on " +
              channelHandlerContext.channel() + "." ) ;
          claim.afterWebsocketHandshake( channelHandlerContext.pipeline() ) ;
        } else {
          LOGGER.error( "Handshake with " + channelHandlerContext.channel() + " failed.",
              future.cause() ) ;
          channelHandlerContext.fireExceptionCaught( future.cause() ) ;
        }
      } ) ;
    }
  }


}
