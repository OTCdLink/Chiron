package com.otcdlink.chiron.upend.http.dispatch;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Generic low-level behavior for doing things with inbound {@link RichHttpRequest},
 * typically reacting by injecting an inbound {@link Command} (which can be seen as the
 * pivot format for doing more complex things), but it can also inject an outbound
 * {@link HttpResponse} to react immediately and do no further inbound processings.
 *
 * @see HttpResponder.Condition
 * @see HttpResponder.Resolver
 * @see HttpResponder.DutyCaller
 */
public interface HttpRequestRelayer {

  /**
   * @return {@code true} if this {@link HttpRequestRelayer} handled the
   *     {@link RichHttpRequest}, so there should be no further calls to
   *     {@link #relay(RichHttpRequest, ChannelHandlerContext)} for this
   *     {@link RichHttpRequest}.
   */
  boolean relay(
      final RichHttpRequest httpRequest,
      final ChannelHandlerContext channelHandlerContext
  ) ;

}
