package io.github.otcdlink.chiron.upend.oldhttp;

import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.upend.http.RenderableCommand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.io.IOException;

/**
 * A {@link Command} which embeds its own HTTP rendering abilities.
 * This is convenient for quick-and-dirty HTML rendering.
 */
@Deprecated
public abstract class HttpRenderableCommand extends Command< Designator, Void > {

  protected HttpRenderableCommand( final Designator internal ) {
    super( internal ) ;
  }

  @Override
  public final void callReceiver( final Void ignore ) {
    throw new UnsupportedOperationException( "Do not call" ) ;
  }

  @Override
  public final void encodeBody( final PositionalFieldWriter ignore ) throws IOException {
    throw new UnsupportedOperationException( "Do not call" ) ;
  }

  public abstract FullHttpResponse render(
      final ByteBufAllocator byteBufAllocator,
      final HttpVersion httpVersion
  ) ;


  public static class Factory {
    private final Designator.Factory designatorFactory ;

    public Factory( final Designator.Factory designatorFactory ) {
      this.designatorFactory = designatorFactory ;
    }

    public Command< Designator, ? > simpleHtml(
        final String html,
        final HttpResponseStatus httpResponseStatus
    ) {
      class SimpleHtmlRenderableCommand
          extends Command< Designator, ChannelHandlerContext >
          implements RenderableCommand.OnPipeline
      {
        private SimpleHtmlRenderableCommand( final Designator internal ) {
          super( internal ) ;
        }

        @Override
        public void encodeBody( final PositionalFieldWriter Ø ) {
          throw new UnsupportedOperationException( "Do not call" ) ;
        }

        @Override
        public void callReceiver( final ChannelHandlerContext channelHandlerContext ) {
          final ByteBuf buffer = Unpooled.buffer() ;
          ByteBufUtil.writeUtf8( buffer, html ) ;
          final FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1,
              httpResponseStatus,
              buffer
          ) ;
          channelHandlerContext.writeAndFlush( fullHttpResponse )
              .addListener( ChannelFutureListener.CLOSE )
          ;
        }

      }

      return new SimpleHtmlRenderableCommand( designatorFactory.internal() ) ;
    }

  }

  @Deprecated
  public static class AutomaticRenderer implements OldHttpCommandRenderer {

    @Override
    public FullHttpResponse render(
        final ByteBufAllocator byteBufAllocator,
        final HttpVersion httpVersion,
        final Command< ?, ? > command
    ) {
      if( command instanceof HttpRenderableCommand ) {
        return ( ( HttpRenderableCommand ) command ).render( byteBufAllocator, httpVersion ) ;
      }
      return null ;
    }

    public static final AutomaticRenderer INSTANCE = new AutomaticRenderer() ;
  }

}
