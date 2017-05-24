package io.github.otcdlink.chiron.upend.http.dispatch;

import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

/**
 * Generic behavior for feeding a {@link ChannelPipeline} starting at a given
 * {@link ChannelHandler}.
 * <h1>Usage</h1>
 * Instances of this classes are the possible outcome of a call to
 * {@link HttpResponder.Outbound#outbound(EvaluationContext, RichHttpRequest)}.
 * Unless they are really sure they want to hack the framework, implementors should do no
 * more than calling {@link ChannelHandlerContext#write(Object)} and
 * {@link ChannelHandlerContext#writeAndFlush(Object)}
 * (but Netty doesn't support such contract restriction).
 */
public interface PipelineFeeder {
  void feed( final ChannelHandlerContext channelHandlerContext, boolean keepAlive ) ;
}
