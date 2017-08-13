package com.otcdlink.chiron.upend.session.implementation;

import com.google.common.base.Stopwatch;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public class DefaultSessionIdentifierGeneratorTest {

  @Test
  public void generateWithDefaults() throws Exception {
    generateAndDisplay( 2_000, 50 ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( DefaultSessionIdentifierGeneratorTest.class ) ;

  private static void generateAndDisplay( final int count, final int displayCount ) {
    checkArgument( displayCount <= count ) ;
    final SessionIdentifier[] generated = new SessionIdentifier[ count ] ;
    final DefaultSessionIdentifierGenerator generator = new DefaultSessionIdentifierGenerator() ;
    final Stopwatch stopwatch = Stopwatch.createStarted() ;
    for( int i = 0 ; i < count ; i ++ ) {
      generated[ i ] = generator.generate() ;
    }
    final long elapsed = stopwatch.elapsed( TimeUnit.MILLISECONDS ) ;

    LOGGER.info( "Generating " + count + " " + SessionIdentifier.class.getSimpleName() +
        " instances took " + elapsed + " ms." ) ;

    final StringBuilder stringBuilder = new StringBuilder() ;
    for( int i = 0 ; i < displayCount ; i ++ ) {
      stringBuilder.append( "  " ).append( generated[ i ].asString() ).append( "\n" ) ;
    }
    LOGGER.info( "Generated: \n" + stringBuilder.toString() ) ;
    LOGGER.info( "Throughput: " + ( count * 1000 / elapsed ) + " instance/s." ) ;
  }

}