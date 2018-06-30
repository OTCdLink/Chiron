package com.otcdlink.chiron.middle.tier;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeBoundaryTest {

  @Test
  public void variance() throws Exception {
    final TimeBoundary.ForAll timeBoundary = TimeBoundary.newBuilder()
        .pingInterval( 1 )
        .pongTimeoutOnDownend( 1 )
        .reconnectDelay( 1, 3 )
        .pingTimeoutOnUpend( 1 )
        .maximumSessionInactivity( 1 )
        .build()
    ;

    LOGGER.info( "Created " + timeBoundary + "." ) ;

    final Random random = new Random() ;
    for( int i = 0 ; i < 1000 ; i++ ) {
      assertThat( timeBoundary.reconnectDelayMs( random ) )
          .isGreaterThanOrEqualTo( 1 )
          .isLessThanOrEqualTo( 3 )
      ;
    }
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( TimeBoundary.ForAll.class ) ;

}