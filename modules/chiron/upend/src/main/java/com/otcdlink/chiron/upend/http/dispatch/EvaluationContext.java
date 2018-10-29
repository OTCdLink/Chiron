package com.otcdlink.chiron.upend.http.dispatch;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.toolbox.netty.RichHttpRequest;

import java.util.function.BiFunction;

/**
 * What a {@link HttpResponder.Resolver}, {@link HttpResponder.Condition}, or
 * {@link HttpResponder.DutyCaller} should know when evaluating some
 * {@link RichHttpRequest}.
 *
 * @param <DUTY> the one from {@link BareHttpDispatcher}.
 */
public interface EvaluationContext< DUTY > {

  /**
   * Returns the path defined by nested calls of
   * {@link BareHttpDispatcher#beginPathSegment(String)}.
   */
  UriPath contextPath() ;

  DUTY duty() ;

  /**
   * Creates a fresh {@link Designator} of {@link Designator.Kind#INTERNAL} kind,
   * which may be the result of {@link Designator.Factory#phasing(RichHttpRequest, BiFunction)}
   * when it makes sense.
   */
  Designator designator() ;
}
