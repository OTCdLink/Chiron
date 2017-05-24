package io.github.otcdlink.chiron.upend.http.dispatch;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.RenderingAwareDesignator;
import io.github.otcdlink.chiron.toolbox.ObjectTools;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.github.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands.ServerError;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface HttpResponder {

  /**
   * Creates a {@link Command} if possible.
   * @deprecated use {@link Outbound}.
   */
  interface Resolver {
    /**
     *
     * @param evaluationContext a non-null object.
     * @param httpRequest a non-null object.
     * @return {@code null} if given parameters don't resolve to a valid {@link Command}.
     */
    Command resolve(
        final EvaluationContext evaluationContext,
        final RichHttpRequest httpRequest
    ) ;
  }

  /**
   * Creates an {@code Object} to send directly on {@link ChannelPipeline} if possible.
   */
  interface Outbound {
    /**
     *
     * @param evaluationContext a non-null object.
     * @param httpRequest a non-null object.
     * @return {@code null} if given parameters don't resolve to a valid {@link PipelineFeeder},
     *     a non-{@code null} {@link PipelineFeeder} otherwise that should only perform
     *     {@code write*} operations.
     */
    PipelineFeeder outbound(
        final EvaluationContext evaluationContext,
        final RichHttpRequest httpRequest
    ) ;
  }


  /**
   * Restricts usage of other response-producing interfaces of {@link HttpResponder}.
   */
  interface Condition {

    /**
     *
     * @return {@code true} if there is a chance that call of some associated
     *     {@link Resolver#resolve(EvaluationContext, RichHttpRequest)}
     *     returns a non-null {@link Command}.
     */
    boolean shouldAccept(
        final EvaluationContext evaluationContext,
        final RichHttpRequest httpRequest
    ) ;
  }

  /**
   * Transforms a {@link RichHttpRequest} into an (optiona) call to {@link DUTY}, meant to
   * produce appropriate {@link Command}.
   * <p>
   * Optionally, a call to this interface's single method produces an {@link HttpResponse},
   * meant to be sent immediately to the {@link ChannelPipeline}.
   * <p>
   * If none of the above happen, {@link BareHttpDispatcher} will consider there is no handling
   * of given {@link RichHttpRequest} and will try another option.
   * <p>
   * If an {@code Exception} is thrown during the above process, {@link BareHttpDispatcher}
   * will send a default {@link ServerError} and log the {@code Exception}.
   * <p>
   * The outcome of produced {@link Command} (if any) should be paired with a {@link Renderer}.
   *
   * @param <DUTY> the Duty as set by {@link BareHttpDispatcher#beginBareActionContext(Function)}.
   */
  interface DutyCaller< DUTY > {

    /**
     * @param evaluationContext expects its {@link EvaluationContext#duty()} method to be called
     *     to issue a {@link DUTY} method call. But it is legal to issue no call it at all.
     * @return {@code null} to not send anything now, or an {@link HttpResponse} that will be
     *     immediately {@link ChannelPipeline#flush()}ed. The {@link io.netty.channel.Channel}
     *     will be {@link ChannelFutureListener#CLOSE}d if it is a {@link FullHttpResponse}.
     *     Returning {@link #SILENT_RESPONSE} means the request was handled, even if there
     *     is {@link DUTY} was not called.
     */
    FullHttpResponse call(
        final EvaluationContext< DUTY > evaluationContext,
        final RichHttpRequest httpRequest
    ) ;

    /**
     * Magic value that {@link #call(EvaluationContext, RichHttpRequest)} can return
     * to say it handled the request in a special way.
     */
    FullHttpResponse SILENT_RESPONSE = ObjectTools.nullObject( FullHttpResponse.class ) ;
  }

  /**
   * The single-method interface to transform the result of a {@link DutyCaller}'s call to its
   * {@code DUTY} into a {@link ChannelPipeline}-usable {@code Object}.
   *
   * <h1>Design considerations</h1>
   * <p>
   * The method should not take more than 2 parameters because {@link RenderingAwareDesignator} appears
   * in a Maven module that has no access to {@link HttpResponder.Renderer} class, so it should be
   * compatible with a {@code BiFunction}.
   * <p>
   * There also a good reason to not pass the {@link EvaluationContext} as we do in
   * {@link DutyCaller#call(EvaluationContext, RichHttpRequest)}:
   * the {@link EvaluationContext} contains the "temptation" to use
   * {@link EvaluationContext#duty()} or {@link EvaluationContext#designator()}, which makes
   * probably no sense. It's not too hard to grab {@link EvaluationContext#contextPath()}
   * using {@link BareHttpDispatcher.BuildContext#currentBuildContext} if really needed.
   */
  interface Renderer< RESULT > extends BiFunction<RichHttpRequest, RESULT, Object > {

    @Override
    Object apply(
        final RichHttpRequest httpRequest,
        final RESULT dutyResult
    ) ;
  }


}
