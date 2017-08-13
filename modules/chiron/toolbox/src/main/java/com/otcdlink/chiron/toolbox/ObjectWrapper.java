package com.otcdlink.chiron.toolbox;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @deprecated use {@link StringWrapper}
 */
public abstract class ObjectWrapper< WRAPPED extends Comparable< WRAPPED > > {

  private final WRAPPED wrapped ;

  public ObjectWrapper( final WRAPPED wrapped ) {
    this.wrapped = checkNotNull( wrapped ) ;
  }

  protected final WRAPPED getWrappedObject() {
    return wrapped ;
  }



  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + wrapped + "}" ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }

    if( other == null ) {
      return false ;
    }

    if( other.getClass() != getClass() ) {
      return false ;
    }

    final ObjectWrapper objectWrapper = ( ObjectWrapper ) other ;

    if( ! wrapped.equals( objectWrapper.wrapped ) ) {
      return false ;
    }

    return true ;
  }

  @Override
  public int hashCode() {
    return wrapped.hashCode() ;
  }

  public static class WrapperComparator< WRAPPER extends ObjectWrapper >
      implements Comparator< WRAPPER >
  {

    @Override
    public int compare( final WRAPPER wrapper1, final WRAPPER wrapper2 ) {
      if( wrapper1 == null ) {
        if( wrapper2 == null ) {
          return 0 ;
        } else {
          return -1 ;
        }
      } else {
        if( wrapper2 == null ) {
          return -1 ;
        } else {
          return compareNoNulls( wrapper1, wrapper2 ) ;
        }
      }
    }

    protected int compareNoNulls( final WRAPPER wrapper1, final WRAPPER wrapper2 ) {
      return wrapper1.getWrappedObject().compareTo( wrapper2.getWrappedObject() ) ;
    }
  }
}
