package io.github.otcdlink.chiron.command;


import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import io.github.otcdlink.chiron.toolbox.clock.Clock;
import io.github.otcdlink.chiron.toolbox.clock.UpdateableClock;
import io.github.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.otcdlink.chiron.command.Stamp.FLOOR_MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class StampTest {

  @Test( expected = IllegalArgumentException.class )
  public void noNegativeTimestamp() throws Exception {
    Stamp.raw( -1, 0 ) ;
  }

  @Test( expected = IllegalArgumentException.class )
  public void noNegativeCounter() throws Exception {
    Stamp.raw( 0, -1 ) ;
  }

  @Test
  public void zero() throws Exception {
    final Stamp stamp = Stamp.raw( FLOOR_MILLISECONDS, 0 ) ;
    assertThat( stamp.timestamp ).isEqualTo( Stamp.FLOOR_MILLISECONDS ) ;
    assertThat( stamp.counter ).isEqualTo( 0 ) ;
    assertThat( stamp.timestampUtc() )
        .isEqualTo( new DateTime( Stamp.FLOOR_MILLISECONDS, DateTimeZone.UTC ) ) ;
  }

  @Test
  public void floor() throws Exception {
    assertThat( FLOOR_MILLISECONDS ).isEqualTo( 1420070400000L ) ;
  }

  @Test
  public void masks() throws Exception {
    assertThat( Stamp.COUNTER_MEANINGFUL_BITS ).isEqualTo( 12 ) ;
    checkBitwiseEquality( 0b00000000_00000000_00001111_11111111, Stamp.COUNTER_MASK ) ;
  }
  
  @Test
  public void hashing() throws Exception {
    final long timestamp = FLOOR_MILLISECONDS + 1000 ;
    final Stamp stamp = Stamp.raw( timestamp, 2 ) ;
    final int hashCode = stamp.hashCode() ;
    final int expected =
        ( ( ( int ) FLOOR_MILLISECONDS + 1000 ) << Stamp.COUNTER_MEANINGFUL_BITS ) +
        2 // Counter untouched.
    ;

    LOGGER.info(
        "Verifying hashCode " +
            "(" + formatLongAsBinary( stamp.timestamp ) + ":" +
            formatLongAsBinary( stamp.counter ) + ") => " +
            formatIntAsBinary( hashCode ) + "."
    ) ;
    checkBitwiseEquality( expected, hashCode ) ;
  }

  @Test( expected = IllegalArgumentException.class )
  public void enforceFloor() throws Exception {
    Stamp.raw( FLOOR_MILLISECONDS - 1, 0 ) ;
  }

  @Test
  public void stringRepresentation() throws Exception {
    check( FLOOR_MILLISECONDS, 0, "0:0" ) ;
    check( FLOOR_MILLISECONDS + 10, 10, "0:a" ) ;
    check( FLOOR_MILLISECONDS + 1000, 999, "1:rr" ) ;
    check( END_2016.getMillis(), 9999, "11lpbz:7pr" ) ;
  }

  @Test
  public void parsing() throws Exception {
    final Stamp.Parser parser = new Stamp.Parser() ;
    checkParsing( parser, FLOOR_MILLISECONDS, 0 ) ;
    checkParsing( parser, FLOOR_MILLISECONDS + 2000, 10 ) ;
    checkParsing( parser, FLOOR_MILLISECONDS + 1000, 999 ) ;
    checkParsing( parser, END_2016.getMillis(), 9999 ) ;
    checkParsing( parser, END_2016.getMillis(), 10000 ) ;
    checkParsing( parser, END_2016.getMillis(), 10001 ) ;
  }

  @Test
  public void parseWithKnownResult() throws Exception {
    final Stamp expected = Stamp.raw( new DateTime( 2016, 9, 14, 14, 33, 0 ).getMillis(), 0 ) ;
    dump( "Expected", expected ) ;
    final Stamp.Parser parser = new Stamp.Parser() ;
    final Stamp parsed = parser.parse( "w0tj0:0" ) ;
    dump( "Parsed", parsed ) ;

    assertThat( parsed ).isEqualTo( expected ) ;
    assertThat( parsed.flooredSeconds() ).isEqualTo( expected.flooredSeconds() ) ;

    checkEquivalence( parsed, expected ) ;
  }


  @Test
  public void generateWithinSameSecond() throws Exception {
    final UpdateableClock clock = UpdateableClock.newClock( FLOOR_MILLISECONDS ) ;
    final Stamp.Generator generator = new Stamp.Generator( clock ) ;
    check( generator.generate(), FLOOR_MILLISECONDS, 0 ) ;
    check( generator.generate(), FLOOR_MILLISECONDS, 1 ) ;
    check( generator.generate(), FLOOR_MILLISECONDS, 2 ) ;
    clock.increment( 1 ) ;
    check( generator.generate(), FLOOR_MILLISECONDS + 1, 3 ) ;
    clock.increment( 10 ) ;
    check( generator.generate(), FLOOR_MILLISECONDS + 11, 4 ) ;
  }

  @Test
  public void generateAcrossTwoSeconds() throws Exception {
    final UpdateableClock clock = UpdateableClock.newClock( FLOOR_MILLISECONDS ) ;
    final Stamp.Generator generator = new Stamp.Generator( clock ) ;
    check( generator.generate(), FLOOR_MILLISECONDS, 0 ) ;
    check( generator.generate(), FLOOR_MILLISECONDS, 1 ) ;
    check( generator.generate(), FLOOR_MILLISECONDS, 2 ) ;
    clock.increment( 1000 ) ;
    check( generator.generate(), FLOOR_MILLISECONDS + 1000, 0 ) ;
    clock.increment( 10 ) ;
    check( generator.generate(), FLOOR_MILLISECONDS + 1010, 1 ) ;
  }


  @Test
  public void concurrency() throws Exception {
    final boolean stress = false ;
    final int threadCount = stress ? Runtime.getRuntime().availableProcessors() : 3 ;
    final int incrementPassCount = stress ? 1_000_000 : 5 ;
    final int totalIdentifierCount = threadCount * incrementPassCount ;

    final Stamp.Generator generator = new Stamp.Generator( Clock.SYSTEM_CLOCK ) ;

    class Incrementer implements Runnable {
      Stamp[] commandIdentifiers = new Stamp[ incrementPassCount ] ;
      long generationDurationMs = 0 ;
      @Override
      public void run() {
        final long start = System.currentTimeMillis() ;
        for( int i = 0 ; i < incrementPassCount ; i ++ ) {
          commandIdentifiers[ i ] = generator.generate() ;
        }
        generationDurationMs = System.currentTimeMillis() - start ;
        Arrays.sort( commandIdentifiers ) ; // Help big sort.
      }
    }

    LOGGER.info( "*** Running nanobenchmark with " + totalIdentifierCount + " " +
        Stamp.class.getSimpleName() + "s using a pool of " +
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

    final Stamp[] allIdentifiers = new Stamp[ totalIdentifierCount ] ;
    {
      for( int i = 0 ; i < threadCount ; i ++ ) {
        System.arraycopy(
            incrementers.get( i ).commandIdentifiers,
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
          Stamp.class.getSimpleName() + "s is " +
          averageGenerationDurationPerThread + " ms."
      ) ;
      final Long throughputPerSecond = averageGenerationDurationPerThread == 0 ? null : 1000 *
          incrementPassCount / averageGenerationDurationPerThread ;
      if( throughputPerSecond != null ) {
        LOGGER.info(
            Stamp.Generator.class.getSimpleName() + "'s throughput: " +
                throughputPerSecond + " " +
                Stamp.class.getSimpleName() + "/s."
        ) ;
      }
    }

    if( totalIdentifierCount <= 20 ) {
      LOGGER.debug( "Dumping all " + totalIdentifierCount + " " +
          Stamp.class.getSimpleName() + "s:" ) ;
      for( final Stamp stamp : allIdentifiers ) {
        LOGGER.debug( "  " + stamp.asStringRoundedToFlooredSecond() ) ;
      }
    }

    {
      long lastTimestamp = Long.MIN_VALUE ;
      long expectedCounter = Long.MIN_VALUE ;
      for( final Stamp stamp : allIdentifiers ) {
        assertThat( stamp.timestamp ).isGreaterThanOrEqualTo( lastTimestamp ) ;
        if( stamp.timestamp == lastTimestamp ) {
          assertThat( stamp.counter == expectedCounter ) ;
          expectedCounter ++ ;
        } else {
          lastTimestamp = stamp.timestamp ;
          expectedCounter = 0 ;
        }
      }
    }
  }

  @Test
  public void equality() throws Exception {
    final UpdateableClock clock = new UpdateableClock.Default( Stamp.FLOOR_MILLISECONDS ) ;
    final Stamp.Generator generator = new Stamp.Generator( clock ) ;
    final Stamp stamp00 = generator.generate() ;
    final Stamp stamp01 = generator.generate() ;
    clock.increment( 1000 ) ;
    final Stamp stamp10 = generator.generate() ;
    final Stamp stamp11 = generator.generate() ;
    new EqualsTester()
        .addEqualityGroup( stamp00 )
        .addEqualityGroup( stamp01 )
        .addEqualityGroup( stamp10 )
        .addEqualityGroup( stamp11 )
        .testEquals()
    ;
  }



  // =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( Stamp.class ) ;

  private static void check(
      final Stamp stamp,
      final long timestamp,
      final long counter
  ) {
    assertThat( stamp.timestamp ).describedAs( "timestamp" ).isEqualTo( timestamp ) ;
    assertThat( stamp.counter ).describedAs( "counter" ).isEqualTo( counter ) ;
  }

  private static void check( final long timestamp, final long counter, final String asString ) {
    final Stamp stamp = Stamp.raw( timestamp, counter ) ;
    assertThat( stamp.asStringRoundedToFlooredSecond() ).isEqualTo( asString ) ;
    final Matcher matcher = Stamp.REGEX.matcher( asString ) ;
    assertThat( matcher.matches() ) ;
    LOGGER.debug( "'" + stamp.asStringRoundedToFlooredSecond() + "' representing " +
            "'" + DateTimeFormat.forPattern( "YYYY-MM-dd_HH:mm:ss:SSS" ).print( timestamp ) +
            ":" + counter + "' ('" + timestamp + ':' + counter + "', all UTC)."
    ) ;
  }

  private static void checkParsing(
      final Stamp.Parser parser,
      final long timestamp,
      final int counter
  ) {
    final Stamp original = Stamp.raw( timestamp, counter ) ;
    final Stamp parsed = parser.parse( original.asStringRoundedToFlooredSecond() ) ;
    LOGGER.info( "Checking equality between original=" + original +
        " and parsed=" + parsed + " ..." ) ;
    checkEquivalence( original, parsed ) ;
  }

  private static void checkEquivalence( final Stamp original, final Stamp parsed ) {
    final String description = "Original: " + original.timestampUtc() + ", " +
        "parsed: " + parsed.timestampUtc() ;
    assertThat( parsed )
        .describedAs( description )
        .isEqualTo( original )
    ;
    assertThat( parsed.toString() )
        .describedAs( description )
        .isEqualTo( original.toString() )
    ;
    assertThat( parsed.asStringWithRawMilliseconds() )
        .describedAs( description )
        .isEqualTo( original.asStringWithRawMilliseconds() )
    ;
    assertThat( parsed.asStringRoundedToFlooredSecond() )
        .describedAs( description )
        .isEqualTo( original.asStringRoundedToFlooredSecond() )
    ;
  }


  private static final DateTime END_2016 =
      new DateTime( 2016, 12, 31, 23, 59, 59, DateTimeZone.UTC ) ;

  private static String formatLongAsBinary( final long l ) {
    return separateBytes(
        String.format( "%64s", Long.toBinaryString( l ) ).replace( ' ', '0' ) ) ;
  }

  private static String formatIntAsBinary( final int i ) {
    return separateBytes(
        String.format( "%32s", Integer.toBinaryString( i ) ).replace( ' ', '0' ) ) ;
  }

  private static final Pattern EIGHT_BITS_PATTERN = Pattern.compile( "([01]{8}?)" ) ;

  private static String separateBytes( final String bitsAsString ) {
    return EIGHT_BITS_PATTERN.matcher( bitsAsString ).replaceAll( ".$1" ).substring( 1 ) ;
  }

  private static void checkBitwiseEquality( final int expected, final int actual ) {
    assertThat( actual )
        .as( "\nExpected: " + formatIntAsBinary( expected ) +
            "\n  Actual: " + formatIntAsBinary( actual ) + "\n" )
        .isEqualTo( expected )
    ;
  }

  private static void dump( final String title, final Stamp stamp ) {
    LOGGER.info(
        title + ": " + stamp + "\n" +
        "  flooredSeconds=" + stamp.flooredSeconds() + "\n" +
        "  rawMillis=" + stamp.timestamp + "\n" +
        "  rawMillis to DateTime=" + new DateTime( stamp.timestamp, DateTimeZone.UTC )
    ) ;
  }

}