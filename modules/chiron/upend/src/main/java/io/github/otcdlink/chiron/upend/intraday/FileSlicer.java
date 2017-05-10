package io.github.otcdlink.chiron.upend.intraday;

import io.github.otcdlink.chiron.codec.DecodeException;
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
import static com.google.common.base.Preconditions.checkState;

/**
 * Iterates on byte sequences in a {@link File}, which are separated by delimiters.
 * This is typically a text file with a line break at the end of each line.
 * <p>
 * This class performs synchronous IO operations that should happen very fast, and
 * pass each byte sequence to process as a {@link ByteBuf}.
 * See <a href="http://stackoverflow.com/a/9051873/1923328" >Peter Lawrey's advice</a>.
 *
 * <h2>Terminology</h2>
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
 * <h2>How it works</h2>
 * <p>
 * The {@link #toSlices(File)} method maps a portion and goes forward in the byte sequence until
 * it detects a {@link #delimiter}. Then it calls {@link #onSlice(ByteBuf, long)}. When going
 * out of portion boundary, the portion is mapped again.
 * For performance, the {@link ByteBuf} passed to {@link #onSlice(ByteBuf, long)} is a view on the
 * portion reused again and again.
 *
 * <h2>Performance</h2>
 * Hardware: MacBook Pro with Intel Quad Core i7 2.7 GHZ, 16 GB RAM DDR3 1.6 GHz, SSD.
 * The Activity Monitor shows an average reading throughput of 20 MB/s.
 * Test logs says:
 * <pre>
 * Mapped 1,108,888,890 bytes in 2 ms.
 * Processed 10,000,000 lines (1,108,888,890 bytes) in 42,012 ms (238,027 slice/s, 26 MB/s).
 * </pre>
 * The profiler shows we are spending most of the time adjusting the slice {@link ByteBuf}, which
 * probably means we are doing near as fast as we can using idiomatic Netty.
 * The disk throughput is surprisingly low, this is probably because operating system loads only
 * small parts of the file at a time because of {@link ByteBuf#forEachByte(ByteProcessor)}
 * so we are performing lots of synchronous IO.
 * Shrinking portion size to 20 MB slightly diminishes throughput.
 * <p>
 * Some people
 * <a href="http://stackoverflow.com/questions/19486077/java-fastest-way-to-read-through-text-file-with-2-million-lines#comment28902204_19486511" >say</a>
 * that using a {@code FileChannel} only makes a difference for data transfer. But Netty's author
 * <a href="http://stackoverflow.com/a/27592696/1923328">recommends</a> the
 * {@link Unpooled#wrappedBuffer(java.nio.ByteBuffer)} approach.
 */
abstract class FileSlicer {

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
        final long start = System.currentTimeMillis() ;
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
        final long end = System.currentTimeMillis() ;
        LOGGER.debug( "Mapped " + portionLength + " bytes in " + ( end - start ) + " ms." ) ;
        return Unpooled.wrappedBuffer( mappedByteBuffer ) ;
      }

      private void adjustSlice() {
        checkArgument( sliceStart >= portionStart ) ;
        final int sliceOffsetInPortion = ( int ) ( sliceStart - portionStart ) ;
        final int sliceEnd = sliceOffsetInPortion +
            ( int ) ( positionInFile - sliceStart - delimiterLastIndex ) ;
        sliceBytebuf.writerIndex( sliceEnd ) ; // Do that first.
        sliceBytebuf.readerIndex( sliceOffsetInPortion ) ;
      }

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
        checkState( positionInFile - sliceStart < portionMaximumLength,
            "Found a slice bigger than " + portionMaximumLength ) ;
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


}
