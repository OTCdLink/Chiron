package io.github.otcdlink.chiron.upend.http.dispatch;

import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

public final class UsualConditions {

  private UsualConditions() { }

  public static final HttpResponder.Condition ALWAYS = new HttpResponder.Condition() {
    @Override
    public boolean shouldAccept(
        final EvaluationContext Ø,
        final RichHttpRequest request
    ) {
      return true ;
    }

    @Override
    public String toString() {
      return UsualConditions.class.getSimpleName() + "#ALWAYS" ;
    }
  } ;

  public static final HttpResponder.Condition NEVER = ( Ø, request ) -> false ;

  public static final HttpResponder.Condition IS_POST_METHOD =
      ( Ø, request ) -> HttpMethod.POST.equals( request.method() ) ;

  public static final HttpResponder.Condition IS_GET_METHOD =
      ( Ø, request ) -> HttpMethod.GET.equals( request.method() ) ;

  /**
   * Verify the address bound to the network interface, not the one in HTTP request.
   * This predicate can be used as additional safeguard once listening to {@code localhost}
   * and nothing else.
   */
  public static final HttpResponder.Condition IS_LOCALHOST =
      ( Ø, request ) -> request.localAddress().getAddress().isLoopbackAddress() ;


  public static HttpResponder.Condition absoluteMatch( final String absolutePath ) {
    return new HttpResponder.Condition() {
      @Override
      public boolean shouldAccept(
          final EvaluationContext evaluationContext,
          final RichHttpRequest httpRequest
      ) {
        return httpRequest.uriPath.equals( absolutePath ) ;
      }

      @Override
      public String toString() {
        return HttpResponder.Condition.class.getSimpleName() +
            "#absoluteMatch{" + absolutePath + "}" ;
      }
    } ;
  }

  /**
   * Evaluates to {@code true} when {@link RichHttpRequest#uriPath} is equal to
   * {@link EvaluationContext#contextPath()} (with no trailing slash) plus given
   * {@code relativeUriPath}.
   */
  public static HttpResponder.Condition relativeMatch( final String relativeUriPath ) {
    return new HttpResponder.Condition() {
      @Override
      public boolean shouldAccept(
          final EvaluationContext evaluationContext,
          final RichHttpRequest httpRequest
      ) {
        final String relativised =
            evaluationContext.contextPath().relativizeFromUnslashedPath( httpRequest.uriPath ) ;
        return relativeUriPath.equals( relativised ) ;
      }

      @Override
      public String toString() {
        return HttpResponder.Condition.class.getSimpleName() + "#relativeMatch{" +
            relativeUriPath + "}" ;
      }
    } ;
  }
}
