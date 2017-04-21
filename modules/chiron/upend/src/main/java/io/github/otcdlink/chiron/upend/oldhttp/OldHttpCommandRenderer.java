package io.github.otcdlink.chiron.upend.oldhttp;

import io.github.otcdlink.chiron.command.Command;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;

@Deprecated
public interface OldHttpCommandRenderer {

  FullHttpResponse render(
      final ByteBufAllocator byteBufAllocator,
      final HttpVersion httpVersion,
      final Command< ?, ? > command
  ) ;

}
