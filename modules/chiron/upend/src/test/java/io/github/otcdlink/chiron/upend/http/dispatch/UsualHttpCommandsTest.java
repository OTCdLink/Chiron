package io.github.otcdlink.chiron.upend.http.dispatch;

import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.netty.channel.Channel;
import mockit.FullVerificationsInOrder;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

public class UsualHttpCommandsTest {

  @Test
  public void redirectWithSlash() {
    verify( "/foo", "/foo", "/foo/" ) ;
    verify( "/bar", "/bar/", null ) ;
  }


// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( UsualHttpCommandsTest.class ) ;

  @SuppressWarnings( "FieldMayBeFinal" )
  @Injectable
  private Channel channel = null ;

  @SuppressWarnings( "FieldMayBeFinal" )
  @Injectable
  private EvaluationContext evaluationContext = null ;

  @SuppressWarnings( "FieldMayBeFinal" )
  @Injectable
  private Designator designator = null ;

  private static final String REQUEST_URI_BASE = "http://127.0.0.1" ;

  private static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress(
      "127.0.0.1", 1111 ) ;

  private static final InetSocketAddress LOCAL_ADDRESS = new InetSocketAddress(
      "127.0.0.1", 2222 ) ;

  private RichHttpRequest httpRequest( final String uriPath ) {
    return RichHttpRequest.from(
        REQUEST_URI_BASE + uriPath, channel, REMOTE_ADDRESS, LOCAL_ADDRESS, false ) ;
  }

  private void verify(
      final String contextPath,
      final String requestUriPath,
      final String redirectionUri
  ) {
    new StrictExpectations() {{
      evaluationContext.contextPath() ; result = UriPath.from( contextPath ) ;
      evaluationContext.designator() ; result = designator ; times = -1 ;
    }} ;
    final RichHttpRequest request = httpRequest( requestUriPath ) ;
    final UsualHttpCommands.Redirect command = ( UsualHttpCommands.Redirect )
        UsualHttpCommands.Redirect.APPEND_MISSING_SLASH_TO_URL.outbound( evaluationContext, request ) ;
    final String description =
        "context path: '" + contextPath + ", " +
        "request URI path: '" + requestUriPath + "', " +
        "redirection: " + command
    ;
    if( redirectionUri == null ) {
      assertThat( command ).describedAs( description ).isNull() ;
    } else {
      assertThat( command.redirectionTargetUri )
          .describedAs( description )
          .isEqualTo( REQUEST_URI_BASE + redirectionUri ) ;
    }

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( "Verified " + description + "." ) ;
  }

}