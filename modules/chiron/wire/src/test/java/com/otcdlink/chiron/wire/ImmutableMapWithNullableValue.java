package com.otcdlink.chiron.wire;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

class ImmutableMapWithNullableValue< K, V > implements Map< K, V > {

  private final ImmutableMap< K, V > delegate ;

  private static final Object NULL = new Object() {
    @Override
    public String toString() {
      return ImmutableMapWithNullableValue.class.getSimpleName() + "{NULL}" ;
    }
  } ;

  public ImmutableMapWithNullableValue( final Map< K, V > mapWithNulls ) {
    final ImmutableMap.Builder< K, V > builder = ImmutableMap.builder() ;
    for( final Map.Entry< K, V > entry : mapWithNulls.entrySet() ) {
      builder.put( entry.getKey(), entry.getValue() == null ? ( V ) NULL : entry.getValue() ) ;
    }
    this.delegate = builder.build() ;
  }

  @Override
  public int size() {
    return delegate.size() ;
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty() ;
  }

  @Override
  public boolean containsKey( final Object key ) {
    return delegate.containsKey( key ) ;
  }

  @Override
  public boolean containsValue( final Object value ) {
    if( value == null ) {
      return delegate.containsValue( NULL ) ;
    } else {
      return delegate.containsValue( value ) ;
    }
  }

  @Override
  public V get( final Object key ) {
    final V value = delegate.get( key ) ;
    return value == NULL ? null : value ;
  }

  @Override
  @Deprecated
  public V put( final K key, final V value ) {
    throw new UnsupportedOperationException( "Immutable" ) ;
  }

  @Deprecated
  @Override
  public V remove( final Object key ) {
    throw new UnsupportedOperationException( "Immutable" ) ;
  }

  @Deprecated
  @Override
  public void putAll( final Map< ? extends K, ? extends V > m ) {
    throw new UnsupportedOperationException( "Immutable" ) ;

  }

  @Override
  @Deprecated
  public void clear() {
    throw new UnsupportedOperationException( "Immutable" ) ;
  }

  @Override
  @Nonnull
  public ImmutableSet< K > keySet() {
    return delegate.keySet() ;
  }

  /**
   * Returns a mutable {@code List} because we have no {@code ImmutableListWithNullableItem} yet.
   */
  @Override
  @Nonnull
  public Collection< V > values() {
    return delegate.values().stream()
        .map( v -> v == NULL ? null : v ).collect( Collectors.toList() ) ;
  }

  @Override
  @Nonnull
  public ImmutableSet< Entry< K, V > > entrySet() {
    return delegate.entrySet().stream()
        .map( e -> new AbstractMap.SimpleImmutableEntry<>( e.getKey(), e.getValue() ) )
        .collect( Collector.of(
            ImmutableSet::builder,
            ( BiConsumer< ImmutableSet.Builder< Entry< K, V > >, Entry< K, V > > )
                ImmutableSet.Builder::add,
            ( builder1, builder2 ) -> builder1,
            ImmutableSet.Builder::build
        ) )
    ;
  }

  @Override
  public String toString() {
    return ImmutableMapWithNullableValue.class.getSimpleName() + "{[" +
        Joiner.on( "," ).withKeyValueSeparator( ":" ).join( delegate ) +
        "]}"
    ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }
    final ImmutableMapWithNullableValue< ?, ? > that = ( ImmutableMapWithNullableValue< ?, ? > )
        other ;
    return Objects.equals( delegate, that.delegate ) ;
  }

  @Override
  public int hashCode() {
    return Objects.hash( delegate ) ;
  }
}
