package io.github.otcdlink.chiron.upend.tier;

import io.github.otcdlink.chiron.upend.http.ContextPathAwareCommand;
import io.github.otcdlink.chiron.upend.http.RenderableCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated with {@link io.github.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer}
 *     there is no need to wrap rendering in a {@link RenderableCommand}.
 */
public class CommandRendererTier extends ChannelOutboundHandlerAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      CommandRendererTier.class ) ;

  /**
   * Decrements {@link ReferenceCounted#refCnt()} if
   * {@link ContextPathAwareCommand#initialHttpRequest} supports it.
   *
   * @see CommandRecognizerTier#channelRead0(io.netty.channel.ChannelHandlerContext, io.netty.handler.codec.http.FullHttpRequest)
   */
  @Override
  public void write(
      final ChannelHandlerContext channelHandlerContext,
      final Object outbound,
      final ChannelPromise promise
  ) throws Exception {

    if( outbound instanceof RenderableCommand.OnPipeline ) {
      final RenderableCommand.OnPipeline renderableOnPipeline =
          ( RenderableCommand.OnPipeline ) outbound ;
      renderableOnPipeline.callReceiver( channelHandlerContext ) ;
      if( outbound instanceof ContextPathAwareCommand ) {
        final ContextPathAwareCommand command = ( ContextPathAwareCommand ) outbound ;
        if( command.initialHttpRequest instanceof ReferenceCounted ) {
          ( ( ReferenceCounted ) command.initialHttpRequest ).release() ;
        }
      }
    } else {
      channelHandlerContext.write( outbound, promise ) ;
    }

  }


  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext,
      final Throwable cause
  ) {
    LOGGER.error( "Caught Throwable in " + this + ".", cause ) ;
    channelHandlerContext.close() ;
  }
}
