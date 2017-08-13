package com.otcdlink.chiron.command;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Experiments to represent a unique identifier for a {@link Command}.
 */
@Ignore( "No test inside" )
public class CommandIdentifierDemo {

  private static final DateTime START_OF_TEST = new DateTime() ;

  static {
    Stamp.raw( 0, 0 ) ;  // Force logging before System.out.println.
  }

  private static Stamp from( final long counter ) {
    return Stamp.raw( START_OF_TEST.getMillis(), counter ) ;
  }

  @Test
  public void fullIdentifier() throws Exception {
    print( Arrays.asList(
        from( 1 ), from( 12 ), from( 123 ), from( 1234 ), from( 98123 ), from( 98124 ) ) ) ;
  }

  private static void print( final Iterable<Stamp> commandIdentifiers ) {
    synchronized( System.class ) {
      for( final Stamp stamp : commandIdentifiers ) {
        System.out.println( String.format(
            "%s:%s",
            TimestampFormatter.SECONDS_FROM_2015_BASE36.apply(
                new DateTime( stamp.timestamp ) ),
            LongFormatter.ENCODER_BASE36.apply( stamp.counter )
        ) ) ;
      }
    }
  }



  @Test
  public void secondsFrom2015() throws Exception {
    print( new DateTime(), TimestampFormatter.SECONDS_FROM_2015_BASE36 ) ;
  }

  private static void print( final DateTime now, final TimestampFormatter formatter ) {
    synchronized( System.class ) {
      System.out.println( "Using " + formatter.name() ) ;
      final long distanceMillis = now.getMillis() - formatter.instant0.getMillis() ;
      System.out.println(
          String.format(
              "Seconds from start: %s    (%d)",
              TimestampFormatter.SECONDS_FROM_2015_BASE36.apply( new DateTime() ),
              distanceMillis
          )
      ) ;
    }
  }


  enum TimestampFormatter implements Function< DateTime, String > {
    SECONDS_FROM_2015_BASE36(
        new DateTime( 2015, 1, 1, 0, 0, DateTimeZone.UTC ),
        ( start, dateTime ) -> {
          final DateTime now = new DateTime( DateTimeZone.UTC ) ;
          assertThat( start.compareTo( now ) < 0 ).describedAs( "Test health" ) ;
          final long differenceMillis = now.getMillis() - start.getMillis() ;
          final long differenceSeconds = differenceMillis / 1000 ;
          return LongFormatter.ENCODER_BASE36.apply( differenceSeconds ) ;
        } ) ;
    private final BiFunction< DateTime, DateTime, String > function ;

    TimestampFormatter(
        final DateTime instant0,
        final BiFunction< DateTime, DateTime, String > function
    ) {
      this.instant0 = instant0 ;
      this.function = function ;
    }

    @Override
    public String apply( final DateTime dateTime ) {
      return function.apply( instant0, dateTime ) ;
    }

    public final DateTime instant0 ;
  }




  /**
   * Padding makes it ugly.
   */
  @Test
  public void longAsBase64() throws Exception {
    print( samples, LongFormatter.ENCODER_BASE36 ) ;
  }

  /**
   * Padding makes it ugly.
   */
  @Test
  public void longAsBase64url() throws Exception {
    print( samples, LongFormatter.ENCODER_BASE64URL ) ;
  }

  /**
   * Having a fixed-length number of bytes gives a fixed-length String.
   */
  @Test
  public void longAsBase64urlNoPadding() throws Exception {
    print( samples, LongFormatter.ENCODER_BASE64URL_NOPADDING ) ;
  }

  @Test
  public void longAsHex() throws Exception {
    print( samples, LongFormatter.ENCODER_HEX ) ;
  }


  enum LongFormatter implements Function< Long, String > {
    ENCODER_BASE64URL( longEncoder( Base64.getUrlEncoder() ) ),
    ENCODER_BASE64URL_NOPADDING( longEncoder( Base64.getUrlEncoder().withoutPadding() ) ),
    ENCODER_HEX( aLong -> String.format( "%x", aLong ) ),
    ENCODER_BASE36( aLong -> Long.toString( aLong, 36 ) ),
    ;

    private final Function< Long, String > function ;

    LongFormatter( final Function< Long, String > function ) {
      this.function = function ;
    }

    @Override
    public String apply( final Long aLong ) {
      return function.apply( aLong ) ;
    }
  }

  private static Function< Long, String > longEncoder( final Base64.Encoder encoder ) {
    return aLong -> {
      final ByteBuffer byteBuffer = ByteBuffer.allocate( 8 ) ;
      byteBuffer.putLong( aLong ) ;
      byteBuffer.flip() ;
      final ByteBuffer encoded = encoder.encode( byteBuffer ) ;
      return new String( encoded.array() ) ;
    } ;
  }


  private static final long[] samples = {
      0L, 1L, 10L, 123L, 1234L, 567890L, 349380438098039L -198987987L } ;

  private static void print( final long[] samples, final LongFormatter stringifier ) {
    synchronized( System.class ) {
      System.out.println( "\nUsing " + stringifier.name() ) ;
      for( final long l : samples ) {
        final String encoded = stringifier.apply( l ) ;
        final String originalUnpadded = Long.toString( l ) ;
        final int originalUnpaddedLength = originalUnpadded.length() ;
        final int encodedLength = encoded.length() ;
        final int shrinking = shrinkingPercentage( originalUnpaddedLength, encodedLength ) ;
        final String formatted = String.format(
            "%16d -> %16s       %2d, %2d (%+d%%)",
            l, encoded,
            originalUnpaddedLength, encodedLength,
            shrinking
        ) ;
        System.out.println( formatted ) ;
      }
    }
  }

  private static int shrinkingPercentage( final float originalLength, final float encodedLength ) {
    return ( int ) ( 1 - ( ( originalLength - encodedLength ) / originalLength ) * 100 ) ;
  }
}