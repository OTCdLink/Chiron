package io.github.otcdlink.chiron.upend.http.caching;

import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPipeline;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Caches some byte sequence in a {@link ByteBuf} that will be poured directly into Netty's
 * {@link ChannelPipeline}.
 * May be in Heap or in a Native Buffer, depending on {@link ByteBufAllocator} used.
 * <p>
 * This class is {@code public} because {@link StaticContentCache#staticContent(java.lang.String)}
 * return an instance of it, and Netty-dependant code should get access to the {@link ByteBuf}.
 */
public class BytebufContent extends MimeTypedResource {

  private final ByteBuf byteBuf ;

  BytebufContent( final ByteBuf byteBuf, final String mimeType ) {
    super( null, mimeType ) ;
    checkArgument( byteBuf.isReadable() ) ;
    checkArgument( ! byteBuf.isWritable() ) ;
    this.byteBuf = byteBuf ;
  }

  @Override
  protected String bodyAsString() {
    return ToStringTools.nameAndHash( bytebuf() ) ;
  }

  public ByteBuf bytebuf() {
    return Unpooled.unmodifiableBuffer( byteBuf ) ;
  }
}
