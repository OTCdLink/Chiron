package com.otcdlink.chiron.flow.journal.slicer;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ByteProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Wraps the {@code MappedByteBuffer} and create {@link Slice} instances that represent
 * small read-only views on it.
 */
class FileChunk {

  private static final Logger LOGGER = LoggerFactory.getLogger( FileChunk.class ) ;

  private final int sliceMaximumCapacity ;

  private final MappedByteBuffer mappedByteBuffer ;
  private final ByteBuf chunkBuffer ;
  private int nextSliceIndexInChunk = 0 ;

  // private final String toString ;


  public FileChunk(
      final FileChannel fileChannel,
      final int probableSliceCount,
      final int sliceMaximumCapacity,
      final long firstByteInFile,
      final long chunkLength
  ) throws IOException {
    checkNotNull( fileChannel ) ;
    this.sliceMaximumCapacity = sliceMaximumCapacity ;
    this.mappedByteBuffer = fileChannel.map(
        FileChannel.MapMode.READ_ONLY, firstByteInFile, chunkLength ) ;
    this.chunkBuffer = Unpooled.wrappedBuffer( mappedByteBuffer ) ;
    nextSliceIndexInChunk = 0 ;

    // this.toString = getClass().getSimpleName() + "{" +
    //     "firstByteInFile=" + firstByteInFile + ";" +
    //     "length=" + mappedByteBuffer.capacity() +
    //     "}"
    // ;
  }

  // @Override
  // public String toString() {
  //   return toString ;
  // }

  public void forEachByte( final int start, final ByteProcessor byteProcessor ) {
    chunkBuffer.forEachByte( start, chunkBuffer.readableBytes() - start, byteProcessor ) ;
  }

  /**
   * Return a {@link Slice} wrapping {@link #chunkBuffer}.
   * <p>
   * Not synchronized, must be called always by the same "slicer" thread.
   * Unbounded, the caller knows when to switch to next {@link FileChunk}.
   */
  public Slice newSlice(
      final long sliceIndexInFile,
      final int sliceStartInChunk,
      final int sliceEndInChunk
  ) {
    final Slice slice;
    slice = newSlice(
        chunkBuffer,
        sliceStartInChunk,
        sliceEndInChunk,
        sliceIndexInFile
    ) ;
    nextSliceIndexInChunk ++ ;
    return slice;
  }


  /**
   * Tries to close the {@code java.nio.MappedByteBuffer} which otherwise waits to be
   * garbage-collected.
   * https://stackoverflow.com/a/19447758/1923328
   */
  public static void closeDirectBuffer( final ByteBuffer byteBuffer ) {
    if( byteBuffer == null || ! byteBuffer.isDirect() ) {
      return ;
    }
    try {
      CLEANERS.get( byteBuffer.getClass() ).clean( byteBuffer ) ;
    } catch( ExecutionException e ) {
      LOGGER.error( "Unexpected.", e ) ;
    }
  }

  private static final LoadingCache< Class< ? extends ByteBuffer >, Cleaner >
      CLEANERS = CacheBuilder.newBuilder().build(
          new CacheLoader< Class< ? extends ByteBuffer >, Cleaner >() {
            @Override
            public Cleaner load( @Nonnull Class< ? extends ByteBuffer > key ) throws Exception {
              return new Cleaner<>( key ) ;
            }
          }
      )
  ;

  private static class Cleaner< BYTEBUFFER extends ByteBuffer > {
    final Class< BYTEBUFFER > byteBufferClass ;
    final Method cleaner ;
    final Method clean ;
    public Cleaner( final Class< BYTEBUFFER > byteBufferClass ) {
      this.byteBufferClass = checkNotNull( byteBufferClass ) ;
      Method cleaner ;
      Method clean ;
      try {
        // we could use this type cast and call functions without reflection code,
        // but static import from sun.* package is risky for non-SUN virtual machine.
        //try { ((sun.nio.ch.DirectBuffer)byteBuffer).cleaner().clean(); } catch (Exception ex) { }
        cleaner = byteBufferClass.getMethod( "cleaner" ) ;
        cleaner.setAccessible( true ) ;
        clean = Class.forName( "sun.misc.Cleaner" ).getMethod( "clean" ) ;
        clean.setAccessible( true ) ;
      } catch( NoSuchMethodException | ClassNotFoundException e ) {
        this.cleaner = null ;
        this.clean = null ;
        return ;
      }
      this.cleaner = cleaner ;
      this.clean = clean ;
    }

    public void clean( final BYTEBUFFER byteBuffer ) {
      if( clean != null ) {
        try {
          clean.invoke( cleaner.invoke( byteBuffer ) ) ;
        } catch( final IllegalAccessException | InvocationTargetException ignore ) { }
      }
    }
  }


  private Slice newSlice(
      final ByteBuf wrapped,
      final int readerIndex,
      final int writerIndex,
      final long sliceIndexInFile

  ) {
    final Slice slice = new Slice(
        sliceMaximumCapacity,
        wrapped,
        readerIndex,
        writerIndex,
        sliceIndexInFile
    ) ;
    // LOGGER.trace( "Created " + slice + "." ) ;
    return slice ;
  }

  public int readableBytes() {
    return chunkBuffer.readableBytes() ;
  }

}
