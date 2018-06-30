package com.otcdlink.chiron.fixture;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@code List} which contains at most one element.
 * This is useful for capturing arguments with JMockit. Using {@link #getOrWait()} we can wait
 * for the effective call of the method, and got passed argument at once.
 * 
 * @see Monolist
 */
public final class BlockingMonolist< ELEMENT > extends AbstractCaptor< ELEMENT > {

  private ELEMENT element = null ;

  @Override
  public boolean add( final ELEMENT element ) {
    lock.lock() ;
    try {
      checkState( this.element == null, "Already contains {" + this.element + "}" ) ;
      this.element = toMagicNull( element ) ;
      condition.signalAll() ;
    } finally {
      lock.unlock() ;
    }
    return true ;
  }

  public ELEMENT getOrWait() throws InterruptedException {
    lock.lock() ;
    try {
      while( element == null ) {
        condition.await() ;
      }
      return fromMagicNull( element ) ;
    } finally {
      lock.unlock() ;
    }
  }

  @Override
  public ELEMENT get( int index ) {
    throw throwException() ;
  }

  @Override
  public boolean isEmpty() {
    return element == null ;
  }
}
