package com.otcdlink.chiron.toolbox.collection;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.toolbox.ComparatorTools;
import com.otcdlink.chiron.toolbox.random.UniqueLongGenerator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.stream.Collector;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Immutable Map keeping values ordered by their {@link KEY}'s {@link KeyHolder.LongKey#index()}.
 * This class doesn't support {@code null} values and doesn't retain addition ordering.
 * Declaring a {@link KEY} type parameter instead of using a {@code Long} encourages dedicated,
 * strongly-typed keys.
 */
public final class ImmutableLongKeyHolderMap<
    KEY extends KeyHolder.LongKey< KEY >,
    VALUE extends KeyHolder< KEY >
> implements Map< KEY, VALUE > {

  private final Object[] values ;
  private final boolean guavaAutomaticTests ;

  public ImmutableLongKeyHolderMap(
      final Collection< Map.Entry< KEY, VALUE > > collection,
      final boolean guavaAutomaticTests
  ) {
    this.values = new Object[ collection.size() ] ;
    this.guavaAutomaticTests = guavaAutomaticTests ;
    int position = 0 ;
    for( final Map.Entry< KEY, VALUE > entry : collection ) {
      checkArgument( entry.getKey().equals( entry.getValue().key() ),
          "Inconsistent entry: " + entry ) ;
      checkNotNull( entry.getKey() ) ;
      checkNotNull( entry.getValue() ) ;
      values[ position ++ ] = entry.getValue() ;
    }
    sortValuesAndVerifyNoDuplicates() ;
  }

  public ImmutableLongKeyHolderMap( final Collection< VALUE > collection ) {
    this.values = new Object[ collection.size() ] ;
    this.guavaAutomaticTests = false ;
    int position = 0 ;
    for( final VALUE entry : collection ) {
      values[ position ++ ] = checkNotNull( entry ) ;
    }
    sortValuesAndVerifyNoDuplicates() ;
  }

  private void sortValuesAndVerifyNoDuplicates() {
    int position;
    Arrays.sort( values, ( Comparator ) COMPARATOR_BY_LONG_KEY ) ;
    VALUE last = null ;
    for( position = 0; position < values.length ; position++ ) {
      VALUE current = valueAtPosition( position ) ;
      if( last != null ) {
        checkArgument( last.key().index() < current.key().index(),
            "Collision: " + current + " and " + last ) ;
      }
      last = current ;
    }
  }

  private ImmutableLongKeyHolderMap( final Object[] values ) {
    this.values = values ;
    guavaAutomaticTests = false ;
  }


// =========
// Structure
// =========

  private static final Comparator< KeyHolder< ? extends KeyHolder.LongKey > >
      COMPARATOR_BY_LONG_KEY =
          new ComparatorTools.WithNull< KeyHolder< ? extends KeyHolder.LongKey > >() {
            @Override
            protected int compareNoNulls(
                KeyHolder< ? extends KeyHolder.LongKey > first,
                KeyHolder< ? extends KeyHolder.LongKey > second
            ) {
              return Long.compare( first.key().index(), second.key().index() ) ;
            }
          }
  ;

  /**
   * An immutable {@link Map.Entry} keeping only a reference to the {@link VALUE}.
   */
  public static final class Entry<
      KEY extends KeyHolder.LongKey< KEY >,
      VALUE extends KeyHolder< KEY >
  > implements Map.Entry< KEY, VALUE > {

    private final VALUE value ;

    public Entry( final VALUE value ) {
      this.value = checkNotNull( value ) ;
    }

    @Override
    public KEY getKey() {
      return value.key() ;
    }

    @Override
    public VALUE getValue() {
      return value ;
    }

    @Override
    public VALUE setValue( VALUE value ) {
      throw new UnsupportedOperationException( "Unsupported" ) ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final Entry< ?, ? > entry = ( Entry< ?, ? > ) other ;
      return Objects.equals( value, entry.value ) ;
    }

    @Override
    public int hashCode() {
      final KEY key = getKey() ;
      final VALUE value = getValue() ;
      return computeEntryHashCode( key, value ) ;
    }

    /**
     * This is defined by the {@link java.util.Map} contract and enforced by Guava testers.
     */
    private static <
        KEY extends KeyHolder.LongKey< KEY >,
        VALUE extends KeyHolder< KEY >
    > int computeEntryHashCode( KEY key, VALUE value ) {
      return ( key == null ? 0 : key.hashCode() ) ^
          ( value == null ? 0 : value.hashCode() ) ;
    }

    @Override
    public String toString() {
      return getKey() + "=" + getValue() ;
    }
  }


// ===============
// Factory methods
// ===============

  private static final ImmutableLongKeyHolderMap EMPTY_KEY_HOLDER_MAP =
      new ImmutableLongKeyHolderMap( new Object[ 0 ] ) ;

  public static <
      KEY extends KeyHolder.LongKey< KEY >,
      VALUE extends KeyHolder< KEY >
  > ImmutableLongKeyHolderMap< KEY, VALUE > of() {
    return ( ImmutableLongKeyHolderMap< KEY, VALUE > ) EMPTY_KEY_HOLDER_MAP ;
  }

  public static <
      KEY extends KeyHolder.LongKey< KEY >,
      VALUE extends KeyHolder< KEY >
  > ImmutableLongKeyHolderMap< KEY, VALUE > of( final VALUE... values ) {
    final Builder< KEY, VALUE > builder = builder() ;
    for( final VALUE v : values ) {
      builder.put( v ) ;
    }
    return builder.build() ;
  }

  public static <
      KEY extends KeyHolder.LongKey< KEY >,
      VALUE extends KeyHolder< KEY >
  > ImmutableLongKeyHolderMap< KEY, VALUE > of( final VALUE v1 ) {
    final Builder< KEY, VALUE > builder = builder() ;
    return builder.put( v1 ).build() ;
  }

  public static <
      KEY extends KeyHolder.LongKey< KEY >,
      VALUE extends KeyHolder< KEY >
  > ImmutableLongKeyHolderMap< KEY, VALUE > copyOf( final Collection< VALUE > values ) {
    final Builder< KEY, VALUE > builder = builder() ;
    return builder.putAll( values ).build() ;
  }

  public static final class Builder<
      KEY extends KeyHolder.LongKey< KEY >,
      VALUE extends KeyHolder< KEY >
      > {

    private final List< VALUE > entries = new ArrayList<>() ;

    public Builder< KEY, VALUE > put( final VALUE value ) {
      entries.add( checkNotNull( value ) ) ;
      return this ;
    }

    public Builder< KEY, VALUE > putAll( final Iterable< VALUE > values ) {
      for( final VALUE value : values ) {
        put( value ) ;
      }
      return this ;
    }

    public ImmutableLongKeyHolderMap< KEY, VALUE > build() {
      return new ImmutableLongKeyHolderMap<>( entries ) ;
    }

    /**
     * TODO: avoid creating {@link ImmutableKeyHolderMap}s.
     */
    public static <
        K extends KeyHolder.LongKey< K >,
        V extends KeyHolder< K >
        > Builder< K, V > combine( final Builder< K, V > builder1, Builder< K, V > builder2
    ) {
      final Builder< K, V > combinator = builder() ;
      combinator.putAll( builder1.build().values() ) ;
      combinator.putAll( builder2.build().values() ) ;
      return combinator ;
    }
  }

  public static <
      K extends KeyHolder.LongKey< K >,
      V extends KeyHolder< K >
  > Builder< K, V > builder() {
    return new Builder<>() ;
  }

// =================
// Key index lookups
// =================

  private VALUE valueAtPosition( final int position ) {
    return ( VALUE ) values[ position ] ;
  }

  /**
   * @see UniqueLongGenerator#nextRandomValue(LongPredicate)
   */
  public boolean containsKeyWithIndex( final long index ) {
    return getByKeyIndex( index ) != null ;
  }

  /**
   * Copied from {@link Arrays#binarySearch(Object[], Object, Comparator)}.
   *
   * @param index must be greater than 0 and smaller than {@link #size()}.
   * @return {@code null} if not found, the value otherwise.
   */
  public VALUE getByKeyIndex( final long index ) {
    checkArgument( index >= 0 ) ;

    final int keyIndexPosition = keyIndexPosition( index ) ;
    if( keyIndexPosition < 0 ) {
      return null ;
    } else {
      return valueAtPosition( keyIndexPosition ) ;
    }
  }

  /**
   * Copied from {@link Arrays#binarySearch(Object[], Object, Comparator)}.
   *
   * @return index of the search key, if it is contained in the array;
   *     otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>.  The
   *     <i>insertion point</i> is defined as the point at which the
   *     key would be inserted into the array: the index of the first
   *     element greater than the key, or <tt>a.length</tt> if all
   *     elements in the array are less than the specified key.  Note
   *     that this guarantees that the return value will be &gt;= 0 if
   *     and only if the key is found.
   */
  private int keyIndexPosition( final long index ) {
    int low = 0 ;
    int high = values.length - 1 ;

    while( low <= high ) {
      final int mid = ( low + high ) >>> 1 ;
      final VALUE midVal = valueAtPosition( mid ) ;
      int cmp = Long.compare( midVal.key().index(), index ) ;
      if( cmp < 0 ){
        low = mid + 1 ;
      } else if( cmp > 0 ) {
        high = mid - 1 ;
      } else {
        return mid ;
      }
    }
    return -( low + 1 ) ;
  }

// ==============
// Copy-on-change
// ==============

  public ImmutableLongKeyHolderMap< KEY, VALUE > copyReplace( final VALUE value ) {
    final int position = keyIndexPosition( value.key().index() );
    if( position < 0 ) {
      throw new IllegalArgumentException( "No existing key for " + value ) ;
    }
    final Object[] newArray = new Object[ values.length ] ;
    System.arraycopy( values, 0, newArray, 0, values.length ) ;
    newArray[ position ] = value ;
    return new ImmutableLongKeyHolderMap<>( newArray ) ;
  }

  public ImmutableLongKeyHolderMap< KEY, VALUE > copyAdd( final VALUE value ) {
    return copyAdd(
        value,
        v -> new IllegalArgumentException(
            "Value already at " + v.key().index() + ", currently " + v )
    ) ;
  }

  public < EXCEPTION extends Exception > ImmutableLongKeyHolderMap< KEY, VALUE > copyAdd(
      final VALUE value,
      final Function< VALUE, EXCEPTION > exceptionThrower
  ) throws EXCEPTION {
    final int position = keyIndexPosition( value.key().index() ) ;
    if( position < 0 ) {
      final int insertionPoint = - position - 1 ;
      final Object[] newArray = new Object[ values.length + 1 ] ;
      System.arraycopy( values, 0, newArray, 0, insertionPoint ) ;
      newArray[ insertionPoint ] = value ;
      System.arraycopy(
          values, insertionPoint, newArray, insertionPoint + 1, values.length - insertionPoint ) ;
      return new ImmutableLongKeyHolderMap<>( newArray ) ;
    } else {
      throw exceptionThrower.apply( valueAtPosition( position )  ) ;
    }
  }


// ===========
// Collections
// ===========

  public static <
      K extends KeyHolder.LongKey< K >,
      V extends KeyHolder< K >
  > Collector< V, ?, ImmutableLongKeyHolderMap< K, V > >
  toImmutableKeyHolderMap() {
    return toImmutableKeyHolderMap( v -> v ) ;
  }

  public static <
      T,
      K extends KeyHolder.LongKey< K >,
      V extends KeyHolder< K >
  > Collector< T, ?, ImmutableLongKeyHolderMap< K, V > >
  toImmutableKeyHolderMap(
      Function< ? super T, ? extends V > valueFunction
  ) {
    checkNotNull( valueFunction ) ;
    return Collector.of(
        ImmutableLongKeyHolderMap.Builder< K, V >::new,
        ( builder, input ) -> {
          final V value = valueFunction.apply( input ) ;
          builder.put( value ) ;
        },
        ImmutableLongKeyHolderMap.Builder::combine,
        ImmutableLongKeyHolderMap.Builder::build
    ) ;
  }


// ===============
// Object contract
// ===============

  @Override
  public String toString() {
    return
        // Guava testers enforces toString.
        /*ImmutableLongKeyHolderMap.class.getSimpleName() +*/ '{' +
        Joiner.on( ", " ).withKeyValueSeparator( '=' ).join( entrySet() ) +
        '}'
    ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null ) {
      return false ;
    }
    if( other instanceof Map ) {
      final Map thatMap = ( Map ) other ;
      if( thatMap.size() == size() ) {
        for( int position = 0 ; position < values.length ; position ++ ) {
          final VALUE value = valueAtPosition( position ) ;
          final Object thatValue = thatMap.get( value.key() );
          if( ! value.equals( thatValue ) ) {
            return false ;
          }
        }
      } else {
        return false ;
      }
      return true ;
    }
    return false ;
  }

  @Override
  public int hashCode() {
    int hashcode = 0 ;
    for( int position = 0 ; position < values.length ; position ++ ) {
      final VALUE value = valueAtPosition( position ) ;
      hashcode += Entry.computeEntryHashCode( value.key(), value ) ;
    }
    return hashcode ;
  }


// ============
// Map contract
// ============

  @Override
  public int size() {
    return values.length ;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0 ;
  }

  @Override
  public boolean containsKey( final Object key ) {
    if( key instanceof KeyHolder.LongKey ) {
      return getByKeyIndex( ( ( KeyHolder.LongKey ) key ).index() ) != null ;
    } else {
      return false ;
    }
  }

  @Override
  public boolean containsValue( Object value ) {
    if( value instanceof KeyHolder ) {
      final KeyHolder.Key key = ( ( KeyHolder ) value ).key() ;
      if( key instanceof KeyHolder.LongKey ) {
        return containsKey( key ) ;
      }
    }
    return false ;
  }

  @Override
  public VALUE get( final Object key ) {
    if( key instanceof KeyHolder.LongKey ) {
      return getByKeyIndex( ( ( KeyHolder.LongKey ) key ).index() ) ;
    }
    return null ;
  }

  /**
   * Unsynchronized access to be faster, two concurrent read on an unintialized value may
   * trigger two constructions, but it is safe because the construction result is always the same.
   */
  private ImmutableSet< KEY > keySet = null ;

  @Override
  public @Nonnull ImmutableSet< KEY > keySet() {
    if( keySet == null ) {
      final ImmutableSet.Builder< KEY > builder = ImmutableSet.builder() ;
      for( int position = 0 ; position < values.length ; position ++ ) {
        final VALUE value = valueAtPosition( position ) ;
        builder.add( value.key() ) ;
      }
      keySet = builder.build() ;
    }
    return keySet ;
  }

  /**
   * Unsynchronized access to be faster, two concurrent read on an unintialized value may
   * trigger two constructions, but it is safe because the construction result is always the same.
   */
  private ImmutableCollection< VALUE > valuesCollection = null ;

  @Override
  public @Nonnull ImmutableCollection< VALUE > values() {
    if( valuesCollection == null ) {
      valuesCollection = ( ImmutableCollection< VALUE > ) ( ImmutableList )
          ImmutableList.copyOf( values ) ;
    }
    return valuesCollection ;
  }

  /**
   * Unsynchronized access to be faster, two concurrent read on an unintialized value may
   * trigger two constructions, but it is safe because the construction result is always the same.
   */
  private Set< Map.Entry< KEY, VALUE > > entrySet ;

  private final ComparatorTools.WithNull< Map.Entry< KEY, VALUE > > entryComparatorByKey =
      new ComparatorTools.WithNull< Map.Entry< KEY, VALUE > >() {
        @Override
        protected int compareNoNulls(
            final Map.Entry< KEY, VALUE > first,
            final Map.Entry< KEY, VALUE > second
        ) {
          return Long.compare( first.getKey().index(), second.getKey().index() );
        }
      }
  ;

  @Override
  public @Nonnull Set< Map.Entry< KEY, VALUE > > entrySet() {
    if( entrySet == null ) {
      if( guavaAutomaticTests ) {
        final Set< Map.Entry< KEY, VALUE > > builder = new TreeSet<>(
            entryComparatorByKey
        ) ;
        for( int position = 0 ; position < values.length ; position ++ ) {
          final VALUE typedValue = valueAtPosition( position ) ;
          builder.add( new Entry<>( typedValue ) ) ;
        }
        entrySet = Collections.unmodifiableSet( builder ) ;
      } else {
        final ImmutableSet.Builder< Map.Entry< KEY, VALUE > > builder = ImmutableSet.builder() ;
        for( int position = 0 ; position < values.length ; position ++ ) {
          final VALUE typedValue = valueAtPosition( position ) ;
          builder.add( new Entry<>( typedValue ) ) ;
        }
        entrySet = builder.build() ;
      }
    }
    return entrySet ;

  }


// ================
// Mutating methods
// ================

  @Override
  @Deprecated
  public VALUE put( final KEY key, final VALUE value ) {
    throw new UnsupportedOperationException( "Do not call" ) ;
  }

  @Override
  @Deprecated
  public VALUE remove( final Object key ) {
    throw new UnsupportedOperationException( "Do not call" ) ;
  }

  @Override
  @Deprecated
  public void putAll( @Nonnull final Map< ? extends KEY, ? extends VALUE > m ) {
    throw new UnsupportedOperationException( "Do not call" ) ;
  }

  @Override
  @Deprecated
  public void clear() {
    throw new UnsupportedOperationException( "Do not call" ) ;
  }

}
