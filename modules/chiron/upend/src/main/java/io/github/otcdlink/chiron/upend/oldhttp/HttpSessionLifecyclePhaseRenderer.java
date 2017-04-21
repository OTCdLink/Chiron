package io.github.otcdlink.chiron.upend.oldhttp;

import io.github.otcdlink.chiron.middle.session.SessionLifecycle;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;

@Deprecated
public interface HttpSessionLifecyclePhaseRenderer {

  FullHttpResponse render(
      final ByteBufAllocator byteBufAllocator,
      final HttpVersion httpVersion,
      final SessionLifecycle.Phase phase
  ) ;

}
