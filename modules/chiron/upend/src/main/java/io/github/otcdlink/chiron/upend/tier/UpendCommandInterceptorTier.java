package io.github.otcdlink.chiron.upend.tier;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.middle.tier.CommandInterceptor;
import io.github.otcdlink.chiron.middle.tier.CommandInterceptorTier;
import io.netty.channel.ChannelHandlerContext;

public final class UpendCommandInterceptorTier extends CommandInterceptorTier {
  public UpendCommandInterceptorTier( final CommandInterceptor commandInterceptor ) {
    super( commandInterceptor ) ;
  }

  @Override
  protected boolean interceptRead(
      final ChannelHandlerContext channelHandlerContext,
      final Command inboundCommand
  ) {
    return commandInterceptor.interceptUpward(
        inboundCommand, inboundSink( channelHandlerContext ) ) ;
  }

  @Override
  protected boolean interceptWrite(
      final ChannelHandlerContext channelHandlerContext,
      final Command outboundCommand
  ) {
    return commandInterceptor.interceptDownward(
        outboundCommand, outboundSink( channelHandlerContext ) ) ;
  }

}
