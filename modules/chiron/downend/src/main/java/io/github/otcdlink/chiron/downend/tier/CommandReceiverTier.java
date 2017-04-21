package io.github.otcdlink.chiron.downend.tier;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import static com.google.common.base.Preconditions.checkNotNull;

public class CommandReceiverTier<
    INBOUND_ENDPOINT_SPECIFIC,
    INBOUND_DUTY
>
    extends SimpleChannelInboundHandler<
        Command< INBOUND_ENDPOINT_SPECIFIC, INBOUND_DUTY >
    >
{

  private final CommandConsumer< Command< INBOUND_ENDPOINT_SPECIFIC, INBOUND_DUTY > >
      inboundCommandConsumer ;

  public CommandReceiverTier(
      final CommandConsumer< Command< INBOUND_ENDPOINT_SPECIFIC, INBOUND_DUTY > >
          inboundCommandConsumer
  ) {
    this.inboundCommandConsumer = checkNotNull( inboundCommandConsumer ) ;
  }

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final Command< INBOUND_ENDPOINT_SPECIFIC, INBOUND_DUTY > inboundMessage
  ) throws Exception {
    inboundCommandConsumer.accept( inboundMessage ) ;
  }

}
