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
import java.util.BitSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Wraps the {@code MappedByteBuffer} and create {@link Slice} instances that represent
 * small read-only views on it.
 */
class FileChunk {

  private static final Logger LOGGER = LoggerFactory.getLogger( FileChunk.class ) ;

  private final int sliceMaximumCapacity ;
  private final Consumer< FileChunk > recycler ;

  private final MappedByteBuffer mappedByteBuffer ;
  private final ByteBuf chunkBuffer ;
  private int nextSliceIndexInChunk = 0 ;

  /**
   * Supports CAS, when {@link Status#isInUse()} returns {@code false} we can run
   * {@link #recycle()}.
   */
  private final AtomicReference< Status > sliceAvailability ;

  public FileChunk(
      final FileChannel fileChannel,
      final int probableSliceCount,
      final int sliceMaximumCapacity,
      final Consumer< FileChunk > recycler,
      final long firstByteInFile,
      final long chunkLength
  ) throws IOException {
    checkNotNull( fileChannel ) ;
    this.sliceMaximumCapacity = sliceMaximumCapacity ;
    sliceAvailability = new AtomicReference<>( new Status( probableSliceCount ) ) ;
    this.recycler = checkNotNull( recycler ) ;
    this.mappedByteBuffer = fileChannel.map(
        FileChannel.MapMode.READ_ONLY, firstByteInFile, chunkLength ) ;
    this.chunkBuffer = Unpooled.wrappedBuffer( mappedByteBuffer ) ;
    nextSliceIndexInChunk = 0 ;
  }

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
        nextSliceIndexInChunk,
        chunkBuffer,
        sliceStartInChunk,
        sliceEndInChunk,
        sliceIndexInFile
    ) ;
    sliceAvailability.updateAndGet( bitSet -> bitSet.raise( nextSliceIndexInChunk ) ) ;
    nextSliceIndexInChunk ++ ;
    return slice;
  }

  /**
   * Recycle, if all {@link Slice}s were recycled.
   * <p>
   * Not synchronized, must be called always by the same "slicer" thread.
   */
  public void prepareToRecycle() {
    final Status updated = sliceAvailability.updateAndGet(
        status -> status.chunkInUse( false ) ) ;
    if( ! updated.isInUse() ) {
      recycle() ;
    }
  }

  /**
   * May be called from any thread.
   */
  private void recycle( final Slice slice ) {
    final Status updated = sliceAvailability.updateAndGet(
        status -> status.clear( slice.indexInRecycler ) ) ;
    if( ! updated.isInUse() ) {
      recycle() ;
    }
  }

  /**
   * May be called from any thread.
   * We need some kind of callback to make {@link FileChunk} available again.
   */
  private void recycle() {
    chunkBuffer.release() ;
    closeDirectBuffer( mappedByteBuffer ) ;
    recycler.accept( this ) ;
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
      final int indexInRecycler,
      final ByteBuf wrapped,
      final int readerIndex,
      final int writerIndex,
      final long sliceIndexInFile

  ) {
    final Slice slice = new Slice(
        indexInRecycler,
        sliceMaximumCapacity,
        this::recycle,
        wrapped,
        readerIndex,
        writerIndex,
        sliceIndexInFile
    ) ;
    return slice;
  }

  public int readableBytes() {
    return chunkBuffer.readableBytes() ;
  }

  private static class Status {

    private final boolean chunkInUse ;
    private final BitSet slicesUsage ;

    public Status( final int sliceCount ) {
      chunkInUse = true ;
      this.slicesUsage = new BitSet( sliceCount ) ;
      this.slicesUsage.set( 0, sliceCount - 1, true ) ;
    }

    private Status( final BitSet slicesUsage, final boolean chunkInUse ) {
      this.chunkInUse = chunkInUse ;
      this.slicesUsage = slicesUsage ;
    }

    public Status clear( final int bitIndex ) {
      return set( bitIndex, false ) ;
    }

    public Status raise( final int bitIndex ) {
      return set( bitIndex, true ) ;
    }

    public Status set( final int bitIndex, final boolean value ) {
      final BitSet copy = this.slicesUsage.get( 0, this.slicesUsage.length() ) ;
      copy.set( bitIndex, value ) ;
      return new Status( copy, chunkInUse ) ;
    }

    public Status chunkInUse( final boolean inUse ) {
      return new Status( slicesUsage, inUse ) ;
    }

    private boolean isChunkInUse() {
      return chunkInUse ;
    }

    private boolean allSliceReleased() {
      return slicesUsage.isEmpty() ;
    }

    public boolean isInUse() {
      return isChunkInUse() || ! allSliceReleased() ;
    }
  }

}
