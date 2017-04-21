package io.github.otcdlink.chiron.fixture.http;

import com.google.common.collect.ImmutableCollection;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.assertj.core.api.AbstractAssert;

import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

public class WatchedResponseAssert
    extends AbstractAssert< WatchedResponseAssert, NettyHttpClient.Outcome >
{
  public static WatchedResponseAssert assertThat(
      final NettyHttpClient.Outcome response
  ) {
    return new WatchedResponseAssert( response ) ;
  }

  protected WatchedResponseAssert( final NettyHttpClient.Outcome actual ) {
    super( actual, WatchedResponseAssert.class ) ;
  }

  public PureHttpResponseAssert isComplete() {
    return new PureHttpResponseAssert( is( NettyHttpClient.CompleteResponse.class ) ) ;
  }

  public NoResponseAssert isNoResponse() {
    return new NoResponseAssert( is( NettyHttpClient.NoResponse.class ) ) ;
  }

  public ResponseWithThrowableAssert hasThrowable() {
    return new ResponseWithThrowableAssert(
        is( NettyHttpClient.ResponseWithThrowable.class ) ) ;
  }

  private < RESPONSE extends NettyHttpClient.Outcome > RESPONSE is(
      final Class< ? extends NettyHttpClient.Outcome > concreteClass
  ) {
    isNotNull() ;
    if( ! concreteClass.isAssignableFrom( actual.getClass() ) ) {
      failWithMessage( "Expected an instance of <%s> but is <%s>",
          concreteClass.getName(), actual.getClass().getName() ) ;
    }
    return ( RESPONSE ) actual ;
  }

  public static class PureHttpResponseAssert
      extends AbstractAssert< PureHttpResponseAssert, NettyHttpClient.CompleteResponse>
  {
    private PureHttpResponseAssert( final NettyHttpClient.CompleteResponse actual ) {
      super( actual, PureHttpResponseAssert.class ) ;
    }

    public PureHttpResponseAssert hasStatusCode( final HttpResponseStatus expected ) {
      isNotNull() ;
      if( actual.response.responseStatus.code() != expected.code() ) {
        failWithMessage( "Expected status code <%s> (%s) but was <%s>",
            expected.code(),
            expected.reasonPhrase(),
            actual.response.responseStatus
        ) ;
      }
      return this ;
    }

    public HttpMoved302ResponseAssert isMoved302() {
      hasStatusCode( HttpResponseStatus.FOUND ) ;
      return new HttpMoved302ResponseAssert( actual ) ;
    }

    public PureHttpResponseAssert hasContentMatching( final Pattern pattern ) {
      isNotNull() ;
      if( ! pattern.matcher( actual.response.contentAsString ).matches() ) {
        failWithMessage( "Expected content to match pattern <%s> but found <%s>",
            pattern.pattern(), actual.response.contentAsString ) ;
      }
      return this ;
    }

    public PureHttpResponseAssert hasContent( final String contentAsString ) {
      isNotNull() ;
      if( ! contentAsString.equals( actual.response.contentAsString ) ) {
        failWithMessage( "Expected content to match <%s> but found <%s>",
            contentAsString, actual.response.contentAsString ) ;
      }
      return this ;
    }

    public PureHttpResponseAssert hasContentContaining( final String contentFragment ) {
      isNotNull() ;
      if( ! actual.response.contentAsString.contains( contentFragment ) ) {
        failWithMessage(
            "Expected content fragment <%s> to be found in <%s> but was not",
            contentFragment, actual.response.contentAsString ) ;
      }
      return this ;
    }

    public PureHttpResponseAssert hasHeader( final CharSequence headerName, final String expectedValue ) {
      isNotNull() ;
      final String headerNameAsString = headerName.toString() ;
      final ImmutableCollection< String > values =
          actual.response.headers.get( headerNameAsString ) ;
      if( expectedValue == null ) {
        if( ! values.isEmpty() ) {
          failWithMessage( "Expected header '<%s>' to be null but found <%s>",
              headerNameAsString,
              values
          ) ;
        }
      } else if( ! values.contains( expectedValue ) ) {
        failWithMessage( "Expected header '<%s>' to be '<%s>' but found <%s>",
            headerNameAsString,
            expectedValue,
            values
        ) ;
      }
      return this ;
    }
  }
  public static class HttpMoved302ResponseAssert
      extends AbstractAssert< HttpMoved302ResponseAssert, NettyHttpClient.CompleteResponse >
  {
    private HttpMoved302ResponseAssert( final NettyHttpClient.CompleteResponse actual ) {
      super( actual, HttpMoved302ResponseAssert.class ) ;
    }

    public HttpMoved302ResponseAssert hasTargetMatching( final Pattern pattern ) {
      isNotNull() ;
      final URL redirectionTarget = actual.redirectionTarget();
      if( redirectionTarget == null ) {
        failWithMessage( "No redirection target" ) ;
      }
      if( ! pattern.matcher( redirectionTarget.toExternalForm() ).matches() ) {
        failWithMessage( "Expected redirection target to match pattern <%s> but found <%s>",
            pattern.pattern(), actual.response.contentAsString ) ;
      }
      return this ;
    }

    public HttpMoved302ResponseAssert hasTargetMatching( final URL target ) {
      return hasTargetMatching( target.toExternalForm() ) ;
    }

    public HttpMoved302ResponseAssert hasTargetMatching( final URI target ) {
      return hasTargetMatching( UrxTools.toUrlQuiet( target ) ) ;
    }

    public HttpMoved302ResponseAssert hasTargetMatching( final String targetUrlAsString ) {
      isNotNull() ;
      final URL redirectionTarget = actual.redirectionTarget() ;
      if( redirectionTarget == null ) {
        failWithMessage( "No redirection target" ) ;
      }
      if( ! targetUrlAsString.equals( redirectionTarget.toExternalForm() ) ) {
        failWithMessage( "Expected redirection target to match <%s> but found <%s>",
            targetUrlAsString, redirectionTarget.toExternalForm() ) ;
      }
      return this ;
    }
  }

  public static class ResponseWithThrowableAssert
      extends AbstractAssert<
          ResponseWithThrowableAssert,
          NettyHttpClient.ResponseWithThrowable
      >
  {
    private ResponseWithThrowableAssert(
        final NettyHttpClient.ResponseWithThrowable actual
    ) {
      super( actual, ResponseWithThrowableAssert.class ) ;
    }
  }

  public static class NoResponseAssert
      extends AbstractAssert<
          NoResponseAssert,
          NettyHttpClient.NoResponse
      >
  {
    private NoResponseAssert(
        final NettyHttpClient.NoResponse actual
    ) {
      super( actual, NoResponseAssert.class ) ;
    }

    public NoResponseAssert isTimeout() {
      return hasCause( NettyHttpClient.Recorder.NoResponseCause.TIMEOUT ) ;
    }

    public NoResponseAssert isCancelled() {
      return hasCause( NettyHttpClient.Recorder.NoResponseCause.CANCELLED ) ;
    }

    public NoResponseAssert hasCause( final NettyHttpClient.Recorder.NoResponseCause cause ) {
      isNotNull() ;
      if( actual.cause != cause ) {
        failWithMessage( "Expecting cause <%s> but was <%s>" );
      }
      return this ;
    }

  }


}
