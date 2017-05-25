package io.github.otcdlink.chiron.toolbox.latency;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import org.joda.time.Duration;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Simple statistics from a serie of occurences.
 * This class stores all its values into a {@code long[]} so {@link LatencyCombinator} can
 * quickly update them using {@code AtomicReference#updateAndGet}
 *
 * @param <CATEGORY> the enum type for defining multiple occurence counters.
 */
public final class LatencyAverage< CATEGORY extends Enum< CATEGORY > > {

  /**
   * Time (as returned by {@code System#currentTimeMillis()}) when starting the measurement.
   * The value is shared by every {@link CATEGORY}.
   */
  public long beginTime() {
    return common( array, Common.BEGIN_TIME ) ;
  }

  /**
   * Time (as returned by {@code System#currentTimeMillis()}) when ending the measurement.
   * The value is shared by every {@link CATEGORY}.
   */
  public long endTime() {
    return common( array, Common.END_TIME ) ;
  }

  public Duration duration() {
    return new Duration( endTime() - beginTime() ) ;
  }
  
  /**
   * Returns associated counter.
   *
   * @return a value greater than or equal to 0.
   */
  public long occurenceCount( final CATEGORY category ) {
    return counter( array, Counter.OCCURENCE, category ) ;
  }

  public final long cumulatedDelay( final CATEGORY category ) {
    return counter( array, Counter.CUMULATED_DELAY, category ) ;
  }

  public final long peakDelay( final CATEGORY category ) {
    return counter( array, Counter.PEAK_DELAY, category ) ;
  }

  private final Class< CATEGORY > categoryEnumClass ;

  /**
   * Keeps all the fields together. Since the number of counters is fixed, and since we know
   * how many {@link CATEGORY} there are in advance, we can safely use offsets in a single array.
   *
   * <pre>
   *
   * |____|____|____|____|____|____|____|____|...
   *                          |..............| -> {@link CATEGORY} (1)
   *           |..............|                -> {@link CATEGORY} (0)
   * |.........|                               -> {@link Common}
   *  ^    ^    ^    ^    ^    ^
   *  |    |    |    |    |    |
   *  |    |    |    |    |    ...
   *  |    |    |    |    {@link Counter#PEAK_DELAY}
   *  |    |    |    {@link Counter#CUMULATED_DELAY}
   *  |    |    {@link Counter#OCCURENCE}
   *  |    ending of measurement
   *  beginning of measurement
   * </pre>
   */
  private final long[] array ;

  LatencyAverage( final Class< CATEGORY > categoryEnumClass, final long[] array ) {
    this.categoryEnumClass = checkNotNull( categoryEnumClass ) ;
    checkArgument( array.length == arraySize( categoryEnumClass ) ) ;
    this.array = copy( array ) ;
  }

  private static final Converter< String, String > CASE_CONVERTER =
      CaseFormat.UPPER_UNDERSCORE.converterTo( CaseFormat.LOWER_CAMEL ) ;

  @Override
  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder() ;
    stringBuilder.append( LatencyAverage.class.getSimpleName() ).append( '{' ) ;
    stringBuilder.append( "duration=" ).append( duration() ) ;
    for( final CATEGORY category : categoryEnumClass.getEnumConstants() ) {
      stringBuilder.append( ';' ) ;
      stringBuilder.append( category.name() ).append( "=[" ) ;
      for( final Counter counter : Counter.values() ) {
        if( counter.ordinal() > 0 ) {
          stringBuilder.append( ';' ) ;
        }
        stringBuilder.append( CASE_CONVERTER.convert( counter.name() ) ) ;
        stringBuilder.append( '=' ) ;
        stringBuilder.append( counter( array, counter, category ) ) ;
      }
      stringBuilder.append( "]" ) ;
    }
    stringBuilder.append( '}' ) ;
    return stringBuilder.toString() ;
  }

// ======
// Arrays
// ======

  static long[] copy( final long[] array ) {
    final long[] copy = new long[ array.length ] ;
    System.arraycopy( array, 0, copy, 0, array.length ) ;
    return copy ;
  }

  /**
   * Creates an array full of zeroes. {@link #recordInto(long[], Enum, long, long)} will detect
   * a {@link Common#BEGIN_TIME} of 0 and will update it properly.
   */
  static < CATEGORY extends Enum< CATEGORY > > long[] newArray(
      final Class< CATEGORY > categoryEnumClass
  ) {
    return new long[ arraySize( categoryEnumClass ) ] ;
  }

  private static < CATEGORY extends Enum< CATEGORY > > int arraySize(
      final Class< CATEGORY > categoryEnumClass
  ) {
    final int categoryCount = categoryEnumClass.getEnumConstants().length ;
    return Common.SIZE + ( categoryCount * Counter.SIZE ) ;
  }




// ============  
// Array access
// ============

  enum Common {
    BEGIN_TIME,
    END_TIME,
    ;
    private static final int SIZE = Common.values().length ;
  }

  static long common( final long[] array, final Common common ) {
    return array[ common.ordinal() ] ;
  }

  static void common( final long[] array, final Common common, final long newValue ) {
    array[ common.ordinal() ] = newValue ;
  }

  private enum Counter {
    OCCURENCE,
    CUMULATED_DELAY,
    PEAK_DELAY,
    ;
    private static final int SIZE = Counter.values().length ;
  }

  private static < CATEGORY extends Enum< CATEGORY > > long counter(
      final long[] array,
      final Counter counter,
      final CATEGORY category
  ) {
    return array[ counterIndex( counter, category ) ] ;
  }

  private static < CATEGORY extends Enum< CATEGORY > > void counter(
      final long[] array,
      final Counter counter,
      final CATEGORY category,
      final long newValue
  ) {
    array[ counterIndex( counter, category ) ] = newValue ;
  }

  private static < CATEGORY extends Enum< CATEGORY > > int counterIndex(
      final Counter counter,
      final CATEGORY category
  ) {
    return Common.SIZE + ( category.ordinal() * Counter.SIZE ) + counter.ordinal() ;
  }

  /**
   *
   * @param array a valid array (no check performed), created with {@link #newArray(Class)}.
   * @param timeAtStart a value strictly greater than 0. 0 is a magic value telling the array
   *     contains no record yet.
   */
  static < CATEGORY extends Enum< CATEGORY > > void recordInto(
      final long[] array,
      final CATEGORY category,
      final long timeAtStart,
      final long timeAtEnd
  ) {
    checkArgument( timeAtStart > 0 ) ;
    checkArgument( timeAtStart <= timeAtEnd ) ;
    final long duration = timeAtEnd - timeAtStart ;

    final long oldStart = common( array, Common.BEGIN_TIME ) ;
    if( oldStart == 0 || oldStart > timeAtStart ) {
      common( array, Common.BEGIN_TIME, timeAtStart ) ;
    }

    final long oldEnd = common( array, Common.END_TIME ) ;
    checkArgument( timeAtEnd >= oldEnd, "Old end: " + oldEnd + ", new end: " + timeAtEnd ) ;
    common( array, Common.END_TIME, timeAtEnd ) ;

    final long oldOccurence = counter( array, Counter.OCCURENCE, category ) ;
    counter( array, Counter.OCCURENCE, category, oldOccurence + 1 ) ;

    final long oldPeakDelay = counter( array, Counter.PEAK_DELAY, category ) ;
    if( duration > oldPeakDelay ) {
      counter( array, Counter.PEAK_DELAY, category, duration ) ;
    }

    final long oldCumulatdDelay = counter( array, Counter.CUMULATED_DELAY, category ) ;
    counter( array, Counter.CUMULATED_DELAY, category, oldCumulatdDelay + duration ) ;

  }


}
