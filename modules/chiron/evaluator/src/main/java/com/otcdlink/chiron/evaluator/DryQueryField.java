package com.otcdlink.chiron.evaluator;

import java.util.function.Function;

/**
 * A simplified {@link PackedQueryField} that doesn't care about context.
 */
@SuppressWarnings( "unused" )
public abstract class DryQueryField<
    ENTITY,
    OPERATOR extends Operator< Void, PARAMETER, VALUE >,
    PARAMETER,
    VALUE
> extends PackedQueryField< ENTITY, Void, OPERATOR, Void, PARAMETER, VALUE > {

  protected DryQueryField(
      final String name,
      final OperatorCare< OPERATOR, Void, PARAMETER, VALUE > operatorCare,
      final OPERATOR operator,
      final Function< ENTITY, VALUE > valueExtractor
  ) {
    super(
        name,
        operatorCare,
        any -> null,
        valueExtractor
    ) ;
  }
}
