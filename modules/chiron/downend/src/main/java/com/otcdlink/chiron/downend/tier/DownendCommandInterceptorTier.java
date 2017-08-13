package com.otcdlink.chiron.downend.tier;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.middle.tier.CommandInterceptorTier;
import io.netty.channel.ChannelHandlerContext;

public final class DownendCommandInterceptorTier extends CommandInterceptorTier
{
  public DownendCommandInterceptorTier( final CommandInterceptor commandInterceptor ) {
    super( commandInterceptor ) ;
  }

  @Override
  protected boolean interceptRead(
      final ChannelHandlerContext channelHandlerContext,
      final Command inboundCommand
  ) {
    return commandInterceptor.interceptDownward(
        inboundCommand, inboundSink( channelHandlerContext ) ) ;
  }

  @Override
  protected boolean interceptWrite(
      final ChannelHandlerContext channelHandlerContext,
      final Command outboundCommand
  ) {
    return commandInterceptor.interceptUpward(
        outboundCommand, outboundSink( channelHandlerContext ) ) ;
  }
}
