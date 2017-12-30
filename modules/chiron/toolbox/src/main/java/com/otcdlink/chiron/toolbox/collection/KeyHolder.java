package com.otcdlink.chiron.toolbox.collection;

import com.otcdlink.chiron.toolbox.ComparatorTools;
import com.otcdlink.chiron.toolbox.ToStringTools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.otcdlink.chiron.toolbox.text.TextTools.isBlank;

/**
 * Generic behavior for holders of strong-typed {@link Key}.
 */
public interface KeyHolder< KEY extends KeyHolder.Key< KEY > > {

  KEY key() ;

  class ComparatorByKey<
      KEYHOLDER extends KeyHolder< KEY >,
      KEY extends KeyHolder.Key< KEY >
  > extends ComparatorTools.WithNull< KEYHOLDER > {
    @Override
    protected int compareNoNulls( final KEYHOLDER first, final KEYHOLDER second ) {
      return first.key().compareTo( second.key() ) ;
    }
  }


// =========
// Key alone
// =========

  /**
   * One of the rare cases where we allow usage of {@code Comparable} which is flawed
   * by its single-cartridge approach (can't be {@code Comparable} for 2 things.
   * But supporting {@code Comparable} here allows generic implementation of
   * {@link ComparatorByKey}.
   *
   * @param <KEY> Concrete type.
   */
  interface Key< KEY extends Key > extends Comparable< KEY > { }

  abstract class LongKey<  KEY extends LongKey > implements Key< KEY > {
    private final long index ;

    protected LongKey( final long index ) {
      checkArgument( index >= 0 ) ;
      this.index = index ;
    }

    public final long index() {
      return index ;
    }

    public final String asString() {
      return Long.toString( index ) ;
    }

    @Override
    public int compareTo( final KEY other ) {
      if( other == null ) {
        return -1 ;
      } else {
        return index() > other.index() ? 1 : ( index() == other.index() ? 0 : -1 ) ;
      }
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" + index + '}' ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }

      if( other == null ) {
        return false ;
      }
      
      if( other.getClass() != this.getClass() ) {
        return false;
      }

      final LongKey that = ( LongKey ) other ;

      if( index != that.index ) {
        return false ;
      }

      return true ;
    }

    @Override
    public int hashCode() {
      return ( int ) index ;
    }
  }


  // TODO TODO TODO TODO
  abstract class StringKey<  KEY extends StringKey > implements Key< KEY > {
    private final String value;

    protected StringKey( final String value ) {
      checkArgument( ! isBlank( value ) ) ;
      this.value = value;
    }

    public String getValue() {
      return value ;
    }

    @Override
    public int compareTo( final KEY other ) {
      if( other == null ) {
        return -1 ;
      } else {
        return getValue().compareTo( other.getValue() ) ;
      }
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" + value + '}' ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }

      if( other == null ) {
        return false ;
      }

      if( other.getClass() != this.getClass() ) {
        return false;
      }

      final StringKey that = ( StringKey ) other;

      return value.equals( that.getValue() ) ;
    }

    @Override
    public int hashCode() {
      return value.hashCode() ;
    }
  }
}
