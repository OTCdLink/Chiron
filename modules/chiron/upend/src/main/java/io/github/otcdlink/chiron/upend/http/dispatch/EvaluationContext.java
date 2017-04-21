package io.github.otcdlink.chiron.upend.http.dispatch;

import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.netty.channel.Channel;

import java.util.function.Function;

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
   * which may be the result of {@link Designator.Factory#phasing(Channel, Function)}
   * when it makes sense.
   */
  Designator designator() ;
}
