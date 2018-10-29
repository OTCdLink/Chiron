package com.otcdlink.chiron.evaluator;

import com.google.common.base.Converter;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.toolbox.collection.Autoconstant;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents one kind of value to be extracted from an {@link ENTITY} for further evaluations
 * in {@link Evaluator#field(QueryField, Operator, Object)}.
 *
 * @param <OPERATOR> useful for propagating some type in {@link Evaluator}.
 */
public abstract class QueryField<
    ENTITY,
    PARAMETER,
    OPERATOR extends Operator< PARAMETER, VALUE >,
    VALUE
> extends Autoconstant {

  final Extractor< ENTITY, VALUE > extractor ;

  /**
   * Need a {@link BiMap} instead of a {@link Converter} so we can enumerate
   * the {@link Operator#symbol()}s for {@link EvaluatorParser}.
   */
  final ImmutableBiMap< String, OPERATOR > operatorBiMap ;

  /**
   * Useful to reparse a parameter from a {@code String}, or to print it properly.
   */
  protected final Converter< String, PARAMETER > parameterConverterFromString ;

  protected QueryField(
      final Extractor< ENTITY, VALUE > extractor,
      final Conversion< PARAMETER, OPERATOR, VALUE > conversion
  ) {
    this(
        extractor,
        conversion.operatorBiMap,
        conversion.parameterConverterFromString
    ) ;
  }

  protected QueryField(
      final Extractor< ENTITY, VALUE > extractor,
      final ImmutableBiMap< String, OPERATOR > operatorBiMap,
      final Converter< String, PARAMETER > parameterConverterFromString
  ) {
    this.extractor = checkNotNull( extractor ) ;
    this.operatorBiMap = checkNotNull( operatorBiMap ) ;
    this.parameterConverterFromString = checkNotNull( parameterConverterFromString ) ;
  }

  /**
   * Helps to avoid repeating several values in every concrete {@link QueryField} declaration.
   */
  public static class Conversion<
      PARAMETER,
      OPERATOR extends Operator< PARAMETER, VALUE >,
      VALUE
  > {
    public final ImmutableBiMap< String, OPERATOR > operatorBiMap ;
    public final Converter< String, PARAMETER > parameterConverterFromString ;

    public Conversion(
        final ImmutableBiMap< String, OPERATOR > operatorBiMap,
        final Converter< String, PARAMETER > parameterConverterFromString
    ) {
      this.operatorBiMap = checkNotNull( operatorBiMap ) ;
      this.parameterConverterFromString = checkNotNull( parameterConverterFromString ) ;
    }

    public static <
        PARAMETER,
        OPERATOR extends Enum< OPERATOR > & Operator< PARAMETER, VALUE >,
        VALUE
    > ImmutableBiMap< String, OPERATOR > fromEnum( final Class< OPERATOR > enumClass ) {
      final ImmutableBiMap.Builder< String, OPERATOR > operatorBiMapBuilder =
          ImmutableBiMap.builder() ;
      final OPERATOR[] enumConstants = enumClass.getEnumConstants() ;
      for( final OPERATOR operator : enumConstants ) {
        operatorBiMapBuilder.put( operator.symbol(), operator ) ;
      }
      final ImmutableBiMap< String, OPERATOR > operatorBiMap = operatorBiMapBuilder.build() ;
      return operatorBiMap ;
    }
  }

  /**
   * Type cheat.
   */
  protected static <
      MAP extends ImmutableMap< String, ? extends THIS >,
      THIS extends QueryField
  > MAP valueMapQuiet( final Class< THIS > thisClass ) {
    return ( MAP ) Autoconstant.valueMap( thisClass ) ;
  }

  static < QUERY_FIELD extends QueryField< ?, ?, ?, ? > >
  ImmutableBiMap< String, QUERY_FIELD > nameMap(
      final ImmutableSet< QUERY_FIELD > queryFields,
      final Function< QueryField, String > nameTransformer
  ) {
    final ImmutableBiMap.Builder< String, QUERY_FIELD > builder = ImmutableBiMap.builder() ;
    for( final QUERY_FIELD queryField : queryFields ) {
      builder.put( nameTransformer.apply( queryField ), queryField ) ;
    }
    return builder.build() ;
  }
}
