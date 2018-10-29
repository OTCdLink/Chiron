package com.otcdlink.chiron.toolbox.collection;

import com.google.common.primitives.Longs;
import com.otcdlink.chiron.toolbox.ComparatorTools;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Objects;

class ImmutableLongKeyHolderMapFixture {
  static AbstractMap.SimpleImmutableEntry< Entity.Key, Entity > asEntry(
      final Entity entity
  ) {
    return new AbstractMap.SimpleImmutableEntry<>( entity.key, entity ) ;
  }

  public static final class Entity implements KeyHolder< Entity.Key > {

    public static final class Key extends KeyHolder.LongKey< Key > {
      public Key( final long index ) {
        super( index ) ;
      }

      @Override
      public String toString() {
        return getClass().getSimpleName() + "{" + index() + "}" ;
      }
    }

    private final Key key ;

    public Entity( final long index ) {
      this.key = new Key( index ) ;
    }

    @Override
    public Key key() {
      return key ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final Entity entity = ( Entity ) other ;
      return Objects.equals( key, entity.key ) ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( key ) ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{" + key.index() + "}" ;
    }

    public static final Comparator< Entity > COMPARATOR =
        new ComparatorTools.WithNull< Entity >() {
          @Override
          protected int compareNoNulls( final Entity first, final Entity second ) {
            return Longs.compare( first.key().index(), second.key().index() ) ;
          }
        }
    ;
  }
}
