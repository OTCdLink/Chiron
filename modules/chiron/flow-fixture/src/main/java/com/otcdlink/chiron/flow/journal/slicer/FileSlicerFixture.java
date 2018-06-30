package com.otcdlink.chiron.flow.journal.slicer;

import com.google.common.base.Charsets;
import com.google.common.collect.AbstractIterator;
import com.otcdlink.chiron.toolbox.SafeSystemProperty;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import com.otcdlink.chiron.toolbox.text.ByteSize;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import com.otcdlink.chiron.toolbox.text.Plural;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

public class FileSlicerFixture {

  private static final Logger LOGGER = LoggerFactory.getLogger( FileSlicerFixture.class ) ;

  public static Scheduler scheduler( final String name ) {
    return Schedulers.fromExecutor(
        Executors.newSingleThreadExecutor( ExecutorTools.newThreadFactory( name ) ) ) ;
  }

  public static void check(
      final File testDirectory,
      final int chunkMaximumLength,
      final int sliceMaximumLength,
      final LineBreak lineBreak,
      final boolean lineBreakAtEnd,
      final long sliceCount,
      final Iterable< String > expectedContent,
      final boolean deepAssert
  ) throws IOException, InterruptedException {
    final Logger LOGGER = LoggerFactory.getLogger( FileSlicerFixture.class ) ;

    final File file = new File( testDirectory, "file" + sliceCount + ".txt" ) ;
    final long fileLength ;

    if( file.exists() ) {
      fileLength = file.length() ;
      LOGGER.info( "Reusing file '" + file.getAbsolutePath() + "' with a size of " +
          Plural.s( fileLength, "byte" ) + "." ) ;
    } else {
      LOGGER.info( "Creating file '" + file + " ..." ) ;
      try( final FileOutputStream fileOutputStream = new FileOutputStream( file ) ;
           final BufferedOutputStream bufferedOutputStream =
               new BufferedOutputStream( fileOutputStream )
      ) {
        final byte[] lineBreakBytes = lineBreak.asByteArray() ;
        final Iterator< String> expectedContentIterator = expectedContent.iterator() ;
        while( expectedContentIterator.hasNext() ) {
          final String slice = expectedContentIterator.next() ;
          bufferedOutputStream.write( slice.getBytes( Charsets.US_ASCII ) ) ;
          if( expectedContentIterator.hasNext() || lineBreakAtEnd ) {
            bufferedOutputStream.write( lineBreakBytes ) ;
          }
        }
      }
      fileLength = file.length() ;
      LOGGER.info( "Created a file with a size of " + Plural.s( fileLength, "byte" ) + "." ) ;
    }


    final boolean dropCacheHappened = dropFilesystemCache() ;

    final long start = System.currentTimeMillis() ;
    final AtomicLong lineCounter = new AtomicLong() ;
    final Iterator< String > expectedContentIterator = expectedContent.iterator() ;



    final FileSlicer fileSlicer = new FileSlicer(
        file, lineBreak.asByteArray(), chunkMaximumLength, sliceMaximumLength, 100 ) ;
    final Semaphore lastSliceReached = new Semaphore( 0 ) ;
    Flux.fromIterable( fileSlicer )
        .map( slice -> {
          final long expectedSliceIndex = lineCounter.getAndIncrement() ;
          final long sliceIndexInFile = slice.sliceIndexInFile ;
          if( deepAssert ) {
            LOGGER.info( "Got at line " + slice.lineIndexInFile() + ": " + slice +
                "\n" + ByteBufUtil.prettyHexDump( slice ) ) ;
            assertThat( slice.toString( Charsets.US_ASCII ) )
                .isEqualTo( expectedContentIterator.next() ) ;
            assertThat( sliceIndexInFile ).isEqualTo( expectedSliceIndex ) ;
            slice.forEachByte( b -> true ) ;  // Used to crash.
          }
          if( sliceIndexInFile == sliceCount - 1 ) {
            lastSliceReached.release() ;
          }
          return slice ;
        } )
        .buffer( FileSlicer.DEFAULT_FLUX_BUFFER_SIZE )  // Less inter-thread communication.
        .publishOn( scheduler( "recycle" ) )
        .subscribe()
    ;

    if( sliceCount > 0 ) {
      lastSliceReached.acquire() ;
    }
    final long end = System.currentTimeMillis() ;

    logThroughputMeasurement(
        LOGGER, start, end, fileLength, "line", lineCounter.get(), dropCacheHappened ) ;
  }

  public static void logThroughputMeasurement(
      final Logger logger,
      final long start,
      final long end,
      final long fileLength,
      final String itemName,
      final long itemCount,
      final boolean dropCacheHappened
  ) {
    final long duration = end - start + 1 ;
    if( duration > 0 ) {
      logger.info(
          "Processed " + Plural.s( itemCount, itemName ) + " " +
          "(" + Plural.s( fileLength, "byte" ) + ") " +
          "in " + duration + " ms (" +
          ( ( 1000 * itemCount ) / duration ) + " " + itemName + "/s, " +
          ByteSize.humanReadableByteCount( 1000 * fileLength / duration, true ) + "/s" +
          ")."
      ) ;
      if( ! dropCacheHappened ) {
        logger.info( "(These figures may be inaccurate if the written file was cached.)" ) ;
      }
    } else {
      logger.info( "Duration too small for calculating stats." ) ;
    }
  }

  /**
   * Avoids reading something that is still in OS' filesystem cache.
   *
   * @return {@code} true if the command for dropping ran successfully (so we can reasonably
   *     <em>suppose</em> it happened).
   */
  public static boolean dropFilesystemCache() {
    boolean dropHappened = false ;
    if( SafeSystemProperty.Standard.OS_NAME.value.contains( "Linux" ) &&
        SafeSystemProperty.Standard.USER_NAME.value.equals( "root" )
    ) {
      try {
        new ProcessBuilder( "/bin/sh", "-c", "\"sync && echo 3 > /proc/sys/vm/drop_caches\"" )
            .start().waitFor() ;
        LOGGER.info( "Successfully dropped filesystem cache." ) ;
        dropHappened = true ;
      } catch( InterruptedException | IOException e ) {
        LOGGER.warn( "Failed to drop filesystem cache. Cause: " +
            e.getClass().getName() + ", " + e.getMessage() ) ;
      }
    }
    return dropHappened ;
  }

  public static void check(
      final File directory,
      final int chunkMaximumLength,
      final int sliceMaximumLength,
      final LineBreak lineBreak,
      boolean lineBreakAtEnd,
      final long sliceCount
  ) throws IOException, InterruptedException {
    check(
        directory,
        chunkMaximumLength,
        sliceMaximumLength,
        lineBreak,
        lineBreakAtEnd,
        sliceCount,
        () -> new StringGenerator( sliceCount ),
        false
    ) ;
  }

  private static class StringGenerator extends AbstractIterator< String > {

    private final long total ;

    private StringGenerator( final long total ) {
      this.total = total ;
    }

    private long index = 0 ;

    @Override
    protected String computeNext() {
      if( index >= total ) {
        return endOfData() ;
      } else {
        return "This string is about 100 bytes long, this is a rough approximation of the size " +
            "of a Proposal Command - " + ( index ++ ) ;
      }
    }
  }
}
