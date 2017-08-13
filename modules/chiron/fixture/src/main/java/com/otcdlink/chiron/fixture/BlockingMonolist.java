package com.otcdlink.chiron.fixture;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@code List} which contains at most one element.
 * This is useful for capturing arguments with JMockit. Using {@link #getOrWait()} we can wait
 * for the effective call of the method, and got passed argument at once.
 * 
 * @see Monolist
 */
public final class BlockingMonolist< ELEMENT > implements List< ELEMENT > {

  private ELEMENT element = null ;

  private final Lock lock = new ReentrantLock() ;
  private final Condition condition = lock.newCondition() ;

  private static final Object NULL = new Object() {
    @Override
    public String toString() {
      return BlockingMonolist.class.getSimpleName() + "#NULL{}" ;
    }
  } ;

  @Override
  public boolean add( final ELEMENT element ) {
    lock.lock() ;
    try {
      checkState( this.element == null ) ;
      this.element = element == null ? ( ELEMENT ) NULL : element ;
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
      return element == NULL ? null : element ;
    } finally {
      lock.unlock() ;
    }
  }
  
  private static RuntimeException throwException() {
    throw new UnsupportedOperationException( "Do not call" ) ;
  }



// ==============
// List interface
// ==============


  @Override
  public int size() {
    throw throwException() ;
  }

  @Override
  public boolean isEmpty() {
    throw throwException() ;
  }

  @Override
  public boolean contains( final Object o ) {
    throw throwException() ;
  }

  @Override
  public Iterator< ELEMENT > iterator() {
    throw throwException() ;
  }

  @Override
  public Object[] toArray() {
    throw throwException() ;
  }

  @Override
  public < T > T[] toArray( final T[] a ) {
    throw throwException() ;
  }

  @Override
  public boolean remove( final Object o ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public boolean containsAll( final Collection< ? > c ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public boolean addAll( final Collection< ? extends ELEMENT > c ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public boolean addAll( final int index, final Collection< ? extends ELEMENT > c ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public boolean removeAll( final Collection< ? > c ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public boolean retainAll( final Collection< ? > c ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public ELEMENT get( final int index ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public ELEMENT set( final int index, final ELEMENT element ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public void add( final int index, final ELEMENT element ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public ELEMENT remove( final int index ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public int indexOf( final Object o ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public int lastIndexOf( final Object o ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public ListIterator< ELEMENT > listIterator() {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public ListIterator< ELEMENT > listIterator( final int index ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public List< ELEMENT > subList( final int fromIndex, final int toIndex ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }
}
