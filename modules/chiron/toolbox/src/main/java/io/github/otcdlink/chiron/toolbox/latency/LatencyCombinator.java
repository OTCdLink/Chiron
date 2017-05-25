package io.github.otcdlink.chiron.toolbox.latency;

import io.github.otcdlink.chiron.toolbox.clock.Clock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Aggregates the duration for achieving {@link TOPIC}s into a {@link LatencyAverage}.
 *
 * @param <TOPIC> some arbitrary value used as identifier.
 * @param <CATEGORY> what the standard set of counters relates to.
 */
public final class LatencyCombinator<
    TOPIC extends Comparable< TOPIC >,
    CATEGORY extends Enum< CATEGORY >
> {

  private final Clock clock ;
  private final Class< CATEGORY > categoryClass ;

  private final ConcurrentHashMap< TOPIC, Long > topicsInProgress = new ConcurrentHashMap<>() ;

  /**
   * A reference to the underlying data structure of a {@link LatencyAverage}, to be
   * atomically updated.
   */
  private final AtomicReference< long[] > latencyAverage ;

  public LatencyCombinator( final Clock clock, final Class< CATEGORY > categoryClass ) {
    this.clock = checkNotNull( clock ) ;
    this.categoryClass = checkNotNull( categoryClass ) ;
    latencyAverage = new AtomicReference<>( LatencyAverage.newArray( categoryClass ) ) ;
  }

  public void begin( final TOPIC topic ) {
    checkNotNull( topic ) ;
    final Long newMeasurement = clock.currentTimeMillis() ;
    final Long existing = topicsInProgress.put( topic, newMeasurement ) ;
    if( existing != null ) {
      throw new IllegalStateException( "Already started measurement for " + topic ) ;
    }
  }

  /**
   * Updates {@link #latencyAverage} atomically. This is very fast because there is only
   * a {@code long[]} creation and a few updates.
   */
  public void end( final TOPIC topic, final CATEGORY category ) {
    final long end = clock.currentTimeMillis() ;
    checkNotNull( topic ) ;
    final Long start = topicsInProgress.remove( topic ) ;
    if( start == null ) {
      throw new IllegalArgumentException( "Unknown " + topic ) ;
    }
    latencyAverage.updateAndGet( previous -> {
        final long[] copy = LatencyAverage.copy( previous ) ;
        LatencyAverage.recordInto( copy, category, start, end ) ;
        return copy ;
    } ) ;
  }

  public LatencyAverage< CATEGORY > average() {
    return new LatencyAverage<>( categoryClass, latencyAverage.get() ) ;
  }

}
