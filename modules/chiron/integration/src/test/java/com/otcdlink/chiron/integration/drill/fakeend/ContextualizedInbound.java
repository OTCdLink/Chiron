package com.otcdlink.chiron.integration.drill.fakeend;

import io.netty.channel.ChannelHandlerContext;

import static com.google.common.base.Preconditions.checkNotNull;

class ContextualizedInbound< MESSAGE > {
  public final ChannelHandlerContext channelHandlerContext ;
  public final MESSAGE message ;

  public ContextualizedInbound(
      final ChannelHandlerContext channelHandlerContext,
      final MESSAGE message
  ) {
    this.channelHandlerContext = checkNotNull( channelHandlerContext ) ;
    this.message = message ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + channelHandlerContext + ";" + message + "}" ;
  }
}
