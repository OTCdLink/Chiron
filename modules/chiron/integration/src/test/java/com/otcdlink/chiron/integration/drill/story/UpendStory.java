package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.fixture.http.WatchedResponseAssert;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.ConnectorDrill.ForUpendConnector.HttpRequestRelayerKind;
import com.otcdlink.chiron.toolbox.netty.Hypermessage;
import com.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.otcdlink.chiron.mockster.Mockster.any;
import static com.otcdlink.chiron.mockster.Mockster.exactly;

public class UpendStory {

  @Test
  public void connectUpendAndEcho() throws Exception {
    LOGGER.info( "*** Test starts here ***" ) ;
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .fakeDownend()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.NONE )
            .done()
        .build()
    ) {
      final ConnectorDrill.ForFakeDownend forFakeDownend = drill.forFakeDownend() ;

      forFakeDownend.halfDuplex().texting().emitWithDutyCall().requestEcho( TAG_T0, "Yay" ) ;
      drill.forUpendConnector().upwardDutyMock().requestEcho( any(), exactly( "Yay" ) ) ;

      // Can't send to Downend if there is no Session.
    }
  }

  @Test
  public void httpOk() throws Exception {
    LOGGER.info( "*** Test starts here ***" ) ;
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .fakeDownend()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.NONE )
            .withHttpRelayer( HttpRequestRelayerKind.ALWAYS_OK )
            .done()
        .build()
    ) {
      final NettyHttpClient.CompleteResponse completeResponse = drill.forFakeDownend().httpRequest(
          new Hypermessage.Request.Get(
              drill.internetAddressPack().upendWebSocketUrlWithHttpScheme() ) )
      ;
      WatchedResponseAssert.assertThat( completeResponse )
          .isComplete()
          .hasStatusCode( HttpResponseStatus.OK )
      ;
    }
  }

  @Test
  public void httpNotFound() throws Exception {
    LOGGER.info( "*** Test starts here ***" ) ;
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .fakeDownend()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.NONE )
            .withHttpRelayer( HttpRequestRelayerKind.ALWAYS_OK )
            .done()
        .build()
    ) {
      final NettyHttpClient.CompleteResponse completeResponse = drill.forFakeDownend().httpRequest(
          new Hypermessage.Request.Get(
              drill.internetAddressPack().upendMalformedUrl() ) )
      ;
      WatchedResponseAssert.assertThat( completeResponse )
          .isComplete()
          .hasStatusCode( HttpResponseStatus.BAD_REQUEST )
      ;
    }
  }




// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( UpendStory.class ) ;

  private static final Command.Tag TAG_T0 = new Command.Tag( "T0" ) ;


}
