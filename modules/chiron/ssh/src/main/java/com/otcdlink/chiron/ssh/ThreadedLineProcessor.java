package com.otcdlink.chiron.ssh;

import com.otcdlink.chiron.toolbox.text.Plural;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Each instance of this class has its own threads which perform blocking IOs.
 */
public final class ThreadedLineProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger( ThreadedLineProcessor.class ) ;

  private final InputStream inputStream ;
  private final Thread thread ;
  private volatile boolean stopped ;

  public ThreadedLineProcessor(
      final InputStream inputStream,
      final ThreadFactory threadFactory,
      final Charset charset,
      final Consumer< String > lineConsumer
  ) {
    @SuppressWarnings( "IOResourceOpenedButNotSafelyClosed" )
    final BufferedReader bufferedReader =
        new BufferedReader( new InputStreamReader( inputStream, charset ) ) ;
    this.inputStream = inputStream ;
    this.thread = threadFactory.newThread( () -> readLines( bufferedReader, lineConsumer ) ) ;
  }

  public void start() {
    thread.start() ;
  }

  public void stop() {
    stopped = true ;
    thread.interrupt() ;
    try {
      inputStream.close() ;
    } catch( final IOException ignore ) { }
  }

  private void readLines(
      final BufferedReader bufferedReader,
      final Consumer< String > lineConsumer
  ) {
    long lineCount = 0 ;
    try {
      while( true ) {
        final String line = bufferedReader.readLine() ;
        if( line == null ) {
          break ;
        } else {
          lineCount ++ ;
          if( lineConsumer != null ) {
            try {
              lineConsumer.accept( line ) ;
            } catch( Exception e ) {
              LOGGER.error( "Error while processing line " + lineCount + " '" + line + "'", e ) ;
              throw e ;
            }
          }
        }
        if( Thread.currentThread().isInterrupted() ) {
          LOGGER.info( "Got interrupted, terminating the loop." ) ;
          break ;
        }
      }
    } catch( final Exception e ) {
      if( ! stopped ) {
        LOGGER.error( "Unrecoverable error while reading stream.", e ) ;
      }
      return ;
    } finally {
      try {
        bufferedReader.close() ;
      } catch( final IOException ignore ) { }
    }
    LOGGER.debug( "Finished reading, " + Plural.s( lineCount, "line" ) + " read at all." ) ;
  }

}
