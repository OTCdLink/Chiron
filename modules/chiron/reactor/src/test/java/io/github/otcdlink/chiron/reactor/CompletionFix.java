package io.github.otcdlink.chiron.reactor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

/**
 * Lightweight locking stuff that blocks until {@link #outbound(Object)} was called as many
 * times as {@link #inbound()} since {@link #waitForCompletion(long, TimeUnit)}.
 */
class CompletionFix< EVENT > {
  private final ReentrantLock lock = new ReentrantLock() ;
  private final Condition done = lock.newCondition() ;
  private final AtomicLong inboundEventCount = new AtomicLong() ;
  private final AtomicLong outboundEventCount = new AtomicLong() ;
  private final AtomicReference< EVENT > previous = new AtomicReference<>() ;

  /**
   * Made volatile to avoid locking.
   */
  private volatile boolean running = false ;


  public void start() {
    // Non-atomic access, but will catch obvious logic errors.
    checkState( ! running, "Already running" ) ;

    this.running = true ;
    inboundEventCount.set( 0 ) ;
    outboundEventCount.set( 0 ) ;
  }

  public void inbound() {
    /** Don't check if it is running, there can be queued events that arrive after calling
     * {@link waitForCompletion}*/
    inboundEventCount.incrementAndGet() ;
  }

  public void outbound( final EVENT event ) {
    if( running ) {
      mayIncrementOutbound( event ) ;
    } else {
      lock.lock() ;
      try {
        mayIncrementOutbound( event ) ;
        done.signalAll() ;
      } finally {
        lock.unlock() ;
      }
    }
  }

  /**
   * Avoid incrementing twice for the same event.
   * @param event
   */
  private void mayIncrementOutbound( final EVENT event ) {
    if( previous.getAndSet( event ) != event ) {
      outboundEventCount.incrementAndGet() ;
    }
  }

  public void waitForCompletion( final long delay, final TimeUnit timeUnit )
      throws InterruptedException
  {
    // Non-atomic access, but will catch obvious logic errors.
    checkState( running, "Not running" ) ;

    running = false ;
    lock.lock() ;
    try {
      while( inboundEventCount.get() > outboundEventCount.get() ) {
        done.await( delay, timeUnit ) ;
      }
    } finally {
      lock.unlock() ;
    }
  }

}
