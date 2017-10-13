package com.otcdlink.chiron.flow;

import com.google.common.collect.AbstractIterator;
import com.google.common.util.concurrent.Uninterruptibles;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A quick-and-dirty class for reading lines in a text file, in a way that can be turned to
 * a {@link Flux} using {@link Flux#fromIterable(Iterable)}.
 */
public class FileLineReader {

  private final File file ;

  public FileLineReader( final File file ) throws IOException {
    this.file = checkNotNull( file ) ;
  }

  public Iterable< String > linesAsIterable() {
    final BufferedReader reader ;
    try {
      reader = new BufferedReader( new FileReader( this.file ) ) ;
    } catch( FileNotFoundException e ) {
      throw new RuntimeException( e ) ;
    }
    final Random random = new Random() ;
    return () -> new AbstractIterator< String >() {
      @Override
      protected String computeNext() {
        try {
          final String line = reader.readLine() ;
          if( line == null ) {
            endOfData() ;
            return null ;
          } else {
            Uninterruptibles.sleepUninterruptibly( random.nextInt( 50 ) , TimeUnit.MILLISECONDS )  ;
            return line ;
          }
        } catch( IOException e ) {
          throw new RuntimeException( e ) ;
        }
      }
    } ;
  }

  public Stream< String > linesAsStream() {
    try {
      return new BufferedReader( new FileReader( this.file ) ).lines() ;
    } catch( FileNotFoundException e ) {
      throw new RuntimeException( e ) ;
    }
  }

}
