package io.github.otcdlink.chiron.upend.tier;

import com.google.common.base.Preconditions;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.github.otcdlink.chiron.upend.http.CommandRecognizer;
import io.github.otcdlink.chiron.upend.http.RenderableCommand;
import io.github.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;

public class ImmediateCommandRecognizerTier
    extends SimpleChannelInboundHandler< FullHttpRequest >
{
  private final CommandRecognizer<RichHttpRequest, Command< Designator, ? > >
      commandRecognizer ;

  public ImmediateCommandRecognizerTier(
      final CommandRecognizer<RichHttpRequest, Command< Designator, ? > >
          commandRecognizer
  ) {
    this.commandRecognizer = Preconditions.checkNotNull( commandRecognizer ) ;
  }

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final FullHttpRequest fullHttpRequest
  ) throws Exception {
    final RichHttpRequest richHttpRequest;
    try {
      richHttpRequest = new RichHttpRequest(
          fullHttpRequest,
          channelHandlerContext.pipeline()
      ) ;
    } catch( final Exception e ) {
      new UsualHttpCommands.BadRequest( "Bad request: " + fullHttpRequest.uri() )
          .feed( channelHandlerContext ) ;
      return ;
    }
    final Command< Designator, ? > recognized = commandRecognizer.recognize(
        richHttpRequest ) ;
    if( recognized != null ) {
      if( recognized instanceof RenderableCommand.OnPipeline ) {
        ( ( RenderableCommand.OnPipeline ) recognized )
            .callReceiver( channelHandlerContext ) ;
      } else {
        channelHandlerContext.fireChannelRead( recognized ) ;
//        channelHandlerContext.writeAndFlush( recognized ) ;
      }
    }
  }

}
