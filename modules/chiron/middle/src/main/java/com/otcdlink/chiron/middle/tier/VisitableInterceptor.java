package com.otcdlink.chiron.middle.tier;

import java.util.function.Consumer;

/**
 * Can't declare it inside {@link CommandInterceptor}, would involve cyclic inheritance.
 */
public interface VisitableInterceptor {
  /**
   * Visit this {@link CommandInterceptor}, or those it contains in the case of a {@link CommandInterceptor.Chain}.
   */
  void visit( Consumer< CommandInterceptor > visitor ) ;
}
