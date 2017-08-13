package com.otcdlink.chiron.upend.intraday;

import com.google.common.io.LineProcessor;
import io.netty.buffer.ByteBuf;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.channels.FileChannel.MapMode;

public final class FileTools {

  private static final Logger LOGGER = LoggerFactory.getLogger( FileTools.class ) ;

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern(
      "yyyy-MM-dd_HH.mm.ss" ) ;
  private FileTools() { }

  public static File rename(
      final File originalFile,
      final DateTime timestamp,
      final String newSuffix
  ) {
    final File renamed = newName( originalFile, timestamp, newSuffix ) ;
    doRename( originalFile, renamed ) ;
    return renamed ;
  }

  public static File newName(
      final File originalFile,
      final DateTime timestamp,
      final String newSuffix
  ) {
    return newName(
        originalFile,
        timestamp == null ? "" : '.' + DATE_TIME_FORMATTER.print( timestamp ),
        newSuffix
    ) ;
  }

  private static File newName(
      final File originalFile,
      final String formattedTimestamp,
      final String newSuffix
  ) {
    final String oldName = originalFile.getName() ;
    final int lastDot = oldName.lastIndexOf( '.' ) ;
    final String newName ;
    if( lastDot >= 0 ) {
      newName = oldName.substring( 0, lastDot ) + formattedTimestamp + '.' + newSuffix ;
    } else {
      newName = oldName + formattedTimestamp + '.' + newSuffix ;
    }
    return new File( originalFile.getParent(), newName ) ;
  }



  private static void doRename( final File originalFile, final File renamed ) {
    if( ! originalFile.renameTo( renamed ) ) {
      LOGGER.warn( "Couldn't rename '" + originalFile.getAbsolutePath() + "' to "
          + "'" + renamed + "'." ) ;
    }
  }

  /**
   * Read lines in {@code java.nio.charset.StandardCharsets#US_ASCII} (aka ASCII 7 bits)
   * and processes them in parallel.
   *
   * @deprecated we should use a {@link ByteBuf} somehow.
   */
  public static void readLinesAscii7(
      final File file,
      final LineProcessor< Void > lineProcessor,
      final ThreadFactory threadFactory
  ) throws Exception {

    final BlockingQueue< String > lineQueue = new ArrayBlockingQueue<>( 1_000 ) ;
    final Semaphore completionSemaphore = new Semaphore( 0 ) ;
    final AtomicReference< Exception > lineProcessingException = new AtomicReference<>() ;

    threadFactory.newThread( new Runnable() {
      @Override
      public void run() {
        while( true ) {
          try {
            final String line = lineQueue.take() ;
            if( isMagicForNoMoreLines( line ) ) {
              completionSemaphore.release() ;
              break ;
            } else {
              lineProcessor.processLine( line ) ;
            }
          } catch( InterruptedException | IOException e ) {
            lineProcessingException.compareAndSet( null, e ) ;
            break ;
          }
        }
      }
    } ).start() ;

    final StringBuilder lineBuilder = new StringBuilder( 100 ) ;
    try(
        final RandomAccessFile randomAccessFile = new RandomAccessFile( file, "r" ) ;
        final FileChannel channel = randomAccessFile.getChannel()
    ) {

      MappedByteBuffer buffer = channel.map(
          MapMode.READ_ONLY, 0, Math.min( channel.size(), Integer.MAX_VALUE ) ) ;

      for( long i = 0 ; i < file.length() ; i++ ) {
        if( ! buffer.hasRemaining() ) {
          buffer = channel.map(
              MapMode.READ_ONLY, i, Math.min( channel.size() - i, Integer.MAX_VALUE ) ) ;
        }
        final byte b = buffer.get() ;
        if( b == '\r' || b == '\n' ) {
          if( lineBuilder.length() > 0 ) {
            final String line = lineBuilder.toString() ;
            lineQueue.put( line ) ;
            lineBuilder.setLength( 0 ) ;
          }
        } else {
          lineBuilder.append( ( char ) b ) ;
        }
        throwExceptionIfThereIsOne( lineProcessingException ) ;
      }
    }
    lineQueue.put( MAGIC_NO_MORE_LINES ) ;
    completionSemaphore.acquire() ;
    throwExceptionIfThereIsOne( lineProcessingException ) ;
  }

  @SuppressWarnings( "RedundantStringConstructorCall" )
  private static final String MAGIC_NO_MORE_LINES = new String( "MAGIC_NO_MORE_LINES" ) ;

  @SuppressWarnings( "StringEquality" )
  protected static boolean isMagicForNoMoreLines( final String line ) {
    return line == MAGIC_NO_MORE_LINES ;
  }

  @SuppressWarnings( "ThrowableResultOfMethodCallIgnored" )
  private static void throwExceptionIfThereIsOne(
      final AtomicReference< Exception > exceptionAtomicReference
  ) throws Exception {
    if( exceptionAtomicReference.get() != null ) {
      throw exceptionAtomicReference.get() ;
    }
  }

}
