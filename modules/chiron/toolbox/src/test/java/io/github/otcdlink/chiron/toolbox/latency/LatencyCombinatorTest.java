package io.github.otcdlink.chiron.toolbox.latency;

import io.github.otcdlink.chiron.toolbox.clock.UpdateableClock;
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

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( LatencyCombinatorTest.class ) ;

  private static final int BEGIN_TIME = 10000 ;

  private final UpdateableClock clock = UpdateableClock.newClock( BEGIN_TIME ) ;

  private final LatencyCombinator< PrivateTopic, PrivateCategory > latencyCombinator =
      new LatencyCombinator<>( clock, PrivateCategory.class ) ;

  private enum PrivateTopic { T1, T2 }
  private enum PrivateCategory { A, B }


}