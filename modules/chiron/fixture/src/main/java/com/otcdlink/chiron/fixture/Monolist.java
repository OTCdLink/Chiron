package com.otcdlink.chiron.fixture;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A {@code List} which contains at most one element.
 * This is useful for capturing arguments with JMockit.
 *
 * @see BlockingMonolist
 */
@SuppressWarnings( "ClassExtendsConcreteCollection" )
public final class Monolist< ELEMENT > extends ArrayList< ELEMENT > {

  @Override
  public void add( final int index, final ELEMENT element ) {
    checkArgument( index == 0 , "Incorrect index: " + index ) ;
    checkState( isEmpty(), "Must be empty" ) ;
    super.add( index, element ) ;
  }

  @Override
  public boolean add( final ELEMENT element ) {
    checkState( isEmpty(), "Must be empty" ) ;
    return super.add( element ) ;
  }

  @Override
  public boolean addAll( final Collection< ? extends ELEMENT > collection ) {
    checkCollectionToAdd( collection ) ;
    return super.addAll( collection ) ;
  }

  @Override
  public boolean addAll( final int index, final Collection< ? extends ELEMENT > collection ) {
    checkCollectionToAdd( collection ) ;
    checkArgument( index == 0, "Incorrect index: " + index ) ;
    return super.addAll( index, collection ) ;
  }

  private void checkCollectionToAdd( final Collection<? extends ELEMENT > collection ) {
    checkNotNull( collection ) ;
    if( isEmpty() ) {
      checkArgument( collection.size() <= 1 ) ;
    } else {
      checkArgument(
          collection.isEmpty(),
          "Can only add empty collection to non-emty " + Monolist.class.getSimpleName() +
              ", trying to add " + collection
      ) ;
    }
  }

  /**
   * @throws AssertionError
   */
  public ELEMENT get() {
    if( size() != 1 ) {
      Assert.fail( "Must contain one element" ) ;
    }
    return get( 0 ) ;
  }

  public ELEMENT getOrNull() {
    if( isEmpty() ) {
      return null ;
    } else {
      return get( 0 ) ;
    }
  }

}
