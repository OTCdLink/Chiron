package com.otcdlink.chiron.fixture;

import com.otcdlink.chiron.toolbox.ToStringTools;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Narrows down {@code List} contract to support addition and querying.
 */
public abstract class AbstractCaptor< ELEMENT > implements List< ELEMENT > {

  protected final Lock lock = new ReentrantLock() ;
  protected final Condition condition = lock.newCondition() ;

  private static final Object NULL = new Object() {
    @Override
    public String toString() {
      return BlockingMonolist.class.getSimpleName() + "#NULL{}" ;
    }
  } ;


  protected static < ELEMENT > ELEMENT toMagicNull( ELEMENT element ) {
    return element == null ? ( ELEMENT ) NULL : element ;
  }

  protected static < ELEMENT > ELEMENT fromMagicNull( final ELEMENT element ) {
    return element == NULL ? null : element;
  }


  @Override
  public abstract boolean add( ELEMENT element ) ;

  @Override
  public String toString() {
    return ToStringTools.nameAndHash( this ) + "{}" ;
  }

  protected static RuntimeException throwException() {
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
  public boolean contains( final Object o ) {
    throw throwException() ;
  }

  @Nonnull
  @Override
  public Iterator< ELEMENT > iterator() {
    throw throwException() ;
  }

  @Nonnull
  @Override
  public Object[] toArray() {
    throw throwException() ;
  }

  @Nonnull
  @Override
  public < T > T[] toArray( @Nonnull final T[] a ) {
    throw throwException() ;
  }

  @Override
  public boolean remove( final Object o ) {
    throw throwException() ;
  }

  @Override
  public boolean containsAll( @Nonnull final Collection< ? > c ) {
    throw throwException() ;
  }

  @Override
  public boolean addAll( @Nonnull final Collection< ? extends ELEMENT > c ) {
    throw throwException() ;
  }

  @Override
  public boolean addAll( final int index, @Nonnull final Collection< ? extends ELEMENT > c ) {
    throw throwException() ;
  }

  @Override
  public boolean removeAll( @Nonnull final Collection< ? > c ) {
    throw throwException() ;
  }

  @Override
  public boolean retainAll( @Nonnull final Collection< ? > c ) {
    throw throwException() ;
  }

  @Override
  public void clear() {
    throw throwException() ;
  }

  @Override
  public ELEMENT set( final int index, final ELEMENT element ) {
    throw throwException() ;
  }

  @Override
  public void add( final int index, final ELEMENT element ) {
    throw throwException() ;
  }

  @Override
  public ELEMENT remove( final int index ) {
    throw throwException() ;
  }

  @Override
  public int indexOf( final Object o ) {
    throw throwException() ;
  }

  @Override
  public int lastIndexOf( final Object o ) {
    throw throwException() ;
  }

  @Nonnull
  @Override
  public ListIterator< ELEMENT > listIterator() {
    throw throwException() ;
  }

  @Nonnull
  @Override
  public ListIterator< ELEMENT > listIterator( final int index ) {
    throw throwException() ;
  }

  @Nonnull
  @Override
  public final List< ELEMENT > subList( final int fromIndex, final int toIndex ) {
    throw throwException() ;
  }  
}
