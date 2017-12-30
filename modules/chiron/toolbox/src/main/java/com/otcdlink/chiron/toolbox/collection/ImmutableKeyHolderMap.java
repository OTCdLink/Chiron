package com.otcdlink.chiron.toolbox.collection;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Function;
import java.util.stream.Collector;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@code Map} that guarantees that the key for each value is a key extracted from the value
 * itself.
 * Element ordering respects the order in which elements were added.
 * This is useful for (yet to come) internal optimisations or implementations of methods like
 * {@link #reverseOrder()}.
 *
 * Current implementation relies on delegating to an {@link ImmutableMap}.
 * It might be faster/more memory-efficient to keep an array of values, with a sequential
 * search on the keys.
 */
public final class ImmutableKeyHolderMap<
    KEY extends KeyHolder.Key< KEY >,
    VALUE extends KeyHolder< KEY >
> implements Map< KEY, VALUE > {


  /**
   * This is the cheap approach that wastes some memory.
   */
  private final ImmutableMap< KEY, VALUE > delegate ;

  private ImmutableKeyHolderMap( final ImmutableMap< KEY, VALUE> delegate ) {
    this.delegate = delegate;
  }

  public ImmutableSet< VALUE > valuesAsImmutableSet() {
    final ImmutableSet.Builder< VALUE > builder = ImmutableSet.builder() ;
    values().forEach( builder::add ) ;
    return builder.build() ;
  }


// =======================
// Classical factory stuff
// =======================

  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > of() {
    return new ImmutableKeyHolderMap<>( ImmutableMap.< KEY, VALUE >of() ) ;
  }

  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > of( final VALUE v0 ) {
    return new ImmutableKeyHolderMap< KEY, VALUE >( ImmutableMap.of( v0.key(), v0 ) ) ;
  }

  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > of( final VALUE v0, final VALUE v1  ) {
    return new ImmutableKeyHolderMap< KEY, VALUE >(
        ImmutableMap.of( v0.key(), v0, v1.key(), v1 ) ) ;
  }

  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > of( final VALUE v0, final VALUE v1, final VALUE v2  ) {
    return new ImmutableKeyHolderMap<>( ImmutableMap.of(
        v0.key(), v0,
        v1.key(), v1,
        v2.key(), v2
    ) ) ;
  }

  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > of(
      final VALUE v0,
      final VALUE v1,
      final VALUE v2,
      final VALUE v3
  ) {
    return new ImmutableKeyHolderMap<>( ImmutableMap.of(
        v0.key(), v0,
        v1.key(), v1,
        v2.key(), v2,
        v3.key(), v3
    ) ) ;
  }

  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > of(
      final VALUE[] values
  ) {
    final ImmutableKeyHolderMap.Builder< KEY, VALUE > builder = builder() ;
    for( final VALUE value : values ) {
      builder.put( value ) ;
    }
    return builder.build() ;
  }


  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > copyOf( final Map< KEY, VALUE > map ) {
    return new ImmutableKeyHolderMap( ImmutableMap.copyOf( map ) ) ;
  }

  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > copyOf( final Set< VALUE > set ) {
    final ImmutableKeyHolderMap.Builder< KEY, VALUE > builder = builder() ;
    for( final VALUE value : set ) {
      builder.put( value ) ;
    }
    return builder.build() ;
  }

  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > copyOf( final KeyHolderMap< KEY, VALUE > map ) {
    return new ImmutableKeyHolderMap<>( ImmutableMap.< KEY, VALUE >copyOf( map ) ) ;
  }

  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  >ImmutableKeyHolderMap< KEY, VALUE > copyOfSortedMap( final SortedMap< KEY, VALUE > map ) {
    return new ImmutableKeyHolderMap<>( ImmutableMap.< KEY, VALUE >copyOf( map ) ) ;
  }


// ===============
// Ancillary tools
// ===============


  public static<
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  > Builder< KEY, VALUE > builder() {
    return new Builder<>() ;
  }

  public static final class Builder<
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  > {

    private final ImmutableMap.Builder< KEY, VALUE > delegate ;

    private Builder() {
      delegate = new ImmutableMap.Builder<>() ;
    }

    public Builder< KEY, VALUE > put( final VALUE value ) {
      delegate.put( value.key(), value ) ;
      return this ;
    }

    public Builder< KEY, VALUE > putAll( final Iterable< VALUE > values ) {
      for( final VALUE value : values ) {
        put( value ) ;
      }
      return this ;
    }

    public ImmutableKeyHolderMap< KEY, VALUE > build() {
      return new ImmutableKeyHolderMap<>( delegate.build() ) ;
    }

    /**
     * TODO: avoid creating {@link ImmutableKeyHolderMap}s.
     */
    public static <
        V extends KeyHolder< K >,
        K extends KeyHolder.Key< K >
    > Builder< K, V > combine( final Builder< K, V > builder1, Builder< K, V > builder2
    ) {
      final Builder< K, V > combinator = builder();
      combinator.putAll( builder1.build().values() ) ;
      combinator.putAll( builder2.build().values() ) ;
      return combinator ;
    }
  }


  public static <
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >
  > ImmutableKeyHolderMap< KEY, VALUE >
  withSortedKeys(
      final ImmutableKeyHolderMap< KEY, VALUE > original
  ) {
    List< KEY > keys = new ArrayList<>( original.keySet() ) ;
    Collections.sort( keys ) ;
    final ImmutableKeyHolderMap.Builder< KEY, VALUE > builder = ImmutableKeyHolderMap.builder() ;
    for( final KEY key : keys ) {
      builder.put( original.get( key ) ) ;
    }
    return builder.build() ;
  }

// =====================
// Nice additional stuff
// =====================

  public ImmutableKeyHolderMap< KEY, VALUE > reverseOrder() {
    final ArrayList< VALUE > valuesAsList = Lists.newArrayList( values() ) ;
    Collections.reverse( valuesAsList ) ;
    final Builder< KEY, VALUE > builder = ImmutableKeyHolderMap.builder() ;
    builder.putAll( valuesAsList ) ;
    return builder.build() ;
  }

  public ImmutableKeyHolderMap< KEY, VALUE > retainMatchingValues(
      final Predicate< VALUE > predicate
  ) {
    checkNotNull( predicate ) ;
    final Builder< KEY, VALUE > builder = ImmutableKeyHolderMap.builder() ;
    for( final VALUE value : values() ) {
      if( predicate.apply( value ) ) {
        builder.put( value ) ;
      }
    }
    return builder.build() ;
  }

  public ImmutableKeyHolderMap< KEY, VALUE > add( final VALUE value ) {
    checkArgument( ! delegate.containsKey( value.key() ) ) ;
    final Builder< KEY, VALUE > builder = ImmutableKeyHolderMap.builder() ;
    builder.putAll( delegate.values() ) ;
    builder.put( value ) ;
    return builder.build() ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + Joiner.on( ", " ).join( values() ) + "}" ;
  }

  static <
      K extends KeyHolder.Key< K >,
      V extends KeyHolder< K >
  > Collector< V, ?, ImmutableKeyHolderMap< K, V > >
  toImmutableKeyHolderMap(
  ) {
    return toImmutableKeyHolderMap( v -> v ) ;
  }

  static <
      T,
      K extends KeyHolder.Key< K >,
      V extends KeyHolder< K >
  > Collector< T, ?, ImmutableKeyHolderMap< K, V > >
  toImmutableKeyHolderMap(
      Function< ? super T, ? extends V > valueFunction
  ) {
    checkNotNull( valueFunction ) ;
    return Collector.of(
        ImmutableKeyHolderMap.Builder< K, V >::new,
        ( builder, input ) -> {
          final V value = valueFunction.apply( input ) ;
          builder.put( value ) ;
        },
        ImmutableKeyHolderMap.Builder::combine,
        ImmutableKeyHolderMap.Builder::build
    ) ;
  }



// ========================
// Methods of Map interface
// ========================


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
    return delegate.containsValue( value ) ;
  }

  @Override
  public VALUE get( final Object key ) {
    return delegate.get( key ) ;
  }

  @Override
  public VALUE put( final KEY key, final VALUE value ) {
    return delegate.put( key, value ) ;
  }

  @Override
  public VALUE remove( final Object key ) {
    return delegate.remove( key ) ;
  }

  @Override
  public void putAll( final Map< ? extends KEY, ? extends VALUE > otherMap ) {
    delegate.putAll( otherMap ) ;
  }

  @Override
  public void clear() {
    delegate.clear() ;
  }

  @Override
  public ImmutableSet< KEY > keySet() {
    return delegate.keySet() ;
  }

  @Override
  public ImmutableCollection< VALUE > values() {
    return delegate.values() ;
  }

  @Override
  public ImmutableSet< Entry< KEY,VALUE > > entrySet() {
    return delegate.entrySet() ;
  }

  @Override
  public boolean equals( final Object other ) {
    return delegate.equals( other ) ;
  }

  @Override
  public int hashCode() {
    return delegate.hashCode() ;
  }


}
