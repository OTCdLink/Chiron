package com.otcdlink.chiron.toolbox.clock;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BoundedTimeConsumptionTest {

  @Test
  public void justConsume() throws Exception {
    final int durationMilliseconds = 10;
    final BoundedTimeConsumption boundedTimeConsumption = create( durationMilliseconds ) ;
    boundedTimeConsumption.start() ;
    updateableClock.increment( 1 ) ;
    boundedTimeConsumption.consume() ;
    boundedTimeConsumption.checkTimeRemains() ;
    assertThat( boundedTimeConsumption.remainder() ).isEqualTo( 9 ) ;
    assertThat( boundedTimeConsumption.timeRemains() ).isTrue() ;
    updateableClock.increment( 9 ) ;
    boundedTimeConsumption.consume() ;
    assertThat( boundedTimeConsumption.remainder() ).isEqualTo( -1 ) ;
    assertThat( boundedTimeConsumption.timeRemains() ).isFalse() ;
  }

  @Test( expected = IllegalStateException.class )
  public void consumeRequiresStart() throws Exception {
    create( 1 ).consume() ;
  }

  @Test( expected = IllegalStateException.class )
  public void remainderRequiresStart() throws Exception {
    create( 1 ).remainder() ;
  }

// =======
// Fixture
// =======

  private final UpdateableClock updateableClock = UpdateableClock.newClock( 1000 ) ;

  private BoundedTimeConsumption create( final int durationMilliseconds ) {
    return new BoundedTimeConsumption( updateableClock, durationMilliseconds ) ;
  }



}