package com.otcdlink.chiron.toolbox;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.otcdlink.chiron.toolbox.collection.ImmutableKeyHolderMap;
import com.otcdlink.chiron.toolbox.collection.KeyHolder;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;

public final class ImmutableCollectionTools {

  private ImmutableCollectionTools() { }

  public static< OBJECT > ImmutableSet< OBJECT > add(
      final ImmutableSet< OBJECT > set,
      final OBJECT object
  ) {
    final ImmutableSet.Builder< OBJECT > builder = ImmutableSet.builder() ;
    builder.addAll( set ) ;
    builder.add( object ) ;
    return builder.build() ;
  }

  public static< OBJECT > ImmutableSet< OBJECT > remove(
      final ImmutableSet< OBJECT > set,
      final OBJECT object
  ) {
    final ImmutableSet.Builder< OBJECT > builder = ImmutableSet.builder() ;
    for( final OBJECT element : set ) {
      if( ! element.equals( object ) ) {
        builder.add( element ) ;
      }
    }
    return builder.build() ;
  }

  /**
   * Creates an {@link ImmutableMap} containing every enum item as key.
   *
   * @param allValueMap contains at least one item, so we can find the class of the {@code Enum}.
   */
  public static < K extends Enum< K >, V > ImmutableMap< K, V > fullImmutableEnumMap(
      final Map< K, ? extends V > allValueMap
  ) {
    checkArgument( ! allValueMap.isEmpty() ) ;
    final K someItem = allValueMap.keySet().iterator().next() ;
    return fullImmutableEnumMap( ( Class< K > ) someItem.getClass(), allValueMap ) ;
  }

  /**
   * Creates an {@link ImmutableMap} containing every enum item as key.
   */
  public static < K extends Enum< K >, V > ImmutableMap< K, V > fullImmutableEnumMap(
      final Class< K > enumClass,
      final Function< K, ? extends V > valueGenerator
  ) {
    final Map< K, V > allValueMap = new LinkedHashMap<>() ;
    final EnumSet< K > enumSet = EnumSet.allOf( enumClass ) ;
    for( final K enumItem : enumSet ) {
      allValueMap.put( enumItem, valueGenerator.apply( enumItem ) ) ;
    }
    return Maps.immutableEnumMap( allValueMap ) ;
  }

  /**
   * Creates an {@link ImmutableMap} containing every enum item as key.
   */
  public static < K extends Enum< K >, V > ImmutableMap< K, V > fullImmutableEnumMap(
      final Class< K > enumClass,
      final Map< K, ? extends V > allValueMap
  ) {
    final EnumSet< K > enumSet = EnumSet.allOf( enumClass ) ;
    for( final K enumItem : enumSet ) {
      if( ! allValueMap.containsKey( enumItem ) ) {
        throw new IllegalArgumentException( "Missing key " + enumItem + " in " + allValueMap ) ;
      }
    }
    return Maps.immutableEnumMap( allValueMap ) ;
  }

  /**
   * @throws IllegalArgumentException if the {@link KEY} already exists.
   */
  public static< KEY extends KeyHolder.Key< KEY >, VALUE extends KeyHolder< KEY > >
  ImmutableKeyHolderMap< KEY, VALUE > add(
      final ImmutableKeyHolderMap< KEY, VALUE > map,
      final VALUE value
  ) {
    return add(
        map,
        value,
        v -> new IllegalArgumentException( "Already exists: key " + value.key() + " in " + map )
    ) ;
  }

  public static<
      KEY extends KeyHolder.Key< KEY >,
      VALUE extends KeyHolder< KEY >,
      EXCEPTION extends Exception
  >
  ImmutableKeyHolderMap< KEY, VALUE > add(
      final ImmutableKeyHolderMap< KEY, VALUE > map,
      final VALUE value,
      final Function< VALUE, EXCEPTION > exceptionThrower
  ) throws EXCEPTION {
    if( map.containsKey( value.key() ) ) {
      throw exceptionThrower.apply( value ) ;
    }
    final ImmutableKeyHolderMap.Builder< KEY, VALUE > builder = ImmutableKeyHolderMap.builder() ;
    builder.putAll( map.values() ) ;
    builder.put( value ) ;
    return builder.build() ;
  }


}
