package com.otcdlink.chiron;

import com.otcdlink.chiron.fixture.AbstractCaptor;
import com.otcdlink.chiron.toolbox.ToStringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public final class Multicaptor< ELEMENT > extends AbstractCaptor< ELEMENT > {

  private static final Logger LOGGER = LoggerFactory.getLogger( Multicaptor.class ) ;

  private final int capacity ;
  private final List< ELEMENT > list ;

  public Multicaptor( final int capacity ) {
    list = new ArrayList<>( capacity ) ;
    this.capacity = capacity ;
  }

  @Override
  public boolean add( ELEMENT element ) {
    if( element == null ) {
      LOGGER.debug( "Dismissing null in " + this + "." ) ;
      return false ;
    }
    lock.lock() ;
    try {
      checkState( list.size() < capacity ) ;
      LOGGER.debug( "Adding " + element + " to " + this + "." ) ;
      list.add( toMagicNull( element ) ) ;
      condition.signalAll() ;
      return true ;
    } finally {
      lock.unlock() ;
    }
  }

  @Override
  public ELEMENT get( final int index ) {
    checkArgument( index >= 0 ) ;
    lock.lock() ;
    try {
      while( list.size() <= index ) {
        try {
          condition.await() ;
        } catch( InterruptedException e ) {
          throw new RuntimeException( e ) ;
        }
      }
      return fromMagicNull( list.get( index ) ) ;
    } finally {
      lock.unlock() ;
    }
  }

  @Override
  public final int size() {
    lock.lock() ;
    try {
      return list.size() ;
    } finally {
      lock.unlock() ;
    }
  }

  @Override
  public final String toString() {
    lock.lock() ;
    try {
      return ToStringTools.nameAndHash( this ) + "{" + list + "}" ;
    } finally {
      lock.unlock() ;
    }
  }

  @Override
  public boolean isEmpty() {
    lock.lock() ;
    try {
      return list.isEmpty() ;
    } finally {
      lock.unlock() ;
    }
  }
}
