package io.github.otcdlink.chiron.upend.tier;

import com.google.common.base.Preconditions;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.upend.http.CommandRecognizer;
import io.github.otcdlink.chiron.upend.http.RenderableCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * @deprecated because {@link CommandRecognizer} is.
 */
public class CommandRecognizerTier
    extends SimpleChannelInboundHandler< FullHttpRequest >
{
  private final CommandRecognizer< FullHttpRequest, Command< Designator, ? > >
      commandRecognizer ;

  public CommandRecognizerTier(
      final CommandRecognizer< FullHttpRequest, Command< Designator, ? > >
          commandRecognizer
  ) {
    this.commandRecognizer = Preconditions.checkNotNull( commandRecognizer ) ;
  }

  /**
   * Attempts to get a {@link Command} from {@link CommandRecognizer}, and injects it in
   * {@link io.netty.channel.ChannelPipeline} if there is one.
   */
  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final FullHttpRequest fullHttpRequest
  ) throws Exception {
    final Command< Designator, ? > recognized = commandRecognizer.recognize(
        fullHttpRequest ) ;
    if( recognized != null ) {
      channelHandlerContext.fireChannelRead( recognized ) ;
    }
    /** TODO: write and flush if instance of {@link RenderableCommand}. */
  }

}
