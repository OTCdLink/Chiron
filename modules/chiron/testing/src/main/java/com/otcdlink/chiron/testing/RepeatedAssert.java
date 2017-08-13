package com.otcdlink.chiron.testing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static com.google.common.base.Preconditions.checkArgument;
import static org.junit.Assert.fail;

public class RepeatedAssert {

  private static final Logger LOGGER = LoggerFactory.getLogger( RepeatedAssert.class ) ;

  private RepeatedAssert() {
  }

  public static void assertEventually(
      final BooleanSupplier predicate,
      final long period,
      final TimeUnit timeUnit,
      final int retries
  ) {
    checkArgument( retries > 0, "Retries (currently %s) must be > 0", retries ) ;
    checkArgument( period > 0L, "Period ( currently %s) must be > 0", period ) ;

    LOGGER.debug( "Asserting for a maximum duration of " +
        period * ( long ) retries + " " + timeUnit + "..." ) ;

    int retryCount = 0 ;
    while( true ) {

      if( predicate.getAsBoolean() ) {
        return ;
      }
      if( retryCount ++ < retries ) {
        try {
          LOGGER.debug( "Unmatched predicate " + predicate + ", waiting a bit and retrying..." );
          timeUnit.sleep( period ) ;
        } catch( InterruptedException e ) {
          throw new RuntimeException( "Should not happen", e ) ;
        }
        continue ;
      }
      fail( "Unmatched predicate after " + retries + " retries." ) ;
    }

  }

}
