package com.otcdlink.chiron.evaluator;

import com.google.common.base.Converter;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.toolbox.collection.Autoconstant;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * To use as an {@link Autoconstant}.
 * Duplicates code from {@link AbstractQueryField}.
 */
public abstract class AutoQueryField<
    ENTITY,
    ENTITY_CONTEXT,
    OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
    OPERATOR_CONTEXT,
    PARAMETER,
    VALUE
>
  extends Autoconstant
  implements QueryField< ENTITY, ENTITY_CONTEXT, OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > {

  protected final Function< ENTITY_CONTEXT, OPERATOR_CONTEXT > operatorContextExtractor;
  protected final ImmutableBiMap< String, OPERATOR > operatorSymbolMap ;
  protected final Converter< String, PARAMETER > parameterConverter ;
  protected final Function< ENTITY, VALUE > valueExtractor ;

  protected AutoQueryField(
      final ImmutableBiMap< String, OPERATOR > operatorSymbolMap,
      final Function< ENTITY_CONTEXT, OPERATOR_CONTEXT > operatorContextExtractor,
      final Converter< String, PARAMETER > parameterConverter,
      final Function< ENTITY, VALUE > valueExtractor
  ) {
    this.operatorContextExtractor = checkNotNull( operatorContextExtractor ) ;
    this.operatorSymbolMap = checkNotNull( operatorSymbolMap ) ;
    this.parameterConverter = checkNotNull( parameterConverter ) ;
    this.valueExtractor = checkNotNull( valueExtractor ) ;
  }

  protected AutoQueryField(
      final OperatorCare< OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > operatorCare,
      final Function< ENTITY_CONTEXT, OPERATOR_CONTEXT > operatorContextExtractor,
      final Function< ENTITY, VALUE > valueExtractor
  ) {
    this(
        operatorCare.symbolMap(),
        operatorContextExtractor,
        operatorCare.parameterConverter(),
        valueExtractor
    ) ;
  }

  public final boolean apply(
      final OPERATOR operator,
      final OPERATOR_CONTEXT operatorContext,
      final PARAMETER parameter,
      final VALUE value
  ) {
    return QueryField.applyOperator( operator, operatorContext, parameter, value ) ;
  }

  @Override
  public final Function< ENTITY_CONTEXT, OPERATOR_CONTEXT > operatorContextExtractor() {
    return operatorContextExtractor ;
  }

  @Override
  public final ImmutableBiMap< String, OPERATOR > operatorSymbolMap() {
    return operatorSymbolMap ;
  }

  @Override
  public final Converter< String, PARAMETER > parameterConverter() {
    return parameterConverter ;
  }

  @Override
  public final Function< ENTITY, VALUE > valueExtractor() {
    return valueExtractor ;
  }

    /**
   * Type cheat.
   */
  protected static <
      MAP extends ImmutableMap< String, ? extends THIS >,
      THIS extends AutoQueryField
  > MAP valueMapQuiet( final Class< THIS > thisClass ) {
    return ( MAP ) Autoconstant.valueMap( thisClass ) ;
  }


}
