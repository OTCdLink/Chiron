package com.otcdlink.chiron.toolbox.latency;

import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class LatencyCombinatorTest {

  @Test
  public void justCombine() throws Exception {
    latencyCombinator.begin( PrivateTopic.T1 ) ;
    clock.increment() ;
    latencyCombinator.begin( PrivateTopic.T2 ) ;
    clock.increment() ;
    latencyCombinator.end( PrivateTopic.T2, PrivateCategory.A ) ;
    clock.increment() ;
    latencyCombinator.end( PrivateTopic.T1, PrivateCategory.B ) ;
    latencyCombinator.begin( PrivateTopic.T1 ) ;
    clock.increment( 2 ) ;
    latencyCombinator.end( PrivateTopic.T1, PrivateCategory.A ) ;

    final LatencyAverage< PrivateCategory > average = latencyCombinator.average() ;
    LOGGER.info( "Got: " + average + "." ) ;

    assertThat( average.beginTime() ).isEqualTo( BEGIN_TIME ) ;
    assertThat( average.endTime() ).isEqualTo( BEGIN_TIME + 5 ) ;
    assertThat( average.occurenceCount( PrivateCategory.A ) ).isEqualTo( 2 ) ;
    assertThat( average.cumulatedDelay( PrivateCategory.A ) ).isEqualTo( 3 ) ;
    assertThat( average.peakDelay( PrivateCategory.A ) ).isEqualTo( 2 ) ;
    assertThat( average.occurenceCount( PrivateCategory.B ) ).isEqualTo( 1 ) ;
    assertThat( average.cumulatedDelay( PrivateCategory.B ) ).isEqualTo( 3 ) ;
    assertThat( average.peakDelay( PrivateCategory.B ) ).isEqualTo( 3 ) ;

  }
  @Test
  public void average() throws Exception {
    latencyCombinator.begin( PrivateTopic.T1 ) ;
    latencyCombinator.begin( PrivateTopic.T2 ) ;
    latencyCombinator.begin( PrivateTopic.T3 ) ;
    final int durationMs = 370 ;
    clock.increment( durationMs ) ;
    latencyCombinator.end( PrivateTopic.T1, PrivateCategory.A ) ;
    latencyCombinator.end( PrivateTopic.T2, PrivateCategory.A ) ;
    latencyCombinator.end( PrivateTopic.T3, PrivateCategory.A ) ;

    final LatencyAverage< PrivateCategory > average = latencyCombinator.average() ;
    LOGGER.info( "Got: " + average + ", " +
        "throughput= " + average.occurencePerSecond( PrivateCategory.A ) + "." ) ;

    assertThat( average.beginTime() ).isEqualTo( BEGIN_TIME ) ;
    assertThat( average.endTime() ).isEqualTo( BEGIN_TIME + durationMs ) ;
    assertThat( average.occurenceCount( PrivateCategory.A ) ).isEqualTo( 3 ) ;
    assertThat( average.cumulatedDelay( PrivateCategory.A ) ).isEqualTo( durationMs * 3 ) ;
    assertThat( average.peakDelay( PrivateCategory.A ) ).isEqualTo( durationMs ) ;

    final float expectedOccurencePerSecond ;
    {  // Keep only 1 decimal.
      final float manyDecimals = ( ( float ) 3000 ) / ( ( float ) durationMs )  ;
      final String floatAsString = Float.toString( manyDecimals ) ;
      final String truncated = floatAsString.substring( 0, floatAsString.indexOf( '.' ) + 2 ) ;
      expectedOccurencePerSecond = Float.parseFloat( truncated ) ;
    }
    assertThat( average.occurencePerSecond( PrivateCategory.A ) )
        .isEqualTo( expectedOccurencePerSecond ) ;
    LOGGER.info( "Occurence per second = " + expectedOccurencePerSecond + "." ) ;

  }

  @Test
  public void merge() throws Exception {
    final LatencyCombinator< PrivateTopic, PrivateCategory > latencyCombinator1 =
        this.newLatencyCombinator() ;
    latencyCombinator1.begin( PrivateTopic.T1 ) ;
    latencyCombinator1.begin( PrivateTopic.T2 ) ;
    clock.increment() ;
    latencyCombinator1.end( PrivateTopic.T1, PrivateCategory.A ) ;
    clock.increment() ;
    latencyCombinator1.end( PrivateTopic.T2, PrivateCategory.B ) ;
    final LatencyAverage< PrivateCategory > latency1 = latencyCombinator1.average() ;

    // Test health.
    assertThat( latency1.beginTime() ).isEqualTo( BEGIN_TIME ) ;
    assertThat( latency1.occurenceCount( PrivateCategory.A ) ).isEqualTo( 1 ) ;
    assertThat( latency1.occurenceCount( PrivateCategory.B ) ).isEqualTo( 1 ) ;
    assertThat( latency1.peakDelay( PrivateCategory.A ) ).isEqualTo( 1 ) ;
    assertThat( latency1.peakDelay( PrivateCategory.B ) ).isEqualTo( 2 ) ;
    assertThat( latency1.cumulatedDelay( PrivateCategory.A ) ).isEqualTo( 1 ) ;
    assertThat( latency1.cumulatedDelay( PrivateCategory.B ) ).isEqualTo( 2 ) ;
    assertThat( latency1.endTime() ).isEqualTo( BEGIN_TIME + 2 ) ;

    clock.increment() ;
    final LatencyCombinator< PrivateTopic, PrivateCategory > latencyCombinator2 =
        this.newLatencyCombinator() ;
    latencyCombinator2.begin( PrivateTopic.T3 ) ;
    clock.increment() ;
    latencyCombinator2.end( PrivateTopic.T3, PrivateCategory.B ) ;
    final LatencyAverage< PrivateCategory > latency2 = latencyCombinator2.average() ;

    // Test health.
    assertThat( latency2.beginTime() ).isEqualTo( BEGIN_TIME + 3 ) ;
    assertThat( latency2.occurenceCount( PrivateCategory.A ) ).isEqualTo( 0 ) ;
    assertThat( latency2.occurenceCount( PrivateCategory.B ) ).isEqualTo( 1 ) ;
    assertThat( latency2.peakDelay( PrivateCategory.A ) ).isEqualTo( 0 ) ;
    assertThat( latency2.peakDelay( PrivateCategory.B ) ).isEqualTo( 1 ) ;
    assertThat( latency2.cumulatedDelay( PrivateCategory.A ) ).isEqualTo( 0 ) ;
    assertThat( latency2.cumulatedDelay( PrivateCategory.B ) ).isEqualTo( 1 ) ;
    assertThat( latency2.endTime() ).isEqualTo( BEGIN_TIME + 4 ) ;


    //noinspection unchecked
    final LatencyAverage< PrivateCategory > merged = LatencyCombinator.combine(
        PrivateCategory.class, latency1, latency2 ) ;

    assertThat( merged.beginTime() ).isEqualTo( BEGIN_TIME ) ;
    assertThat( merged.occurenceCount( PrivateCategory.A ) ).isEqualTo( 1 ) ;
    assertThat( merged.occurenceCount( PrivateCategory.B ) ).isEqualTo( 2 ) ;
    assertThat( merged.peakDelay( PrivateCategory.A ) ).isEqualTo( 1 ) ;
    assertThat( merged.peakDelay( PrivateCategory.B ) ).isEqualTo( 2 ) ;
    assertThat( merged.cumulatedDelay( PrivateCategory.A ) ).isEqualTo( 1 ) ;
    assertThat( merged.cumulatedDelay( PrivateCategory.B ) ).isEqualTo( 3 ) ;
    assertThat( merged.endTime() ).isEqualTo( BEGIN_TIME + 4 ) ;

  }

  // =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( LatencyCombinatorTest.class ) ;

  private static final int BEGIN_TIME = 10000 ;

  private final UpdateableClock clock = UpdateableClock.newClock( BEGIN_TIME ) ;

  private final LatencyCombinator< PrivateTopic, PrivateCategory > latencyCombinator =
      newLatencyCombinator() ;

  private LatencyCombinator<PrivateTopic, PrivateCategory> newLatencyCombinator() {
    return new LatencyCombinator<>( clock, PrivateCategory.class );
  }

  private enum PrivateTopic { T1, T2, T3 }
  private enum PrivateCategory { A, B }


}