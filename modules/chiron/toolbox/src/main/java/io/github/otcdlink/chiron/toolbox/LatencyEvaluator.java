package io.github.otcdlink.chiron.toolbox;

import io.github.otcdlink.chiron.toolbox.clock.Clock;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class LatencyEvaluator< TOPIC > {

  @SuppressWarnings( "UnusedDeclaration" )
  private static final Logger LOGGER = LoggerFactory.getLogger( LatencyEvaluator.class ) ;

  private final Clock clock ;

  /**
   * Need a lock to protect {@link #ongoingLatencyMeasurement}, compare-and-swap can't work
   * if there is more one counter to increment at once.
   */
  private final Object lock = ToStringTools.createLockWithNiceToString( getClass() ) ;

  public LatencyEvaluator() {
    this( Clock.SYSTEM_CLOCK ) ;
  }

  public LatencyEvaluator( final Clock clock ) {
    this.clock = clock ;
  }

  private final Map< TOPIC, DateTime > measurements = new HashMap<>() ;


  public void begin( final TOPIC topic ) {
    synchronized( lock ) {
      checkArgument(
          measurements.put( topic, clock.getCurrentDateTime() ) == null,
          "Already measuring " + topic
      ) ;
    }
  }

  private final LatencyEvaluator.MeasurementInProgress ongoingLatencyMeasurement =
      new LatencyEvaluator.MeasurementInProgress() ;

  public void end( final TOPIC topic ) {
    final DateTime endTime = clock.getCurrentDateTime() ;
    synchronized( lock ) {
      final DateTime startTime = measurements.remove( topic ) ;
      checkState( startTime != null, "Could not find measurement for " + topic ) ;
      ongoingLatencyMeasurement.add( startTime.getMillis(), endTime.getMillis() ) ;
    }

  }

  public LatencyEvaluator.CombinedLatency combinedLatency() {
    synchronized( lock ) {
      return ongoingLatencyMeasurement.copy() ;
    }
  }


  public static CombinedLatency newCombinedLatency() {
    return new MeasurementInProgress() ;
  }

  public interface CombinedLatency {
    long occurenceCount() ;
    long peakDelayMilliseconds() ;

    default float averageDelayMilliseconds() {
      return occurenceCount() == 0 ? 0 : cumulatedDelayMilliseconds() / occurenceCount() ;
    }

    /**
     * Difference between start of earliest measurement, and end of latest measurement.
     */
    default long overallMeasurementDuration() {
      return end() - start() ;
    }

    /**
     * {@link #occurenceCount()} divided by {@link #cumulatedDelayMilliseconds()}.
     */
    default float throughputOccurencePerSecond() {
      final long throughput = overallMeasurementDuration() <= 0 ? 0 :
          ( occurenceCount() * 1000 ) / overallMeasurementDuration() ;
      return throughput ;
    }

    /**
     * For internal use, doesn't harm to expose it however.
     */
    long cumulatedDelayMilliseconds() ;

    /**
     * For internal use, doesn't harm to expose it however.
     */
    long start() ;

    /**
     * For internal use, doesn't harm to expose it however.
     */
    long end() ;

    /**
     * For internal use, doesn't harm to expose it however.
     */
    CombinedLatency copy() ;

  }

  private static class AbstractCombinedLatency implements CombinedLatency {

    protected long occurenceCount = 0 ;
    protected long cumulatedDelay = 0 ;
    protected long peakDelay = 0 ;
    protected long start = -1 ;
    protected long end = -1 ;

    @Override
    public long occurenceCount() {
      return occurenceCount ;
    }

    @Override
    public long peakDelayMilliseconds() {
      return peakDelay ;
    }

    @Override
    public long cumulatedDelayMilliseconds() {
      return cumulatedDelay ;
    }

    @Override
    public long end() {
      return end ;
    }

    @Override
    public long start() {
      return start ;
    }

    @Override
    public CombinedLatency copy() {
      return new MeasurementInProgress(
          occurenceCount(),
          cumulatedDelayMilliseconds(),
          peakDelayMilliseconds(),
          start,
          end
      ) ;
    }

  }

  public static final class MeasurementInProgress extends AbstractCombinedLatency {

    public MeasurementInProgress(
        final long occurenceCount,
        final long cumulatedDelay,
        final long peakDelay,
        final long start,
        final long end
    ) {
      this.occurenceCount = occurenceCount ;
      this.cumulatedDelay = cumulatedDelay ;
      this.peakDelay = peakDelay ;
      this.start = start ;
      this.end = end ;
    }

    public MeasurementInProgress() {
      this( 0, 0, 0, -1, -1 ) ;
    }

    public void add( final long start, final long end ) {
      final long delay = end - start ;
      occurenceCount ++ ;
      cumulatedDelay += delay ;
      if( delay > peakDelay ) {
        peakDelay = delay ;
      }
      this.start = this.start < 0 ? start : Math.min( this.start, start ) ;
      this.end = Math.max( this.end, end ) ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + '{' +
          "occurenceCount=" + occurenceCount() + ';' +
          "averageDelay=" + averageDelayMilliseconds() + "ms;" +
          "peakDelay=" + peakDelayMilliseconds() + "ms;" +
          "span=" + overallMeasurementDuration() + "ms;" +
          "throughput=" + throughputOccurencePerSecond() + "occurence/s;" +
          '}'
      ;
    }
  }
}
