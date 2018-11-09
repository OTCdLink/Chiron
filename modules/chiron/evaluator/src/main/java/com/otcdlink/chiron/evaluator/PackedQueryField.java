package com.otcdlink.chiron.evaluator;

import java.util.function.Function;

/**
 * Uses an {@link OperatorCare} to factor some declarations.
 */
public abstract class PackedQueryField<
    ENTITY,
    ENTITY_CONTEXT,
    OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
    OPERATOR_CONTEXT,
    PARAMETER,
    VALUE
> extends AbstractQueryField< ENTITY, ENTITY_CONTEXT, OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > {

  protected PackedQueryField(
      final String name,
      final OperatorCare< OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > operatorCare,
      final Function< ENTITY_CONTEXT, OPERATOR_CONTEXT > evaluatorContextExtractor,
      final Function< ENTITY, VALUE > valueExtractor
  ) {
    super(
        name,
        operatorCare.symbolMap(),
        evaluatorContextExtractor,
        operatorCare.parameterConverter(),
        valueExtractor
    ) ;
  }
}
