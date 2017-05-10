package io.github.otcdlink.chiron.upend.http.content.caching;

import com.google.common.io.ByteSource;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;
import io.github.otcdlink.chiron.upend.http.content.StaticContent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Keeps "small" content (less than 2 TB) in Heap/native buffer.
 */
public final class StaticContentCache {

  private final ByteBufAllocator bytebufAllocator ;

  private final StaticContentResolver< StaticContent.Streamed > staticContentResolver ;

  private final ConcurrentMap< String, BytebufContent > bytebufMap ;

  StaticContentCache(
      final ConcurrentMap< String, BytebufContent > bytebufMap,
      final StaticContentResolver< StaticContent.Streamed > staticContentResolver,
      final ByteBufAllocator bytebufAllocator
  ) {
    this.bytebufMap = checkNotNull( bytebufMap ) ;
    this.staticContentResolver = checkNotNull( staticContentResolver ) ;
    this.bytebufAllocator = checkNotNull( bytebufAllocator ) ;
  }

  /**
   * This method could take a long to execute and throw an {@code IOException} so it's
   * not well-suited for {@link java.util.Map#computeIfAbsent(Object, Function)}
   * but we'll see. The {@code ConcurrentHashMap} brings a lot of goodness (speed, simplicity),
   * we shouldn't give it up with no serious reason.
   */
  private BytebufContent createCacheableContent( final String resourcePath ) {
    final StaticContent staticContent = staticContentResolver.apply( resourcePath ) ;
    if( staticContent == null ) {
      return null ;
    } else {
      try {
        final ByteSource byteSource = ( ( StaticContent.Streamed ) staticContent ).byteSource ;
        final long resourceSize = byteSource.size() ;
        checkArgument( resourceSize < Integer.MAX_VALUE,
            "Requested resource too large (" + resourceSize + ") for '" + resourcePath + "'" ) ;
        final ByteBuf byteBuf = bytebufAllocator.buffer( ( int ) resourceSize ) ;
        try( final ByteBufOutputStream outputStream = new ByteBufOutputStream( byteBuf ) ) {
          byteSource.copyTo( outputStream ) ;
        }
        final BytebufContent staticContentAsByteBuf = new BytebufContent(
            NettyTools.unmodifiableBufferSafe( byteBuf ),
            staticContent.mimeType
        ) ;
        staticContentAsByteBuf.bytebuf().retain() ;
        return staticContentAsByteBuf ;
      } catch( final IOException e ) {
        throw new RuntimeException( "Should not happen with in-memory resources", e ) ;
      }
    }
  }


  public BytebufContent staticContent( final String resourcePath ) {
    checkNotNull( resourcePath ) ;
    final BytebufContent cached =
        bytebufMap.computeIfAbsent( resourcePath, this::createCacheableContent ) ;
    return cached == null ? null : cached ;
  }

}
