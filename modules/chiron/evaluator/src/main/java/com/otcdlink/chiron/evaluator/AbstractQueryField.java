package com.otcdlink.chiron.evaluator;

import com.google.common.base.Converter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import com.otcdlink.chiron.toolbox.collection.Autoconstant;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default implementation.
 *
 * @param <OPERATOR_CONTEXT> needed to transform {@link ENTITY_CONTEXT} into something that
 *     {@link OPERATOR} can use. As a matter of fact, at the time of the declaration of a
 *     {@link PackedQueryField} as an enum or {@link Autoconstant} member,
 *     we don't know the {@link ENTITY_CONTEXT} instance.
 */
public abstract class AbstractQueryField<
    ENTITY,
    ENTITY_CONTEXT,
    OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
    OPERATOR_CONTEXT,
    PARAMETER,
    VALUE
> implements QueryField< ENTITY, ENTITY_CONTEXT, OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > {

  private final String name ;
  protected final Function< ENTITY_CONTEXT, OPERATOR_CONTEXT > operatorContextExtractor;
  protected final ImmutableBiMap< String, OPERATOR > operatorSymbolMap ;
  protected final Converter< String, PARAMETER > parameterConverter ;
  protected final Function< ENTITY, VALUE > valueExtractor ;

  protected AbstractQueryField(
      final String name,
      final ImmutableBiMap< String, OPERATOR > operatorSymbolMap,
      final Function< ENTITY_CONTEXT, OPERATOR_CONTEXT > operatorContextExtractor,
      final Converter< String, PARAMETER > parameterConverter,
      final Function< ENTITY, VALUE > valueExtractor
  ) {
    checkArgument( ! Strings.isNullOrEmpty( this.name = name ) ) ;
    this.operatorContextExtractor = checkNotNull( operatorContextExtractor ) ;
    this.operatorSymbolMap = checkNotNull( operatorSymbolMap ) ;
    this.parameterConverter = checkNotNull( parameterConverter ) ;
    this.valueExtractor = checkNotNull( valueExtractor ) ;
  }

  @Override
  public String name() {
    return name ;
  }

  /**
   * @param operatorContext passing it here gives memoization opportunity for the caller.
   */
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
}
