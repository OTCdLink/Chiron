package com.otcdlink.chiron.upend.twilio;

import com.google.common.collect.ImmutableMultimap;
import com.otcdlink.chiron.fixture.http.WatchedResponseAssert;
import com.otcdlink.chiron.toolbox.CollectingException;
import com.otcdlink.chiron.toolbox.TcpPortBooker;
import com.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.netty.handler.codec.http.HttpResponseStatus;
import mockit.FullVerifications;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class AbstractTwilioCallbackTest {


  @Test
  public void createTwiml() throws Exception {

    new StrictExpectations() {{
      twilioHttpCallback.createTwiml( "someToken" ) ;
      result = "<html>processed 'someToken'</html>" ;
    }} ;

    httpClient.httpGet( url( "/twiml/someToken" ), recorder ) ;
    WatchedResponseAssert.assertThat( recorder.nextOutcome() )
        .isComplete()
        .hasStatusCode( HttpResponseStatus.OK )
        .hasContent( "<html>processed 'someToken'</html>\n" )
    ;

    new FullVerifications() {{ }} ;
  }

  @Test
  public void status() throws Exception {

    new StrictExpectations() {{
      twilioHttpCallback.updateTwilioCallStatus( "someToken", TwilioStatus.CALL_COMPLETED ) ;
    }} ;

    httpClient.httpPost(
        url( "/status/someToken" ),
        ImmutableMultimap.of( "CallStatus", "completed" ),
        recorder
    ) ;
    WatchedResponseAssert.assertThat( recorder.nextOutcome() )
        .isComplete()
        .hasStatusCode( HttpResponseStatus.OK )
    ;
    new FullVerifications() {{ }} ;
  }


  @Test
  public void contextHome() throws Exception {
    httpClient.httpGet( url( "/" ), recorder ) ;
    WatchedResponseAssert.assertThat( recorder.nextOutcome() )
        .isComplete()
        .hasStatusCode( HttpResponseStatus.OK )
    ;
  }


  @Test
  public void fallback() throws Exception {
    httpClient.httpGet( url( "/fallback" ), recorder ) ;
    WatchedResponseAssert.assertThat( recorder.nextOutcome() )
        .isComplete()
        .hasStatusCode( HttpResponseStatus.OK )
    ;
  }


// =======
// Fixture
// =======

  protected final int port = TcpPortBooker.THIS.find() ;

  protected static final String CONTEXT_PATH = "/twilio" ;

  protected final NettyHttpClient.Recorder recorder = new NettyHttpClient.Recorder() ;

  protected final NettyHttpClient httpClient = new NettyHttpClient() ;


  @SuppressWarnings( "WeakerAccess" )
  public @Injectable
  TwilioHttpCallback twilioHttpCallback = null ;

  @Before
  public void setUp() throws Exception {
    CollectingException.newCollector().execute(
        () -> start( port, CONTEXT_PATH, twilioHttpCallback ),
        httpClient::start
    ).throwIfAny( "Test setUp failed." ) ;

  }

  @After
  public void tearDown() throws Exception {
    CollectingException.newCollector().execute(
        this::stop,
        httpClient::stop
    ).throwIfAny( "Test tearDown failed." ) ;
  }

  protected final URL url( final String pathSegment ) throws MalformedURLException {
    return new URL( "http://localhost:" + port + CONTEXT_PATH + pathSegment ) ;
  }

  protected abstract void start(
      int port,
      String contextPath,
      TwilioHttpCallback twilioHttpCallback
  ) throws Exception ;

  protected abstract void stop() throws Exception ;

}
