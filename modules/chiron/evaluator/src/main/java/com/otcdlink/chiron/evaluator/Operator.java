package com.otcdlink.chiron.evaluator;

import java.util.function.Function;

/**
 * Evaluates a {@link VALUE} given an {@link OPERATOR_CONTEXT} and a query {@link PARAMETER}.
 *
 * @param <OPERATOR_CONTEXT> not a part of the Entity or the {@link PARAMETER}, but needed for
 *     the evaluation. A typical exemple is current date/time.
 */
public interface Operator< OPERATOR_CONTEXT, PARAMETER, VALUE > {

  /**
   * A compact representation when {@link EvaluatorPrinter printed}.
   */
  String symbol() ;

  boolean apply( OPERATOR_CONTEXT context, PARAMETER parameter, VALUE value ) ;

  static< ENTITY_CONTEXT, OPERATOR_CONTEXT >
  Function< ENTITY_CONTEXT, OPERATOR_CONTEXT > nullExtractor() {
    return any -> null ;
  }
}
