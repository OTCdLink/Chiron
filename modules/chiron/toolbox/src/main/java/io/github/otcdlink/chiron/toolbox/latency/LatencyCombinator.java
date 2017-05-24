package io.github.otcdlink.chiron.toolbox.latency;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.toolbox.clock.Clock;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Gathers  multiple {@link LatencyAverage}s, each identified by a {@link TOPIC}.
 *
 * @param <TOPIC> some arbitrary value used as identifier.
 */
public final class LatencyCombinator< TOPIC extends Comparable< TOPIC > > {

  private final Clock clock ;

  public LatencyCombinator( Clock clock ) {
    this.clock = checkNotNull( clock ) ;
  }

  public void start( final TOPIC topic ) {
    checkNotNull( topic ) ;
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public void end( final TOPIC topic, final boolean success ) {
    checkNotNull( topic ) ;
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public ImmutableMap< TOPIC, LatencyAverage > combinedLatencies() {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  final class OngoingMeasurement {
    public void addOccurence( long start, long end ) {
      throw new UnsupportedOperationException( "TODO" ) ;
    }
    public void addMisfire() {
      throw new UnsupportedOperationException( "TODO" ) ;
    }

    LatencyAverage latencyAverage() {
      throw new UnsupportedOperationException( "TODO" ) ;
    }
  }


}
