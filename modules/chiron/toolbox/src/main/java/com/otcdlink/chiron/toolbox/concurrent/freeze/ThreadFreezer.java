package com.otcdlink.chiron.toolbox.concurrent.freeze;

import com.otcdlink.chiron.toolbox.ToStringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;

public final class ThreadFreezer< FREEZABLE >  {

  private static final Logger LOGGER = LoggerFactory.getLogger( ThreadFreezer.class ) ;

  private final AtomicReference< SignalKit > signalKit = new AtomicReference<>( null ) ;

  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + "{}" ;
  }

  /**
   * Blocks until a call to {@link Freezable#freeze(Consumer)} happens.
   */
  public ThreadFreeze< FREEZABLE > freeze() {
    final SignalKit signalKit = this.signalKit.getAndSet( null ) ;
    checkState( signalKit != null, "Not ready to lock" ) ;
    final FREEZABLE lockable ;
    try {
      lockable = signalKit.lockComplete.take() ;
    } catch( final InterruptedException e ) {
      throw new RuntimeException( "Should not happen", e ) ;
    }
    signalKit.locked = lockable ;
    LOGGER.info( "Locked " + lockable + " in " + this + "." ) ;
    return signalKit.threadFreeze ;
  }

  /**
   * Returns a {@code Consumer} of the object to be locked; the caller of {@code Consumer#apply()}
   * <em>must</em> pass an object of the {@link FREEZABLE} type.
   * This object will be retrieved by {@link ThreadFreeze#frozen()}.
   */
  public Consumer< FREEZABLE > asConsumer() {
    final SignalKit signalKit = new SignalKit() ;
    checkState( this.signalKit.compareAndSet( null, signalKit ), "Already locked by " +
        ThreadFreezer.this.toString() ) ;

    return new Consumer< FREEZABLE >() {
      @Override
      public void accept( FREEZABLE freezable ) {
        signalKit.lockComplete.offer( freezable ) ;
        try {
          signalKit.unlockComplete.acquire() ;
        } catch( InterruptedException e ) {
          throw new RuntimeException( e ) ;
        }
      }

      @Override
      public String toString() {
        return ToStringTools.nameAndCompactHash( this ) + "{" +
            ThreadFreezer.this.toString() + "}" ;
      }
    } ;

  }

  private class SignalKit {

    final BlockingQueue< FREEZABLE > lockComplete = new ArrayBlockingQueue<>( 1 ) ;

    /**
     * {@code Object#wait()/#notify()} are not suitable because we could start waiting after
     * the notification happened. A {@code Semaphore} keeps track of that.
     */
    final Semaphore unlockComplete = new Semaphore( 0 ) ;
    FREEZABLE locked = null ;

    final ThreadFreeze threadFreeze = new ThreadFreeze() {
      @Override
      public void unfreeze() {
        unlockComplete.release() ;
        LOGGER.debug( "Unlocked " + locked + " in " + ThreadFreezer.this + "." ) ;
      }

      @Override
      public FREEZABLE frozen() {
        checkState( unlockComplete.availablePermits() == 0,
            "Already released " + unlockComplete + " for " + ThreadFreezer.this ) ;
        return locked ;
      }

      @Override
      public String toString() {
        return ThreadFreeze.class.getSimpleName() + "{" + unlockComplete + "}" ;
      }
    } ;

  }

}
