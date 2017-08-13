package com.otcdlink.chiron.toolbox;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class StringWrapper< WRAPPED extends StringWrapper< WRAPPED > >
    implements Comparable< WRAPPED >
{

  protected final String wrapped ;

  protected StringWrapper( final String wrapped ) {
    this.wrapped = checkNotNull( wrapped ) ;
  }

  protected final String getWrappedString() {
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

    final StringWrapper objectWrapper = ( StringWrapper ) other ;

    if( ! wrapped.equals( objectWrapper.wrapped ) ) {
      return false ;
    }

    return true ;
  }

  @Override
  public int hashCode() {
    return wrapped.hashCode() ;
  }

  @Override
  public int compareTo( final WRAPPED other ) {
    return GENERIC_COMPARATOR.compare( this, other ) ;
  }

  private static final WrapperComparator< StringWrapper > GENERIC_COMPARATOR =
      new WrapperComparator<>() ;

  public static class WrapperComparator< WRAPPER extends StringWrapper >
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
      return wrapper1.getWrappedString().compareTo( wrapper2.getWrappedString() ) ;
    }
  }
}
