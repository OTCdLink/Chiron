package com.otcdlink.chiron.upend.intraday;

import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ByteProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Iterates on byte sequences in a {@link File}, which are separated by delimiters, and passes
 * them in a {@link ByteBuf}.
 * This is typically a text file with a line break at the end of each line.
 * <p>
 * The {@link ByteBuf} is consistent with deserialisation primitives of a {@link Command}.
 * <p>
 * This class performs synchronous IO operations that should happen very fast, and
 * pass each byte sequence to process as a {@link ByteBuf}.
 * See <a href="http://stackoverflow.com/a/9051873/1923328" >Peter Lawrey's advice</a>.
 *
 * <h1>Terminology</h1>
 * <p>
 * Delimiter: a well-defined sequence of bytes (at least 1).
 * <p>
 * Slice: a sequence of bytes delimited ended by a {@link #delimiter}.
 * A slice and its {@link #delimiter} must be smaller than {@value Integer#MAX_VALUE} =
 * 2147483647 bytes ~= 2 GB. This is because {@link ByteBuf} manipulation functions require
 * some {@code int} parameter.
 * <p>
 * Portion: the memory-mapped portion of a file that fits in a {@link ByteBuf}, big enough
 * to contain at least one whole slice.
 *
 * <h1>How it works</h1>
 * <p>
 * The {@link #toSlices(File)} method maps a portion and goes forward in the byte sequence until
 * it detects a {@link #delimiter}. Then it calls {@link #onSlice(ByteBuf, long)}. When going
 * out of portion boundary, the portion is mapped again.
 * For performance, the {@link ByteBuf} passed to {@link #onSlice(ByteBuf, long)} is a view on the
 * portion reused again and again.
 *
 * <h1>Performance</h1>
 * Hardware: MacBook Pro Retina 2012 with Intel Quad Core i7 2.7 GHZ, 16 GB RAM DDR3 1.6 GHz, SSD.
 * The Activity Monitor shows a reading throughput of 106-131 MB/s.
 * Test logs says (after disabling state checks and logging by commenting them out):
 * <pre>
 * Processed 100000000 lines (11188888890 bytes) in 90174 ms (1108967 slice/s, 124 MB/s).
 * </pre>
 * <p>
 * This figure is a bit disappointing since such an SSD can read about 1 GB/S.
 * <p>
 * Some people
 * <a href="http://stackoverflow.com/questions/19486077/java-fastest-way-to-read-through-text-file-with-2-million-lines#comment28902204_19486511" >say</a>
 * that using a {@code FileChannel} only makes a difference for data transfer. But Netty's author
 * <a href="http://stackoverflow.com/a/27592696/1923328">recommends</a> the
 * {@link Unpooled#wrappedBuffer(java.nio.ByteBuffer)} approach.
 * 
 */
public abstract class FileSlicer {

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( FileSlicer.class ) ;

  /**
   * Default buffer size for mapping a file into memory.
   * It turns out that using a 100 times greater size doesn't affect Replay duration.
   */
  private static final int DEFAULT_PORTION_MAXIMUM_LENGTH = 1024 * 1024 ;

  private final byte[] delimiter ;
  private final int delimiterLastIndex;
  private final int portionMaximumLength ;

  public FileSlicer( final byte[] delimiter ) {
    this( delimiter, DEFAULT_PORTION_MAXIMUM_LENGTH ) ;
  }

  public FileSlicer( final byte[] delimiter, final int portionMaximumLength ) {
    checkArgument( delimiter.length > 0 ) ;
    this.delimiterLastIndex = delimiter.length - 1 ;
    this.delimiter = Arrays.copyOf( delimiter, delimiter.length ) ;
    checkArgument( portionMaximumLength > 0 ) ;
    this.portionMaximumLength = portionMaximumLength ;
  }

  /**
   * @return the number of slices.
   */
  public long toSlices( final File file ) throws IOException {

    @SuppressWarnings( { "IOResourceOpenedButNotSafelyClosed", "ChannelOpenedButNotSafelyClosed" } )
    final FileChannel fileChannel = new RandomAccessFile( file, "r" ).getChannel() ;
    final long fileSize = fileChannel.size() ;

    class ReusableByteProcessor implements ByteProcessor {
      private long positionInFile = 0 ;
      private long portionStart = 0 ;
      private long sliceStart = 0 ;
      private int sliceIndex = 0 ;
      private int positionInDelimiter = -1 ;

      /**
       * Wraps the {@code ByteBuffer} mapping a portion of the file.
       */
      private ByteBuf portionBytebuf = null ;

      /**
       * A "sliding window" on {@link #portionBytebuf}.
       */
      private ByteBuf sliceBytebuf = null ;

      private void mapPortionAndSlice( final long sliceStart ) throws IOException {
        portionBytebuf = mapPortion( sliceStart ) ;
        sliceBytebuf = portionBytebuf.duplicate() ;
      }

      private ByteBuf mapPortion( final long sliceStart ) throws IOException {
        // final long start = System.currentTimeMillis() ;
        checkArgument( sliceStart >= 0 ) ;
        portionStart = sliceStart ;
        final int portionLength ;
        if( sliceStart >= fileSize - portionMaximumLength ) {
          // Last portion, may be smaller than maximum portion size.
          portionLength = ( int ) ( fileSize - sliceStart ) ;
        } else {
          portionLength = ( int ) Math.min( portionMaximumLength, fileSize ) ;
        }
        final MappedByteBuffer mappedByteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY, sliceStart, portionLength ) ;
        // final long end = System.currentTimeMillis() ;
        // Logging causes slowdown.
        // LOGGER.debug( "Mapped " + portionLength + " bytes in " + ( end - start ) + " ms." ) ;
        return Unpooled.wrappedBuffer( mappedByteBuffer ) ;
      }

      private void adjustSlice() {
        checkArgument( sliceStart >= portionStart ) ;
        final int sliceOffsetInPortion = ( int ) ( sliceStart - portionStart ) ;
        final int sliceEnd = sliceOffsetInPortion +
            ( int ) ( positionInFile - sliceStart - delimiterLastIndex ) ;
        sliceBytebuf.setIndex( sliceOffsetInPortion, sliceEnd ) ;
      }

      /**
       * This method is called for each byte so it must execute <em>fast</em>.
       */
      @Override
      public boolean process( final byte byteValue ) throws Exception {
        if( positionInDelimiter == -1 ) {
          if( byteValue == delimiter[ 0 ] ) {
            positionInDelimiter = 0 ;
          }
        } else {
          // Inside a delimiter which is more than 2 byte long.
          if( byteValue != delimiter[ ++ positionInDelimiter ] ) {
            throw new DecodeException( "Inconsistent delimiter " ) ;
          }
        }

        if( positionInDelimiter == delimiterLastIndex ) {
          // Found a complete delimiter.
          positionInDelimiter = -1 ;
          adjustSlice() ;
          onSlice( sliceBytebuf, sliceIndex ++ ) ;
          sliceStart = positionInFile + 1 ;
        }

        positionInFile ++ ;

        /** Don't use {@link com.google.common.base.Preconditions#checkState(boolean, Object)}
         * which would perform string concatenation. Commenting out this check and using the
         * optimized 'if' instead causes throughput to increase by 600 %.
         * Commenting the whole 'if' gives +10 %. */

        // checkState( positionInFile - sliceStart < portionMaximumLength,
        //    "Found a slice bigger than " + portionMaximumLength ) ;

        // if( TESTING && positionInFile - sliceStart >= portionMaximumLength ) {
        //   throw new IllegalStateException( "Found a slice bigger than " + portionMaximumLength ) ;
        // }

        return true ;
      }

      public boolean nextPortion() throws IOException {
        if( positionInFile >= fileSize ) {
          return false ;
        } else {
          mapPortionAndSlice( sliceStart ) ;
          positionInFile = sliceStart ;
          positionInDelimiter = -1 ;
          return true ;
        }
      }
    }

    final ReusableByteProcessor byteProcessor = new ReusableByteProcessor() ;
    while( byteProcessor.nextPortion() ) {
      byteProcessor.portionBytebuf.forEachByte( byteProcessor ) ;
    }

    return byteProcessor.sliceIndex ;
  }


  protected abstract void onSlice( final ByteBuf sliced, long sliceIndex ) throws Exception;


  @SuppressWarnings( "unused" )
  private static final boolean TESTING ;
  static {
    boolean testing = false ;
    try {
      Class.forName( "org.junit.Test" ) ;
      testing = true ;
    } catch( final ClassNotFoundException ignore ) { }
    TESTING = testing ;
    // if( TESTING ) {
    //   LOGGER.warn( "Using " + FileSlicer.class.getSimpleName() +
    //       " with testing enabled, read performance shrunk by (approximatively) 10 %." ) ;
    // }
  }
}
