package com.otcdlink.chiron.upend.tier;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.toolbox.ReadableStateHolder;
import com.otcdlink.chiron.upend.UpendConnector;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class CommandReceiverTier< UPWARD_DUTY >
    extends SimpleChannelInboundHandler<Command< Designator, UPWARD_DUTY >>
{
  private static final Logger LOGGER = LoggerFactory.getLogger( CommandReceiverTier.class ) ;

  private final ReadableStateHolder< UpendConnector.State > readableState ;
  private final CommandConsumer< Command<Designator, UPWARD_DUTY > > commandConsumer ;

  public CommandReceiverTier(
      final ReadableStateHolder<UpendConnector.State> readableState,
      final CommandConsumer< Command<Designator, UPWARD_DUTY > > commandConsumer
  ) {
    this.readableState = checkNotNull( readableState ) ;
    this.commandConsumer = checkNotNull( commandConsumer ) ;
  }

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final Command< Designator, UPWARD_DUTY > command
  ) throws Exception {
    final UpendConnector.State currentState = readableState.get() ;
    if( currentState == UpendConnector.State.STARTED ) {
      commandConsumer.accept( command ) ;
      LOGGER.debug( "Passing " + command + " to " + commandConsumer + "." ) ;
    } else {
      LOGGER.info( "Quietly dropping " + command + " because in " + currentState + " state." ) ;
    }
  }
}
