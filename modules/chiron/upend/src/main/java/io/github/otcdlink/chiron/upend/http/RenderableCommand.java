package io.github.otcdlink.chiron.upend.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

/**
 *
 * @param <CALLABLE_RECEIVER> {@link ChannelPipeline} or {@link HttpResponseReceiver}.
 */
public interface RenderableCommand< CALLABLE_RECEIVER > {

  void callReceiver( CALLABLE_RECEIVER callableReceiver ) ;

  interface OnPipeline extends RenderableCommand< ChannelHandlerContext > { }

  interface OnReceiver extends RenderableCommand< HttpResponseReceiver > { }

}
