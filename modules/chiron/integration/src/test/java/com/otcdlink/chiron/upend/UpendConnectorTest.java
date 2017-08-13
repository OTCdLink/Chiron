package com.otcdlink.chiron.upend;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.fixture.http.WatchedResponseAssert;
import com.otcdlink.chiron.toolbox.TcpPortBooker;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import com.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;

@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class UpendConnectorTest {

  @Test
  public void httpOk() throws Exception {
    fixture.initializeAndStart( fixture::httpEchoSetup ) ;
    fixture.httpClient().httpGet( fixture.urlPrebuilder().home(), fixture.httpResponseWatcher ) ;
    final NettyHttpClient.Outcome outcome = fixture.httpResponseWatcher.nextOutcome() ;
    WatchedResponseAssert.assertThat( outcome ).isComplete()
        .hasStatusCode( HttpResponseStatus.OK ) ;
  }

  @Test
  public void malformedUri() throws Exception {
    fixture.initializeAndStart( fixture::httpEchoSetup ) ;
    fixture.httpClient().httpGet(
        fixture.urlPrebuilder().malformed(),
        fixture.httpResponseWatcher
    ) ;
    final NettyHttpClient.Outcome outcome = fixture.httpResponseWatcher.nextOutcome() ;
    WatchedResponseAssert.assertThat( outcome ).isComplete()
        .hasStatusCode( HttpResponseStatus.BAD_REQUEST ) ;
  }


// =======
// Fixture
// =======

  private final UpendConnectorFixture fixture = fixture() ;

  private static UpendConnectorFixture fixture() {
    final TimeKit< UpdateableClock > timeKit = TimeKit.instrumentedTimeKit( Stamp.FLOOR ) ;
    return fixture( timeKit.designatorFactory ) ;
  }

  private static UpendConnectorFixture fixture( final Designator.Factory designatorFactory ) {
    return new UpendConnectorFixture( designatorFactory, TcpPortBooker.THIS.find() ) ;
  }

  @After
  public void tearDown() throws Exception {
    fixture.stopAll() ;
    Assertions.assertThat( fixture.httpResponseWatcher.pendingResponses() )
        .describedAs( "Did not consume every Response" )
        .asList().isEmpty()
    ;
  }
}