package com.otcdlink.chiron.toolbox.clock;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractPulseTest {

//  @Test( timeout = 2_000_000 )
  @Test( timeout = 2000 )
  public void justSchedule() throws Exception {
    Thread.currentThread().setName( "test" ) ;
    final Pulse pulse = newPulse( timestamp -> doneSemaphore.release() ) ;
    try {
      pulse.start() ;
      doneSemaphore.acquire() ;
    } finally {
      pulse.stop() ;
    }
  }


  //  @Test( timeout = 5_000_000 )
  @Test( timeout = 20_000 )
  public void runSeveralTimes() throws Exception {
    Thread.currentThread().setName( "test" ) ;
    final BlockingQueue< DateTime > timestamps = new LinkedBlockingQueue<>() ;
    final Pulse pulse = newPulse( timestamps::add ) ;
    try {
      LOGGER.info( "Starting at " + now() + "." ) ;
      pulse.start() ;
      DateTime previous = now() ;
      for( int i = 0 ; i < 3 ; i++ ) {
        final DateTime timestamp = timestamps.take() ;
        LOGGER.info( "Obtained    " + timestamp + "." ) ;
        assertThat( PulseTools.transitioned( previous, timestamp, resolution ) ) ;
        previous = timestamp ;
      }
    } finally {
      pulse.stop() ;
    }
  }

  @Test( timeout = 10_000 )
  public void restart() throws Exception {
    Thread.currentThread().setName( "test" ) ;
    final BlockingQueue< DateTime > timestamps = new LinkedBlockingQueue<>() ;
    final Pulse pulse = newPulse( timestamps::add ) ;
    try {
      LOGGER.info( "Starting at " + now() + "." ) ;
      pulse.start() ;
      final DateTime timestamp = timestamps.take() ;
      LOGGER.info( "Obtained    " + timestamp + "." ) ;
    } finally {
      pulse.stop() ;
      LOGGER.info( "Stopped at  " + now() + "." ) ;
    }
    assertThat( timestamps ).isEmpty() ; // No pulse while stopped.
    try {
      LOGGER.info( "Starting at " + now() + "." ) ;
      pulse.start() ;
      final DateTime timestamp1 = timestamps.take() ;
      LOGGER.info( "Obtained    " + timestamp1 + "." ) ;
      final DateTime timestamp2 = timestamps.take() ;
      LOGGER.info( "Obtained    " + timestamp2 + "." ) ;
    } finally {
      pulse.stop() ;
    }
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( AbstractPulseTest.class ) ;

  private final Semaphore doneSemaphore = new Semaphore( 0 ) ;

  @SuppressWarnings( { "OverridableMethodCallDuringObjectConstruction", "OverriddenMethodCallDuringObjectConstruction" } )
  protected final Pulse.Resolution resolution = resolution() ;

  private static DateTime now() {
    return new DateTime( DateTimeZone.UTC ) ;
  }

  protected abstract Pulse newPulse( final Pulse.Tickee tickee ) ;

  protected abstract Pulse.Resolution resolution() ;


  private static final int TIMEOUT = 1_000_000 ;
//  private static final int TIMEOUT = 1_000 ;

}