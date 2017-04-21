package io.github.otcdlink.chiron.upend.http;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.github.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * Attempts to transform some object into a {@link Command}.
 *
 * @param <COMMAND> type of recognized {@link Command}.
 * @param <INBOUND> typically a {@link FullHttpRequest} or {@link TextWebSocketFrame} in
 *     {@link ChannelPipeline}.
 *
 * @deprecated use {@link HttpRequestRelayer} instead.
 */
public interface CommandRecognizer< INBOUND, COMMAND > extends HttpRequestRelayer {

  @Override
  default boolean relay(
      final RichHttpRequest httpRequest,
      final ChannelHandlerContext channelHandlerContext
  ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  /**
   * @return {@code null} if no transformation was possible.
   */
  COMMAND recognize( final INBOUND inbound ) ;


}
