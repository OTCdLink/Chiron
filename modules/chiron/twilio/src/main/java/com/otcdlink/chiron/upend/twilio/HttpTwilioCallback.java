package com.otcdlink.chiron.upend.twilio;

import com.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import com.otcdlink.chiron.upend.http.dispatch.EvaluationContext;
import com.otcdlink.chiron.upend.http.dispatch.HttpDispatcher;
import com.otcdlink.chiron.upend.http.dispatch.HttpResponder;
import com.otcdlink.chiron.upend.http.dispatch.UriPath;
import com.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.otcdlink.chiron.upend.http.dispatch.UsualConditions.relativeMatch;
import static com.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands.Redirect.APPEND_MISSING_SLASH_TO_URL;

/**
 * Configures an {@link HttpDispatcher} to respond to Twilio's HTTP requests, delegating logic
 * to {@link TwilioHttpCallback}.
 */
public final class HttpTwilioCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger( HttpTwilioCallback.class ) ;

  private final TwilioHttpCallback twilioHttpCallback ;

  public HttpTwilioCallback( final TwilioHttpCallback twilioHttpCallback ) {
    this.twilioHttpCallback = checkNotNull( twilioHttpCallback ) ;
  }

  private static final String CALL_STATUS_PARAMETER_NAME = "CallStatus" ;

  public Consumer< HttpDispatcher< ?, ?, Void, Void, ? extends HttpDispatcher > > setup() {
    return httpDispatcher -> httpDispatcher
        .response( APPEND_MISSING_SLASH_TO_URL )
        .responseIf(
            relativeMatch( "/" ),
            UsualHttpCommands.Html.outbound( "Twilio callback service" )
        )
        .beginPathSegment( TwilioHttpCallback.URL_SEGMENT_TWIML )
            .response( createTwimlOutbound() )
        .endPathSegment()
        .beginPathSegment( TwilioHttpCallback.URL_SEGMENT_STATUS )
            .response( createStatusOutbound() )
        .endPathSegment()
        .beginPathSegment( TwilioHttpCallback.URL_SEGMENT_FALLBACK )
            .response( createFallbackOutbound() )
        .endPathSegment()
        .forbidden()
    ;
  }

  private static String extractToken(
      final EvaluationContext evaluationContext,
      final RichHttpRequest httpRequest
  ) {
    final UriPath uriPath = evaluationContext.contextPath() ;
    return uriPath.relativize( httpRequest.uriPath, true ) ;
  }


  private HttpResponder.Outbound createTwimlOutbound() {
    return ( evaluationContext, httpRequest ) -> {
      final String token = extractToken( evaluationContext, httpRequest );
      return token == null ? null : new UsualHttpCommands.Xml(
          // TODO: remove '\n' which is here only for keeping TwilioCallbackServlet unchanged.
          twilioHttpCallback.createTwiml( token ) + "\n" ) ;
    } ;
  }

  private HttpResponder.Outbound createStatusOutbound() {
    return ( evaluationContext, httpRequest ) -> {
      final String token = extractToken( evaluationContext, httpRequest ) ;
      final String callStatus = httpRequest.parameter( CALL_STATUS_PARAMETER_NAME ) ;
      final TwilioStatus twilioStatus = TwilioStatus.fromSpecifiedName( callStatus ) ;

      if( token != null && twilioStatus != null ) {
        twilioHttpCallback.updateTwilioCallStatus( token, twilioStatus ) ;
        return UsualHttpCommands.Html.htmlBodyOk(
            "Status set to " + callStatus + " for " + token + "." ) ;
      } else {
        return new UsualHttpCommands.Html(
            HttpResponseStatus.BAD_REQUEST,
            "Bad request; " +
                "token=" + token +
                "callStatus=" + twilioStatus
        ) ;
      }
    } ;
  }

  private static HttpResponder.Outbound createFallbackOutbound() {
    return ( evaluationContext, httpRequest ) -> {
      final String message =
          "Twilio reported an unsupported request: '" + httpRequest.uri() + "'." ;
      LOGGER.warn( message ) ;
      return UsualHttpCommands.Html.htmlBodyOk( message ) ;
    } ;
  }


}
