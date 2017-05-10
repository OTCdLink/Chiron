package io.github.otcdlink.chiron.middle.tier;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * Much code copied from {@link io.netty.channel.SimpleChannelInboundHandler}.
 */
public abstract class SelectiveDuplexTier< INBOUND, OUTBOUND >
    extends ChannelDuplexHandler
{

  private final TypeParameterMatcher inboundMessageMatcher ;
  private final TypeParameterMatcher outboundMessageMatcher ;


  protected SelectiveDuplexTier() {
    inboundMessageMatcher = TypeParameterMatcher.find(
        this, SelectiveDuplexTier.class, "INBOUND" ) ;
    outboundMessageMatcher = TypeParameterMatcher.find(
        this, SelectiveDuplexTier.class, "OUTBOUND" ) ;
  }


// =======
// Inbound
// =======

  /**
   * Returns {@code true} if the given message should be handled. If {@code false} it will be passed
   * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
   */
  public boolean acceptInboundMessage( final Object msg ) throws Exception {
    return inboundMessageMatcher.match( msg ) ;
  }

  @Override
  public void channelRead(
      final ChannelHandlerContext channelHandlerContext,
      final Object inbound
  ) throws Exception {
    if( acceptInboundMessage( inbound ) ) {
      @SuppressWarnings( "unchecked" ) final INBOUND imsg = ( INBOUND ) inbound ;
      inboundMessage( channelHandlerContext, imsg ) ;
    } else {
      forwardInbound( channelHandlerContext, inbound ) ;
    }
  }

  protected static void forwardInbound(
      final ChannelHandlerContext channelHandlerContext,
      final Object inbound
  ) {
    channelHandlerContext.fireChannelRead( inbound ) ;
  }

  protected abstract void inboundMessage(
      ChannelHandlerContext channelHandlerContext,
      INBOUND inbound
  ) throws Exception ;


// ========
// Outbound
// ========

  /**
   * Returns {@code true} if the given message should be handled. If {@code false} it will be passed
   * to the next {@link ChannelOutboundHandler} in the {@link ChannelPipeline}.
   */
  protected boolean acceptOutboundMessage( final Object msg ) throws Exception {
    return outboundMessageMatcher.match( msg ) ;
  }

  @Override
  public void write(
      final ChannelHandlerContext channelHandlerContext,
      final Object outbound,
      final ChannelPromise promise
  ) throws Exception {
    if( acceptOutboundMessage( outbound ) ) {
      @SuppressWarnings( "unchecked" )
      final OUTBOUND outboundMessage = ( OUTBOUND ) outbound ;
      outboundMessage( channelHandlerContext, outboundMessage, promise ) ;
    } else {
      forwardOutbound( channelHandlerContext, outbound, promise ) ;
    }
  }

  protected static void forwardOutbound(
      final ChannelHandlerContext channelHandlerContext,
      final Object outbound,
      final ChannelPromise promise
  ) {
    channelHandlerContext.write( outbound, promise ) ;
  }

  protected abstract void outboundMessage(
      ChannelHandlerContext channelHandlerContext,
      OUTBOUND outbound,
      ChannelPromise promise
  ) throws Exception ;


}
