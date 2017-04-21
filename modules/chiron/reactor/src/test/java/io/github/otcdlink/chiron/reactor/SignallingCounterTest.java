package io.github.otcdlink.chiron.reactor;


import org.junit.Test;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SignallingCounterTest {


  @Test( timeout = TIMEOUT )
  public void defaultIsZero() throws Exception {
    signallingCounter.waitForZero( 1, TimeUnit.SECONDS ) ;
  }

  @Test( timeout = TIMEOUT )
  public void incrementAndDecrement() throws Exception {
    signallingCounter.increment() ;
    signallingCounter.decrement() ;
    signallingCounter.waitForZero( 1, TimeUnit.SECONDS ) ;
  }

  @Test( expected = IllegalStateException.class )
  public void justDecrement() throws Exception {
    signallingCounter.decrement() ;
  }

  @Test( timeout = TIMEOUT, expected = TimeoutException.class )
  public void timeout() throws Exception {
    signallingCounter.increment() ;
    signallingCounter.waitForZero( 10, TimeUnit.MILLISECONDS ) ;
  }

  @Test( timeout = TIMEOUT )
  public void releaseOnlyAtZero() throws Exception {
    Thread.currentThread().setName( SignallingCounterTest.class.getSimpleName() ) ;

    final Semaphore readyToDecrementSemaphore = new Semaphore( 0 ) ;
    final Semaphore decrementSemaphore = new Semaphore( 0 ) ;
    new Thread(
        () -> {
          readyToDecrementSemaphore.release() ;
          try {
            decrementSemaphore.acquire() ;
            signallingCounter.decrement() ;
          } catch( InterruptedException e ) {
            throw new RuntimeException( e ) ;
          }
        },
        "auxiliary"
    ).start() ;

    readyToDecrementSemaphore.acquire() ;
    signallingCounter.increment() ;
    decrementSemaphore.release() ;
    signallingCounter.waitForZero( 1, TimeUnit.SECONDS ) ;

  }

// =======
// Fixture
// =======

  private static final long TIMEOUT = 1000 ;

  private final FlowgraphBuilderTools.SignallingCounter signallingCounter =
      new FlowgraphBuilderTools.SignallingCounter() ;

}