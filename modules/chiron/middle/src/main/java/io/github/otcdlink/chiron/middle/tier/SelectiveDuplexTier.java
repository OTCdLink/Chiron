package io.github.otcdlink.chiron.middle.tier;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * Much code copied from {@link io.netty.channel.SimpleChannelInboundHandler}.
 */
public abstract class SelectiveDuplexTier< INBOUND, OUTBOUND >
    extends ChannelDuplexHandler
{

  private final boolean autoRelease ;
  private final TypeParameterMatcher inboundMessageMatcher ;
  private final TypeParameterMatcher outboundMessageMatcher ;


  protected SelectiveDuplexTier() {
    this( false ) ;
  }

  protected SelectiveDuplexTier( final boolean autoRelease ) {
    inboundMessageMatcher = TypeParameterMatcher.find(
        this, SelectiveDuplexTier.class, "INBOUND" ) ;
    outboundMessageMatcher = TypeParameterMatcher.find(
        this, SelectiveDuplexTier.class, "OUTBOUND" ) ;
    this.autoRelease = autoRelease ;
  }

  protected SelectiveDuplexTier(
      final Class< ? extends INBOUND > inboundMessageType,
      final Class< ? extends OUTBOUND > outboundMessageType
  ) {
    this( inboundMessageType, outboundMessageType, false ) ;
  }

  protected SelectiveDuplexTier(
      final Class< ? extends INBOUND > inboundMessageType,
      final Class< ? extends OUTBOUND > outboundMessageType,
      final boolean autoRelease
  ) {
    inboundMessageMatcher = TypeParameterMatcher.get( inboundMessageType ) ;
    outboundMessageMatcher = TypeParameterMatcher.get( outboundMessageType ) ;
    this.autoRelease = autoRelease ;
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
    boolean release = true ;
    try {
      if( acceptInboundMessage( inbound ) ) {
        @SuppressWarnings( "unchecked" ) final INBOUND imsg = ( INBOUND ) inbound ;
        inboundMessage( channelHandlerContext, imsg ) ;
      } else {
        release = false ;
        forwardInbound( channelHandlerContext, inbound ) ;
      }
    } finally {
      if( autoRelease && release ) {
        ReferenceCountUtil.release( inbound ) ;
      }
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
    boolean release = true ;
    try {
      if( acceptOutboundMessage( outbound ) ) {
        @SuppressWarnings( "unchecked" )
        final OUTBOUND outboundMessage = ( OUTBOUND ) outbound ;
        outboundMessage( channelHandlerContext, outboundMessage, promise ) ;
      } else {
        release = false ;
        forwardOutbound( channelHandlerContext, outbound, promise ) ;
      }
    } finally {
      if( autoRelease && release ) {
        ReferenceCountUtil.release( outbound ) ;
      }
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
