package com.otcdlink.chiron.toolbox.handover;

import com.otcdlink.chiron.toolbox.ToStringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

/**
 * Transforms an asynchronous call to the upend logic into a synchronous one.
 *
 * @deprecated use {@link Handover} for high-level contracts and {@link PromiseHandover}
 *     for implementation.
 */
public abstract class Hatch< OBJECT > implements Handover< OBJECT > {

  private static final Logger LOGGER = LoggerFactory.getLogger( Hatch.class ) ;

  public static < OBJECT > Onetime< OBJECT > create() {
    return new Onetime<>() ;
  }

  /**
   * Blocks until a call to {@link #give(Object)}.
   *
   * @return the value passed to {@link #give(Object)}.
   */
  public abstract OBJECT takeOrWait() ;

  public abstract void give( final OBJECT result ) ;

  private static final Object UNASSIGNED = new Object() {
    @Override
    public String toString() {
      return Hatch.class.getSimpleName() + "#NULL" ;
    }
  } ;


  public static< OBJECT > Hatch< OBJECT > createNull() {
    return new Hatch< OBJECT >() {
      @Override
      public OBJECT takeOrWait() {
        throw new UnsupportedOperationException( "Don't call" ) ;
      }

      @Override
      public void give( final OBJECT result ) { }
    } ;
  }


  public static class Abstract< OBJECT > extends Hatch< OBJECT > {
    protected final ReentrantLock lock = new ReentrantLock() ;
    protected final Condition condition = lock.newCondition() ;
    protected OBJECT passed = ( OBJECT ) UNASSIGNED ;

    @Override
    public OBJECT takeOrWait() {
      LOGGER.debug( "Entered " + this + "#takeOrWait." ) ;
      lock.lock() ;
      try {
        while( passed == UNASSIGNED ) {
          condition.await() ;
        }
        LOGGER.debug( "Exiting " + this + "#takeOrWait successfully." ) ;
        return passed ;
      } catch( final InterruptedException e ) {
        LOGGER.debug( "Exiting " + this + "#takeOrWait with exception " + e + "." ) ;
        throw new RuntimeException( "Should not happen", e ) ;
      } finally {
        lock.unlock() ;
      }
    }

    @Override
    public void give( final OBJECT result ) {
      lock.lock() ;
      checkState( passed == UNASSIGNED ) ;
      try {
        passed = result ;
        condition.signalAll() ;
      } finally {
        lock.unlock() ;
      }
    }

    @Override
    public String toString() {
      return ToStringTools.nameAndCompactHash( this ) + "{}" ;
    }
  }

  public static class Onetime< OBJECT > extends Hatch< OBJECT > {
    private final Rereadable< OBJECT > rereadable = new Rereadable<>() ;
    private final AtomicBoolean safeguard = new AtomicBoolean() ;

    /**
     * Blocks until a call to {@link #give(Object)}.
     *
     * @return the value passed to {@link #give(Object)}.
     */
    @Override
    public OBJECT takeOrWait() {
      checkState( safeguard.compareAndSet( false, true ), "Already called" ) ;
      return rereadable.takeOrWait() ;
    }

    @Override
    public void give( final OBJECT result ) {
      rereadable.give( result ) ;
    }
  }

  /**
   * Supports several calls to {@link #takeOrWait()}.
   */
  public static class Rereadable< OBJECT > extends Abstract< OBJECT >{ }


}
