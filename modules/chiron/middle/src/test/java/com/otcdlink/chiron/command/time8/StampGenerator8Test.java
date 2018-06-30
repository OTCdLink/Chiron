package com.otcdlink.chiron.command.time8;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class StampGenerator8Test {

  @Test
  public void burst() {
    final int iterationCount = 100 ;
    final Instant[] results = new Instant[ iterationCount ] ;
    final StampGenerator8 stampGenerator8 = new StampGenerator8( Clock.systemUTC() ) ;
    for( int i = 0 ; i < iterationCount ; i ++ ) {
      results[ i ] = stampGenerator8.newInstant() ;
    }
    for( int i = 0 ; i < iterationCount ; i ++ ) {
      LOGGER.info( "Generated [" + i + "]=" + format( results[ i ] ) ) ;
    }

  }

  @Test
  public void concurrency() throws Exception {
    final boolean stress = false ;
    final int threadCount = stress ? Runtime.getRuntime().availableProcessors() : 3 ;
    final int incrementPassCount = stress ? 1_000_000 : 5 ;
    final int totalIdentifierCount = threadCount * incrementPassCount ;

    final StampGenerator8 generator = new StampGenerator8( Clock.systemUTC() ) ;

    class Incrementer implements Runnable {
      Instant[] instants = new Instant[ incrementPassCount ] ;
      long generationDurationMs = 0 ;
      @Override
      public void run() {
        final long start = System.currentTimeMillis() ;
        for( int i = 0 ; i < incrementPassCount ; i ++ ) {
          instants[ i ] = generator.newInstant() ;
        }
        generationDurationMs = System.currentTimeMillis() - start ;
        Arrays.sort( instants ) ; // Help big sort.
      }
    }

    LOGGER.info( "*** Running nanobenchmark with " + totalIdentifierCount + " " +
        Instant.class.getSimpleName() + "s using a pool of " +
        threadCount + " threads. *** "
    ) ;


    final ImmutableList< Incrementer > incrementers ;
    {
      final ExecutorService executorService = Executors.newFixedThreadPool(
          threadCount, ExecutorTools.newThreadFactory( "test-concurrency" ) ) ;
      {
        final ImmutableList.Builder< Incrementer > builder = ImmutableList.builder() ;
        for( int i = 0 ; i < threadCount ; i ++ ) {
          builder.add( new Incrementer() ) ;
        }
        incrementers = builder.build() ;
      }
      incrementers.forEach( executorService::execute ) ;
      executorService.shutdown() ;
      executorService.awaitTermination( 1, TimeUnit.HOURS ) ;
    }

    final Instant[] allIdentifiers = new Instant[ totalIdentifierCount ] ;
    {
      for( int i = 0 ; i < threadCount ; i ++ ) {
        System.arraycopy(
            incrementers.get( i ).instants,
            0,
            allIdentifiers,
            incrementPassCount * i,
            incrementPassCount
        ) ;
      }
      Arrays.sort( allIdentifiers ) ; // Breaks on a null, so it checks everybody's here.
    }

    {
      final long averageGenerationDurationPerThread ;
      {
        long sum = 0 ;
        for( final Incrementer incrementer : incrementers ) {
          sum += incrementer.generationDurationMs ;
        }
        averageGenerationDurationPerThread = ( sum / threadCount ) ;
      }
      LOGGER.info(
          "Average generation time for " + incrementPassCount + " " +
          Instant.class.getSimpleName() + "s is " +
          averageGenerationDurationPerThread + " ms."
      ) ;
      final Long throughputPerSecond = averageGenerationDurationPerThread == 0 ? null :
          1000 * incrementPassCount / averageGenerationDurationPerThread ;
      if( throughputPerSecond != null ) {
        LOGGER.info(
            StampGenerator8.class.getSimpleName() + "'s throughput: " +
                throughputPerSecond + " " +
                Instant.class.getSimpleName() + "/s."
        ) ;
      }
    }

    if( totalIdentifierCount <= 20 ) {
      LOGGER.debug( "Dumping all " + totalIdentifierCount + " " +
          Instant.class.getSimpleName() + "s:" ) ;
      for( final Instant instant : allIdentifiers ) {
        LOGGER.debug( "  " + format( instant ) ) ;
      }
    }

    {
      Instant previous = null ;
      for( final Instant instant : allIdentifiers ) {
        if( previous != null ) {
          assertThat( instant ).isNotEqualTo( previous ) ;
        }
        previous = instant ;
      }
    }
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( StampGenerator8Test.class ) ;

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(
      "uuuu-MM-dd HH:mm:ss X " ) ;

  private static final DecimalFormat NANOSECOND_DECIMAL_FORMAT ;
  static {
    final DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols() ;
    decimalFormatSymbols.setGroupingSeparator( ',' ) ;
    NANOSECOND_DECIMAL_FORMAT = new DecimalFormat( "###,###,##0", decimalFormatSymbols ) ;
  }

  private static String format( final Instant instant ) {
    return DATE_TIME_FORMATTER.format( ZonedDateTime.ofInstant( instant , ZoneOffset.UTC ) ) +
        NANOSECOND_DECIMAL_FORMAT.format( instant.getNano() ) ;
  }

}