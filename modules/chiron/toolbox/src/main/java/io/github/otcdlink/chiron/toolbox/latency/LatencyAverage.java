package io.github.otcdlink.chiron.toolbox.latency;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Simple statistics from a serie of occurences that have an associated duration or a failure.
 */
public final class LatencyAverage {

  /**
   * All occurences, including {@link #misfireCount}.
   */
  public final long occurenceCount ;

  /**
   * Non-successful occurences. They don't count in delay measurement.
   */
  public final long misfireCount ;

  /**
   * Time (as returned by {@code System#currentTimeMillis()}) when starting the measurement.
   */
  public final long beginTime ;

  /**
   * Time (as returned by {@code System#currentTimeMillis()}) when ending the measurement.
   */
  public final long endTime ;

  public final long cumulatedDelay ;

  public final long peakDelay ;

  LatencyAverage(
      final long occurenceCount,
      final long misfireCount,
      final long beginTime,
      final long endTime,
      final long cumulatedDelay,
      final long peakDelay
  ) {
    checkArgument( occurenceCount >= 0 ) ;
    checkArgument( occurenceCount - misfireCount >= 0 ) ;
    this.occurenceCount = occurenceCount ;

    checkArgument( misfireCount >= 0 ) ;
    this.misfireCount = misfireCount ;

    checkArgument( beginTime >= 0 ) ;
    this.beginTime = beginTime ;

    checkArgument( endTime >= 0 ) ;
    checkArgument( endTime >= beginTime ) ;
    this.endTime = endTime ;

    checkArgument( cumulatedDelay >= 0 ) ;
    this.cumulatedDelay = cumulatedDelay ;

    checkArgument( peakDelay >= 0 ) ;
    this.peakDelay = peakDelay ;
  }

  public long successCount() {
    return occurenceCount - misfireCount ;
  }



  public LatencyAverage combine( final LatencyAverage first, final LatencyAverage second ) {
    if( first == null ) {
      if( second == null ) {
        return null ;
      } else {
        return second ;
      }
    } else {
      if( second == null ) {
        return first ;
      } else {
        return first.combine( second ) ;
      }
    }
  }

  public LatencyAverage combine( final LatencyAverage other ) {
    checkNotNull( other ) ;
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  // TODO: other methods for span, average, etc.
}
