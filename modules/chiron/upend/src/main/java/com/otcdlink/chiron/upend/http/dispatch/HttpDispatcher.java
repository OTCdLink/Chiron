package com.otcdlink.chiron.upend.http.dispatch;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import com.otcdlink.chiron.upend.http.content.StaticContent;
import com.otcdlink.chiron.upend.http.content.caching.BytebufContent;
import com.otcdlink.chiron.upend.http.content.caching.StaticContentCache;
import com.otcdlink.chiron.upend.http.content.file.StaticFileContentProvider;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Enhances {@link BareHttpDispatcher} with nice shortcuts.
 */
public class HttpDispatcher<
    COMMAND extends Command< Designator, DUTY >,
    COMMAND_CONSUMER extends CommandConsumer< COMMAND >,
    DUTY,
    ANCESTOR,
    THIS extends HttpDispatcher< COMMAND, COMMAND_CONSUMER, DUTY, ANCESTOR, THIS >
> extends BareHttpDispatcher< COMMAND, COMMAND_CONSUMER, DUTY, ANCESTOR, THIS > {

  public HttpDispatcher( final Designator.FactoryForInternal designatorFactory ) {
    super( designatorFactory ) ;
  }

  public static HttpDispatcher< ?, ?, Void, Void, ? extends HttpDispatcher > newDispatcher(
      final Designator.FactoryForInternal designatorSupplier
  ) {
    return new HttpDispatcher( designatorSupplier ) ;
  }

  /**
   * Ugly transtyping hack, needed to propagate {@link DUTY} type.
   */
  public<
      NEW_COMMAND extends Command< Designator, CHILD_DUTY >,
      NEW_COMMAND_CONSUMER extends CommandConsumer< NEW_COMMAND >,
      CHILD_DUTY,
      THAT extends HttpDispatcher<
          NEW_COMMAND,
          NEW_COMMAND_CONSUMER,
          CHILD_DUTY,
          THIS,
          THAT
      >
  > THAT beginActionContext(
      final Function< NEW_COMMAND_CONSUMER, CHILD_DUTY > commandCrafterCreator
  ) {
    return super.beginBareActionContext( commandCrafterCreator ) ;
  }


// =================
// Casual HTTP stuff
// =================

  public THIS notFound() {
    return response( UsualHttpCommands.NotFound.outbound() ) ;
  }

  public THIS forbidden() {
    return response( UsualHttpCommands.Forbidden.outbound() ) ;
  }

  public THIS redirect( final String uri ) {
    return response( UsualHttpCommands.Redirect.outbound( uri ) ) ;
  }

  public THIS html( final String htmlBody ) {
    return response( UsualHttpCommands.Html.outbound( htmlBody ) ) ;
  }

  public THIS xml( final String htmlBody ) {
    return response( UsualHttpCommands.Xml.outbound( htmlBody ) ) ;
  }

// ================
// Resource-serving
// ================

  public final THIS file( final StaticFileContentProvider fileContentProvider ) {
    return response( ( evaluationContext, httpRequest ) -> {

      /**
       * Relativisation calls {@link UriPath#checkPathWellFormed(String)}
       * so we have a safe path here (doesn't start by "/" and contains no "..").
       */
      final String cleanRelativePath =
          evaluationContext.contextPath().relativizeFromSlashedPath( httpRequest.uriPath ) ;

      final StaticContent.FromFile staticContentFromFile =
          fileContentProvider.fileContent( cleanRelativePath ) ;

      if( staticContentFromFile == null ) {
        return null ;
      } else {
        return new UsualHttpCommands.JustFile( staticContentFromFile, httpRequest ) ;
      }
    } ) ;
  }

  public final THIS resourceMatch( final StaticContentCache contentCache ) {
    checkNotNull( contentCache ) ;
    return response( ( HttpResponder.Outbound ) ( evaluationContext, httpRequest ) -> {

      final String cleanRelativeUriPath = evaluationContext.contextPath()
          .relativizeFromSlashedPath( httpRequest.uriPath ) ;

      final BytebufContent staticContentAsByteBuf =
          contentCache.staticContent( cleanRelativeUriPath ) ;

      if( staticContentAsByteBuf == null ) {
        return null ;
      } else {
        return new UsualHttpCommands.JustBytebuf(
            staticContentAsByteBuf,
            httpRequest.uriPath
        ) ;
      }
    } ) ;
  }

// =====
// Other
// =====

  public final THIS enforceSchemeHostPort( final SchemeHostPort schemeHostPortEnforced ) {
    checkNotNull( schemeHostPortEnforced ) ;
    return response( ( evaluationContext, httpRequest ) -> {
      if( schemeHostPortEnforced.hostnameAsString().equals( httpRequest.requestedHost ) &&
          schemeHostPortEnforced.port() == httpRequest.requestedPort &&
          ( httpRequest.uriScheme == null ||
              schemeHostPortEnforced.scheme.nameLowerCase().equals( httpRequest.uriScheme ) )
          ) {
        return null ;
      } else {
        final String redirection = UrxTools.derive( httpRequest.uri(), schemeHostPortEnforced ) ;
        return new UsualHttpCommands.Redirect( redirection ) ;
      }
    } ) ;
  }


}
