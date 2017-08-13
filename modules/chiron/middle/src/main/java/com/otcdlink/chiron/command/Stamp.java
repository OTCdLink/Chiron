package com.otcdlink.chiron.command;

import com.google.common.base.Joiner;
import com.otcdlink.chiron.toolbox.ComparatorTools;
import com.otcdlink.chiron.toolbox.clock.Clock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Uniquely identifies a {@link Command} with a timestamp, and a counter to discriminate
 * {@link Stamp}s with the same timestamp.
 * The {@link #timestamp} represents an instant in the time, as returned by
 * {@link DateTime#getMillis()} with UTC Time Zone.
 * The {@link #counter} is a positive number.
 *
 * <h1>Compression</h1>
 * <p>
 * The {@link Generator} generates {@link Stamp}s shaving milliseconds, and substracting
 * {@link #FLOOR_MILLISECONDS} which is a date before which there should be no {@link Stamp}.
 * As a result, the {@link #flooredSeconds()} method gives consistent results for {@link Stamp}
 * objects created from a single {@link Generator}, and deserialized {@link Stamp} objects reading
 * the result of {@link #asStringRoundedToFlooredSecond()}.
 */
public class Stamp implements Comparable< Stamp > {

  private static final Logger LOGGER = LoggerFactory.getLogger( Stamp.class ) ;

  /**
   * By substracting {@link #FLOOR_MILLISECONDS} from {@link #timestamp} we can reduce
   * the number of character it takes to display in {@link #asStringRoundedToFlooredSecond()}.
   * This value must not change, or {@link Stamp}s in logs would show inconsistently.
   * Unless some mindless change occured, calculated value is {@code 1420070400000}.
   */
  public final static long FLOOR_MILLISECONDS ;

  public final static DateTime FLOOR ;

  /**
   * For debug, {@code toString()}, and stuff like that.
   */
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormat.forPattern( "YYYY-MM-dd_HH:mm:ss:SSS" ).withZoneUTC() ;

  static {
    final DateTime floor = new DateTime( 2015, 1, 1, 0, 0, DateTimeZone.UTC ) ;
    FLOOR_MILLISECONDS = ( floor.getMillis() / 1000 ) * 1000 ;
    FLOOR = floor ;
    LOGGER.info(
        "Initialized FLOOR to " +
        DATE_TIME_FORMATTER.print( FLOOR ) +
        " (" + FLOOR_MILLISECONDS + " ms in 1970-based time)."
    ) ;
  }

  /**
   * The value returned by {@link DateTime#getMillis()} for UTC Time Zone.
   * It does not depend on {@link #FLOOR_MILLISECONDS} in any way.
   */
  final long timestamp ;

  /**
   * A value to discriminate {@link Stamp}s created within the same millisecond (so they have
   * the same {@link #timestamp}. A {@link Stamp.Generator} enforces such consistency; for this
   * reason there can be only one {@link Stamp.Generator} for the whole application.
   */
  final long counter ;

  /**
   * Use {@link Stamp.Generator} for getting consistent {@link #counter} sequences
   * within the same second.
   *
   * @param timestamp may be before {@link #FLOOR_MILLISECONDS} so we'll have negative timestamp.
   *     This avoids breaking tests.
   */
  private Stamp( final long timestamp, final long counter ) {
    checkArgument(
        timestamp >= FLOOR_MILLISECONDS,
        "Unfloored timestamp: " + timestamp +
            " (" + DATE_TIME_FORMATTER.print( timestamp ) + "), " +
            "floor is " + FLOOR_MILLISECONDS +
            " (" + DATE_TIME_FORMATTER.print( FLOOR_MILLISECONDS ) + ")"
    ) ;
    checkArgument( counter >= 0, "Bad counter: " + counter + " (timestamp: " + timestamp + ")" ) ;
    this.timestamp = timestamp ;
    this.counter = counter ;
  }

  public static Stamp raw( final long timestamp, final long counter ) {
    return new Stamp( timestamp, counter ) ;
  }


// =======
// Hashing
// =======

  /**
   * Keep the least significant bytes of {@link #timestamp} and {@link #counter} because those
   * vary the most often, offering best dispersion.
   * A value of 12 for {@link #COUNTER_MEANINGFUL_BITS} means keeping {@link #counter}
   * as it is up to 2^12 = 4096.
   */
  @Override
  public int hashCode() {
    int result = ( int ) ( timestamp << COUNTER_MEANINGFUL_BITS ) ;
    result |= ( int ) ( counter & COUNTER_MASK ) ;
    return result ;
  }

  static final int COUNTER_MEANINGFUL_BITS = 12 ;
  static final int COUNTER_MASK ;
  static {
    int mask = 0 ;
    for( int weight = 1 ; weight <= COUNTER_MEANINGFUL_BITS ; weight ++ ) {
      mask <<= 1 ;
      mask |= 1 ;
    }
    COUNTER_MASK = mask ;
  }


// ==========
// Formatting
// ==========

  public DateTime timestampUtc() {
    return new DateTime( timestamp, DateTimeZone.UTC ) ;
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' +
        DATE_TIME_FORMATTER.print( timestampUtc() ) + ';' +
        asStringRoundedToFlooredSecond() +
        '}'
    ;
  }

  public String asStringRoundedToFlooredSecond() {
    final long differenceSeconds = flooredSeconds() ;
    return Long.toString( differenceSeconds, 36 ) + ":" + Long.toString( counter, 36 ) ;
  }

  String asStringWithRawMilliseconds() {
    final long differenceSeconds = timestamp ;
    return 'r' + Long.toString( differenceSeconds, 36 ) + ":" + Long.toString( counter, 36 ) ;
  }

  long flooredSeconds() {
    return ( timestamp - FLOOR_MILLISECONDS ) / 1000 ;
  }


// ==========
// Comparison
// ==========


  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final Stamp that = ( Stamp ) other ;
    return COMPARATOR.compare( this, that ) == 0 ;
  }

  @Override
  public int compareTo( final Stamp other ) {
    checkNotNull( other ) ;
    return COMPARATOR.compare( this, other ) ;
  }

  public static final Comparator< Stamp > COMPARATOR =
      new ComparatorTools.WithNull< Stamp >() {
        @Override
        protected int compareNoNulls(
            final Stamp first,
            final Stamp second
        ) {
          final int timestampComparison = ComparatorTools.compare( first.timestamp, second.timestamp ) ;
          if( timestampComparison == 0 ) {
            final int counterComparison = ComparatorTools.compare( first.counter, second.counter ) ;
            return counterComparison ;
          } else {
            return timestampComparison ;
          }
        }
      }
  ;


// =======
// Parsing
// =======

  /**
   * Matches what {@link #asStringRoundedToFlooredSecond()} returns.
   */
  public static final Pattern REGEX = Pattern.compile( "(-?[0-9a-z]+):([0-9a-z]+)" ) ;

  /**
   * Not thread-safe as it caches previous {@link #timestamp}.
   */
  public static class Parser {
    private long lastTimestamp = 0 ;
    private String lastTimestampAsString = null ;

    /**
     * Relies on {@link #asStringRoundedToFlooredSecond()}'s format.
     */
    public Stamp parse( final CharSequence charSequence ) {
      final Matcher matcher = REGEX.matcher( charSequence ) ;
      if( matcher.matches() ) {
        final String timestampAsString = matcher.group( 1 ) ;
        final String counterAsString = matcher.group( 2 ) ;
        final long newTimestamp ;
        if( timestampAsString.equals( lastTimestampAsString ) ) {
          newTimestamp = lastTimestamp ;
        } else {
          newTimestamp = ( Long.parseLong( timestampAsString, 36 ) * 1000 ) + FLOOR_MILLISECONDS ;
          lastTimestampAsString = timestampAsString ;
          lastTimestamp = newTimestamp ;
        }
        final long newCounter = Long.parseLong( counterAsString, 36 ) ;
        return raw( newTimestamp, newCounter ) ;
      } else {
        return null ;
      }
    }
  }


// =========
// Generator
// =========

  public static class Generator {
    private final Clock clock ;
    private final AtomicReference< Stamp > lastGenerated = new AtomicReference<>() ;

    public Generator( final Clock clock ) {
      this.clock = checkNotNull( clock ) ;
      checkArgument( clock.currentTimeMillis() >= 0 ) ;
    }

    /**
     * Generates a unique {@link Stamp} with unique, always-increasing
     * {@link Stamp#counter} for a {@link Stamp#timestamp} within
     * the same second. It assumes that {@link Clock#currentTimeMillis()} is always increasing.
     */
    public Stamp generate() {
      final long now = clock.currentTimeMillis() ;
      final Stamp stamp = lastGenerated.updateAndGet(
          last -> {
            final long newTimestamp = last == null ?
                now : Math.max( last.timestamp, now ) ;
            final long newTimestampInSeconds = newTimestamp / 1000 ;
            return last != null && last.timestamp / 1000 == newTimestampInSeconds ?
                raw( newTimestamp, last.counter + 1 ) :
                raw( newTimestamp, 0 )
            ;
          }
      ) ;
      return stamp ;
    }
  }


// ==========
// JavaScript
// ==========

  public static final String MODULE = "Stamp" ;
  public static final String MOMENTJS_FORMAT = "YYYY-MM-DD_HH:mm:ss.SSS ZZ" ;

  /**
   * Micro JavaScript library for parsing and formatting. Expects Moment.js library.
   * http://momentjs.com
   */
  public static final String SCRIPT = Joiner.on( "\n" ).join( 
      "var " + MODULE + " = ( function () { ",
      "    ",
      "  // Timestamp supposed to be a moment object (from Moment.js). ",
      "  var _newTuple2 = function( t, c ) { ",
      "    return { ",
      "        timestamp : function() { return t ; },",
      "        counter : function() { return c ; }, ",
      "        raw : function() { return t + ':' + c ; } ",
      "    } ",
      "  } ; ",
      "  var _FLOOR_S = " + ( FLOOR_MILLISECONDS / 1000 ) + " ; " +
      "  ",
      "  return { ",
      "    ",
      "    parse36 : function( s ) { ",
      "      return parseInt( s, 36 ) ;",
      "    }, ",
      "    ",
      "    parseToTuple2 : function( s ) { ",
      "      var regex = /" + REGEX.pattern() + "/g ; ",
      "      var match = regex.exec( s ) ; ",
      "      if( match ) { ",
      // "        print( '_FLOOR_S = ' + _FLOOR_S ) ; ",
      "        var secondsFloored =  " + MODULE + ".parse36( match[ 1 ] ) ; ",
      "        var seconds = secondsFloored + _FLOOR_S ; ",
      "        var timestamp = moment.unix( seconds ) ; // <--- Moment.js used here. ",
      // "        print( 'secondsFloored = ' + secondsFloored ) ; ",
      // "        print( 'seconds = ' + seconds ) ; ",
      // "        print( 'timestamp = ' + timestamp ) ; ",
      // "        print( 'moment( _FLOOR_S ) = ' + moment.unix( _FLOOR_S ) ) ; ",
      "        var counter = " + MODULE + ".parse36( match[ 2 ] ) ; ",
      "        return _newTuple2( timestamp, counter ) ; ",
      "      } else { ",
      "        return null ; ",
      "      } ; ",
      "      return s ; ",
      "    }, ",
      "    ",
      "    parseAndExpand : function( s, format ) { ",
      "      format = format || '" + MOMENTJS_FORMAT + "' ;",
      "      var parsed = " + MODULE + ".parseToTuple2( s ) ; ",
      "      if( parsed == null ) { ",
      "        return null ; ",
      "      } else { ",
      "        var formattedTimestamp = parsed.timestamp().format( format ) ; ",
      "        return formattedTimestamp + ' :: ' + parsed.counter() ; ",
      "      }  ",
      "    } ",
      "  ",
      "  } ;",
      "}() ) ;"
  ) ;


}
