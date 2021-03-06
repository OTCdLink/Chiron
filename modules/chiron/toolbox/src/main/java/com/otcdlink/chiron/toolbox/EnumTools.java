package com.otcdlink.chiron.toolbox;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.function.IntFunction;

public enum EnumTools { ;

  private static final Logger LOGGER = LoggerFactory.getLogger( EnumTools.class ) ;

  public static void checkEnumExtender(
      final Enum[] baseValues,
      final Enum[] extendingValues
  ) {
    if( extendingValues.length < baseValues.length ) {
      throw new IncorrectEnumExtensionError( "Only " + extendingValues.length + " values against "
          + baseValues.length + " base values" ) ;
    }
    for( int i = 0 ; i < baseValues.length ; i ++ ) {
      final Enum baseValue = baseValues[ i ] ;
      final Enum extendingValue = extendingValues[ i ] ;
      if( baseValue.ordinal() != extendingValue.ordinal() ) {
        throw new IncorrectEnumExtensionError( "Base ordinal " + baseValue.ordinal()
            + " doesn't match with " + extendingValue.ordinal() ) ;
      }
      if( ! baseValue.name().equals( extendingValue.name() ) ) {
        throw new IncorrectEnumExtensionError( "Base name[ " + i + "] " + baseValue.name()
            + " doesn't match with " + extendingValue.name() ) ;
      }
    }
  }

  public static < ENUM extends Enum > ENUM fromOrdinalSafe(
      final ENUM[] values,
      final int ordinal
  ) throws BadEnumOrdinalException {
    for( final ENUM enumElement : values ) {
      if( enumElement.ordinal() == ordinal ) {
        return enumElement ;
      }
    }
    throw new BadEnumOrdinalException( ordinal, ImmutableList.copyOf( values ) ) ;
  }

  public static < ENUM extends Enum > Integer ordinalMaybe( final ENUM value ) {
    if( value == null ) {
      return null ;
    } else {
      return value.ordinal() ;
    }
  }

  public static < ENUM extends Enum< ENUM > > ImmutableMap< Integer, ENUM > mapFromOrdinal(
      final ENUM[] enumValues
  ) {
    final ImmutableMap.Builder< Integer, ENUM > builder = ImmutableMap.builder() ;
    for( int i = 0 ; i < enumValues.length ; i ++ ) {
      builder.put( i, enumValues[ i ] ) ;
    }
    return builder.build() ;
  }

  public static < ENUM extends Enum< ENUM > > IntFunction< ENUM > strictResolverFromOrdinal(
      final java.util.function.Supplier< ENUM[] > valuesSupplier
  ) {
    return resolverFromOrdinal( valuesSupplier, true ) ;
  }

  public static < ENUM extends Enum< ENUM > > IntFunction< ENUM > lenientResolverFromOrdinal(
      final java.util.function.Supplier< ENUM[] > valuesSupplier
  ) {
    return resolverFromOrdinal( valuesSupplier, false ) ;
  }

  /**
   * Kind of caching function to resolve an {@code Enum} value from its ordinal value, which
   * might be {@code null}.
   * <p>
   * This doesn't conflict with the tests using fake enum value (with {@code EnumBuster})
   * as long as the test runs with a fresh {@code ClassLoader} (which is the default) and the
   * {@code EnumBuster} creates the fake value before caching occurs.
   *
   * @param strict {@code false} to allow {@code null} result, {@code true} to throw
   *     a {@link BadEnumOrdinalException} if resolution failed.
   * @param valuesSupplier reduces the chance to leak a reference to a (mutable) array.
   *     This is typically a reference to the static method {@code SomeEnum::values}.
   * @return {@code null} if no value found
   */
  public static < ENUM extends Enum< ENUM > > IntFunction< ENUM > resolverFromOrdinal(
      final java.util.function.Supplier< ENUM[] > valuesSupplier,
      final boolean strict
  ) {

    final ImmutableList< ENUM > safeValues = ImmutableList.copyOf( valuesSupplier.get() ) ;

    final IntFunction< ENUM > resolver = index -> {
      if( index >= 0 && index < safeValues.size() ) {
        return safeValues.get( index ) ;
      }
      if( strict ) {
        throw new BadEnumOrdinalException( index, safeValues ) ;
      } else {
        return null ;
      }
    } ;
    LOGGER.debug( "Created enum value resolver from " + safeValues + "." ) ;
    return resolver ;
  }

  public static < ENUM extends Enum< ENUM > > java.util.function.Function< String, ENUM >
  strictResolverFromName(
      final java.util.function.Supplier< ENUM[] > valuesSupplier
  ) {
    return resolverFromName( valuesSupplier, true ) ;
  }

  public static < ENUM extends Enum< ENUM > > java.util.function.Function< String, ENUM >
  lenientResolverFromName(
      final java.util.function.Supplier< ENUM[] > valuesSupplier
  ) {
    return resolverFromName( valuesSupplier, false ) ;
  }

  public static < ENUM extends Enum< ENUM > > java.util.function.Function< String, ENUM >
  resolverFromName(
      final java.util.function.Supplier< ENUM[] > valuesSupplier,
      final boolean strict
  ) {
    final ImmutableMap< String, ENUM > valuesByName ;
    {
      final ENUM[] values = valuesSupplier.get() ;
      final ImmutableMap.Builder< String, ENUM > builder = ImmutableMap.builder() ;
      for( final ENUM value : values ) {
        builder.put( value.name(), value ) ;
      }
      valuesByName = builder.build() ;
    }

    final Function< String, ENUM > resolver = name -> {
      if( name == null ) {
        if( strict ) {
          throw new NullPointerException( "name" ) ;
        } else {
          return null ;
        }
      }

      final ENUM resolved = valuesByName.get( name ) ;

      if( resolved == null && strict ) {
        throw new IllegalArgumentException(
            "Unsupported name: '" + name + "', only supports: " + valuesByName.keySet() ) ;
      }
      return resolved ;
    } ;
    LOGGER.debug( "Created " + ( strict ? "strict" : "lenient" ) +
        "enum value resolver from " + valuesByName + "." ) ;
    return resolver ;
  }

  /**
   * @see #lenientResolverFromName(java.util.function.Supplier)  for caching
   */
  public static < ENUM extends Enum > ENUM fromNameLenient(
      final ENUM[] values,
      final String name
  ) {
    for( final ENUM enumElement : values ) {
      if( enumElement.name().equals( name) ) {
        return enumElement ;
      }
    }
    return null ;
  }

  public static < ENUM extends Enum > ENUM last( final ENUM[] values ) {
    return values[ values.length - 1 ] ;
  }


  public static String enumItemToJavaName( final Enum enumItem ) {
    return enumItemNameToJavaName( enumItem.name() ) ;
  }

  public static String enumItemNameToJavaName( final String enumItemName ) {
    return CaseFormat.UPPER_UNDERSCORE.to( CaseFormat.LOWER_CAMEL, enumItemName ) ;
  }

  public static < ENUM extends Enum< ENUM > > ENUM javaNameToEnumLenient(
      final ENUM[] values,
      final String javaName
  ) {
    final String enumName = CaseFormat.LOWER_CAMEL.to( CaseFormat.UPPER_UNDERSCORE, javaName ) ;
    return fromNameLenient( values, enumName ) ;
  }

  public static < ENUM extends Enum< ENUM > > String join(
      final Joiner joiner,
      final ENUM[] values,
      final Function< ENUM, String > presenter
  ) {
    final Object[] transformedValues = new Object[ values.length ] ;
    for( int i = 0 ; i < values.length ; i ++ ) {
      transformedValues[ i ] = presenter.apply( values[ i ] ) ;
    }
    return joiner.join( transformedValues ) ;
  }

  public static < K extends Enum< K >, V > ImmutableMap< K, V > prefilledImmutableEnumMap(
      final Class< K > enumClass,
      final Function< K, V > valueResolver
  ) {
    return prefilledImmutableMap(
        ImmutableSet.copyOf( enumClass.getEnumConstants() ),
        valueResolver
    ) ;
  }

  public static < K, V > ImmutableMap< K, V > prefilledImmutableMap(
      final ImmutableSet< K > keys,
      final Function< K, V > valueResolver

  ) {
    final ImmutableMap.Builder< K, V > builder = ImmutableMap.builder() ;
    for( final K k : keys ) {
      builder.put( k, valueResolver.apply( k ) ) ;
    }
    return builder.build() ;
  }
}
