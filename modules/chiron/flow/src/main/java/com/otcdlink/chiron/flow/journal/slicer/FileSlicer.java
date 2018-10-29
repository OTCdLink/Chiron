package com.otcdlink.chiron.flow.journal.slicer;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import io.netty.buffer.ByteBuf;
import io.netty.util.ByteProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Reads sequence of bytes in a file, separated by a (possibly multibyte) delimiter.
 * Each sequence of byte is named a Slice and comes wrapped into a {@link Slice} that has
 * a maximum size.
 * <p>
 * A {@link Slice} has a maximum size, set during object construction. A good default value
 * is defined in {@link #DEFAULT_SLICE_MAXIMUM_LENGTH}, it yields the best
 * performance, while supporting rather long {@link Slice} (about 500 kB).
 *
 * <h1>Interoperability</h1>
 * <p>
 * This class is meant to support {@link Flux#fromIterable(Iterable)} but it has no dependency
 * on Reactor stuff.
 * <p>
 * Pipelining processings in multiple threads (with something like
 * {@link Flux#publishOn(Scheduler)}) seems to have a negative performance impact.
 * Use {@link Flux#buffer(int)} to reduce the cost of the inter-thread throughput.
 * <p>
 * This is the correct approach:
 * <pre>
 *   Flux.fromIterable( fileSlicer )
 *     .map( slice -&gt; { ... ; return slice ; }
 *     .buffer( FileSlicer#DEFAULT_FLUX_BUFFER_SIZE )
 *     .publishOn( scheduler )  // Switch to another thread.
 *     .subscribe()
 *   ;
 * </pre>
 *
 * <h1>How it works</h1>
 * <p>
 * The {@link FileSlicer} maps parts of the file in a {@code MappedByteBuffer}.
 * into one or more {@link FileChunk}.
 * <p>
 * {@code MappedByteBuffer} <a href="http://stackoverflow.com/a/9051873/1923328" >is the fastest</a>
 * according to Peter Lawrey, the author of Chronicle Framework.
 * <p>
 * Reading the file is about consuming bytes in the buffer, to detect delimiters. Each
 * delimiter triggers the creation of a {@link Slice} (which subclasses {@link ByteBuf}).
 * <p>
 * A {@link FileChunk} has a maximum capacity (as {@code MappedByteBuffer} has).
 * If the end of the {@link FileChunk} is hit before the end of the file, then another
 * {@link FileChunk} is created.
 * If the end of the {@link FileChunk} is hit before the end of a delimiter, then the next created
 * {@link FileChunk} rewinds to the start position of the {@link Slice} that was left
 * uncompletely read, and reads it again.
 *
 * <h1>Resource freeing</h1>
 * <p>
 * Instances of {@code java.nio.MappedByteBuffer} are handled by the GC, as the Javadoc suggests.
 * There has been attempts to reference-count {@link Slice} instances with a {@code BitSet} in a
 * thread-safe structure but this got things just slower. This also seemed to cause a random bug
 * when reading big files on production machine: sometimes a {@link Slice} ended before
 * the delimiter.
 *
 * <h1>Performance</h1>
 *
 * <h2>Java version</h2>
 *
 * <pre>
 java version "1.8.0_131"
 Java(TM) SE Runtime Environment (build 1.8.0_131-b11)
 Java HotSpot(TM) 64-Bit Server VM (build 25.131-b11, mixed mode)
   </pre>

 * <h2>Laptop</h2>
 * <p>
 * Hardware: MacBook Pro Retina 2012 with Intel Quad Core i7 2.7 GHZ, 16 GB RAM DDR3 1.6 GHz, SSD.
 * <pre>
Processed 29000000 lines (3236888890 bytes) in 23302 ms (1244528 slice/s, 138 MB/s).
 * </pre>
 * <p>
 * The results vary a lot depending on the size of the file read, but this is probably because
 * of filesystem cache.
 *
 * <h2>OVH Infrastructure server</h2>
 * <p>
 * Hardware: OVH server, 8 x Intel(R) Xeon(R) CPU E5-1620 0 @ 3.60GHz, 32 GB RAM, rotating drive,
 * software RAID.
 * <p>
 * Raw read speed calculated with system tools claims 130-160 MB/s, sound very few.
 * I/O gauges are probably broken somehow, because {@code iotop} doesn't show read activity when
 * accessing to a 11 GB file, and tests of {@link FileSlicer} show higher throughput.
 * <p>
 * {@link FileSlicer} obtains sustained read speed of ~300 MB/s
 * (from 29 M to 100 M lines of 100 bytes each).
 * <pre>
Processed 100000000 lines (11188888890 bytes) in 36216 ms (2761210 slice/s, 308 MB/s).
   </pre>
 *
 * <h2>Gathered system characteristics</h2>
 * <p>
 * This is what we get on one of our Linux servers.
 * <pre>
# more /proc/cpuinfo
 model name	: Intel(R) Xeon(R) CPU E5-1620 0 @ 3.60GHz
 cpu MHz    : 1199.926
 cache size	: 10240 KB
 ...
   </pre>
 * and
 * <pre>
# hdparm -Tt /dev/mapper/vg-vault

 /dev/mapper/vg-vault:
 Timing cached reads:   21672 MB in  2.00 seconds = 10844.80 MB/sec
 Timing buffered disk reads: 398 MB in  3.00 seconds = 132.48 MB/sec
 </pre>
 * or
 * <pre>
# dd if=/dev/zero of=testfile bs=100k count=1k &amp;&amp; sync
# sh -c "sync &amp;&amp; echo 3 &gt; /proc/sys/vm/drop_caches"
# dd if=testfile of=/dev/null bs=100
1048576+0 records in
1048576+0 records out
104857600 bytes (105 MB, 100 MiB) copied, 0.656948 s, 160 MB/s
 * </pre>
 *
 */
public class FileSlicer implements Iterable< Slice > {

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( FileSlicer.class ) ;

  /**
   * The maximum size of the {@code MappedByteBuffer} to map a {@link FileChunk} onto.
   * Yields the best performance results (greater values cause smaller throughput).
   */
  public static final int DEFAULT_CHUNK_MAXIMUM_LENGTH = 1024 * 1024 ;

  /**
   * Decent value for buffering with {@link Flux#buffer(int)} when the intent is to reduce
   * inter-thread communication in batch mode (buffering is not suitable for interactive/online
   * use).
   */
  public static final int DEFAULT_FLUX_BUFFER_SIZE = 100_000 ;

  public static final LineBreak DEFAULT_LINE_BREAK = LineBreak.CR_UNIX ;

  /**
   * A decent value to pass to the constructor for defining the maximum length of a
   * {@link Slice}. It relies on {@link #DEFAULT_CHUNK_MAXIMUM_LENGTH} while
   * allowing {@link Slice} to be as long as possible.
   */
  public static final int DEFAULT_SLICE_MAXIMUM_LENGTH =
      ( DEFAULT_CHUNK_MAXIMUM_LENGTH - ( DEFAULT_LINE_BREAK.asByteArray().length + 1 ) ) / 2 ;

  public static int defaultProbableSliceCountPerChunk(
      final int chunkMaximumLength,
      final int sliceProbableLength
  ) {
    final int max = Math.max( 100, 10 * chunkMaximumLength / sliceProbableLength ) ;
    return max ;
  }

  private final File file ;
  private final FileChannel fileChannel ;
  private final long fileLength ;
  private final byte[] delimiter ;
  private final int delimiterLastIndex ;
  private final int chunkMaximumLength ;
  private final int probableSliceCount ;

  private final int sliceMaximumLength ;

  public FileSlicer(
      final File file,
      final byte[] delimiter,
      final int chunkMaximumLength,
      final int sliceMaximumLength,
      final int probableSliceCount
  ) throws FileNotFoundException {
    this.file = checkNotNull( file ) ;
    this.fileChannel = new RandomAccessFile( file, "r" ).getChannel() ;
    this.fileLength = file.length() ;
    checkArgument( delimiter.length > 0 ) ;
    this.delimiterLastIndex = delimiter.length - 1 ;
    this.delimiter = Arrays.copyOf( delimiter, delimiter.length ) ;

    checkArgument(
        chunkMaximumLength >= 2 * ( sliceMaximumLength + delimiter.length ),
        "Need chunkMaximumLength (currently " + chunkMaximumLength + ") to be more than " +
        "[ 2 x [ sliceMaximumCapacity + delimiter size ] ] (currently " + sliceMaximumLength +
        " + " + delimiter.length + ") to rewind correctly if a Slice or its delimiter " +
        "splits over two Chunks"
    ) ;
    checkArgument( chunkMaximumLength > 0 ) ;
    this.chunkMaximumLength = chunkMaximumLength ;
    checkArgument( sliceMaximumLength > 0 ) ;
    this.sliceMaximumLength = sliceMaximumLength;

    checkArgument( probableSliceCount >= 0 ) ;
    this.probableSliceCount = probableSliceCount ;

  }

  // @Override
  // public String toString() {
  //   return getClass().getSimpleName() + '{' + file.getAbsolutePath() + '}' ;
  // }

// =======
// Pooling
// =======


  private FileChunk newFileChunk(
      final long positionOfFirstByteToReadInFile,
      final long chunkLength
  ) throws IOException {
    final FileChunk fileChunk = new FileChunk(
        fileChannel,
        probableSliceCount,
        sliceMaximumLength,
        positionOfFirstByteToReadInFile,
        chunkLength
    ) ;
    LOGGER.debug( "Created " + fileChunk + "." ) ;
    return fileChunk ;
  }


// ========
// Iterable
// ========

  @Nonnull
  @Override
  public Iterator< Slice > iterator() {

    if( fileLength == 0 ) {
      return ImmutableList.<Slice>of().iterator() ;
    } else {
      return new SliceIterator() ;
    }
  }

  /**
   * Contains all the "moving parts" of a {@link FileSlicer}.
   */
  private class SliceIterator extends AbstractIterator< Slice > {

    /**
     * The last byte processed by {@link #byteProcessor} or -1 before first processing.
     */
    private long positionOfLastByteReadInFile = -1 ;

    /**
     * Offset of the first byte in {@link FileChunk}'s {@link ByteBuf} to become a
     * {@link Slice}, updated when starting {@link #delimiter} detection.
     */
    private int sliceStartInChunk = 0 ;

    /**
     * Offset of the last byte in {@link FileChunk} that will become part of {@link Slice}
     * (which means the byte before end of file or {@link #delimiter}).
     * This field is updated by {@link #byteProcessor}.
     */
    private int sliceEndInChunk = 0 ;

    private long positionInFileOfLastByteOfChunk = -1 ;

    /**
     * For debug.
     */
    private int sliceIndexInFile = 0 ;

    /**
     * Like {@link #sliceStartInChunk} but offset is file-based.
     */
    private long lastSliceStartInFile = 0 ;

    /**
     * Offset of the last byte to consumed by {@link #byteProcessor}, relative to
     * {@link FileChunk}'s {@link ByteBuf}.
     * After detecting a complete {@link Slice} it contains the offset of the last byte
     * of the {@link #delimiter}.
     */
    private int lastByteReadInChunk = 0 ;

    private int positionInDelimiter = -1 ;

    private FileChunk fileChunk = null ;

    private Slice iteratorNextResult = null ;

    private boolean foundDelimiter = false ;

    @Override
    protected Slice computeNext() {

      while( true ) {
        /** Looping allows to retry reading a {@link Slice} after last one, if
         * {@link #byteProcessor} did not detect the {@link #delimiter}. */
        try {
          final long positionOfFirstByteToReadInFile = positionOfLastByteReadInFile + 1 ;
          if( fileChunk == null ) {
            if( positionOfFirstByteToReadInFile == fileLength ) {
              /** This can happen after hitting last {@link Slice} (with no {@link #delimiter
               * after it), or with a zero-length file. */
              endOfData() ;
              return null ;
            } else {
              final long chunkLength = Math.min(
                  chunkMaximumLength, fileLength - positionOfFirstByteToReadInFile ) ;
              fileChunk = newFileChunk( positionOfFirstByteToReadInFile, chunkLength ) ;
              lastByteReadInChunk = -1 ;
              sliceStartInChunk = 0 ;
              positionInDelimiter = -1 ;
              lastSliceStartInFile = positionOfFirstByteToReadInFile ;
              positionInFileOfLastByteOfChunk =
                  positionOfLastByteReadInFile + fileChunk.readableBytes() ;
            }
          }

          /** Read until we hit a complete {@link #delimiter} or the end. */
          foundDelimiter = false ;
          sliceEndInChunk = sliceStartInChunk - 1 ;
          if( lastByteReadInChunk + 1 < fileChunk.readableBytes() ) {
            lastSliceStartInFile = positionOfFirstByteToReadInFile ;
            fileChunk.forEachByte( lastByteReadInChunk + 1, byteProcessor ) ;
          } else {
            fileChunk = null ;  // Force recreation.
            continue ;
          }

          if( foundDelimiter ) {
            iteratorNextResult = fileChunk.newSlice(
                sliceIndexInFile ++, sliceStartInChunk, sliceEndInChunk + 1 ) ;
            sliceStartInChunk = sliceEndInChunk + delimiter.length + 1 ;
            return iteratorNextResult ;
          } else {
            if( positionOfLastByteReadInFile == fileLength - 1 ) {
              /** No {@link #delimiter} but end of file. */
              if( sliceEndInChunk >= 0 ) {
                iteratorNextResult = fileChunk.newSlice(
                    sliceIndexInFile ++, sliceStartInChunk, sliceEndInChunk + 1 ) ;
                return iteratorNextResult ;
              } else {
                fileChunk = null ;
                return endOfData() ;
              }
            } else {
              fileChunk = null ;
              /** Force next {@link FileChunk} to start where incomplete {@link Slice}
               * ended. */
              positionOfLastByteReadInFile = lastSliceStartInFile - 1 ;
            }
            // TODO: detect a multibyte delimiter with only a part of it.
          }
        } catch( IOException e ) {
          throw new RuntimeException( e ) ;
        }
      }
    }

    /**
     * Netty says {@link ByteProcessor} is faster than plain loop.
     */
    private final ByteProcessor byteProcessor = byteValue -> {
      if( positionInDelimiter == -1 ) {
        if( byteValue == delimiter[ 0 ] ) {
          positionInDelimiter = 0 ;
        } else {
          sliceEndInChunk ++ ;
        }
      } else {
        // Inside a delimiter which is more than 2 byte long.
        if( byteValue != delimiter[ ++ positionInDelimiter ] ) {
          if( positionOfLastByteReadInFile + 1 >= positionInFileOfLastByteOfChunk ) {
            /** We hit the end of {@link #fileChunk} so we have to rewind. */
            return false ;
          } else {
            throw new DecodeException( "Inconsistent delimiter " ) ;
          }
        }
      }

      positionOfLastByteReadInFile++ ;
      lastByteReadInChunk ++ ;

      if( positionInDelimiter == delimiterLastIndex ) {
        // Found a complete delimiter.
        positionInDelimiter = -1 ;
        foundDelimiter = true ;
        return false ; // End of byte-processing loop.
      } else {
        return true ;
      }
    } ;
  }
}
