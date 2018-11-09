package com.otcdlink.chiron.evaluator;

import com.google.common.base.Converter;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;

import java.util.function.Function;

public interface QueryField<
    ENTITY,
    ENTITY_CONTEXT,
    OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
    OPERATOR_CONTEXT,
    PARAMETER,
    VALUE
> {
  String name() ;

  boolean apply(
      final OPERATOR operator,
      final OPERATOR_CONTEXT operatorContext,
      final PARAMETER parameter,
      final VALUE value
  ) ;

  Function< ENTITY_CONTEXT, OPERATOR_CONTEXT > operatorContextExtractor() ;
  ImmutableBiMap< String, OPERATOR > operatorSymbolMap() ;
  Converter< String, PARAMETER > parameterConverter() ;
  Function< ENTITY, VALUE > valueExtractor() ;


  static<
    OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
    OPERATOR_CONTEXT,
    PARAMETER,
    VALUE
  > boolean applyOperator(
      final OPERATOR operator,
      final OPERATOR_CONTEXT operatorContext,
      final PARAMETER parameter,
      final VALUE value
  ) {
    return operator.apply( operatorContext, parameter, value ) ;
  }

  static < QUERY_FIELD extends QueryField< ?, ?, ?, ?, ?, ? >>
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
