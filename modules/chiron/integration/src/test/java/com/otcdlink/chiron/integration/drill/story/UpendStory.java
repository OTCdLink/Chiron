package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.fixture.NettyLeakDetectorExtension;
import com.otcdlink.chiron.fixture.http.WatchedResponseAssert;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.ConnectorDrill.ForUpendConnector.HttpRequestRelayerKind;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.toolbox.netty.Hypermessage;
import com.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;

import static com.otcdlink.chiron.mockster.Mockster.any;
import static com.otcdlink.chiron.mockster.Mockster.exactly;

@ExtendWith( NettyLeakDetectorExtension.class )
class UpendStory {

  @Test
  void connectUpendAndEcho() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .fakeDownend()
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.NONE )
            .done()
        .build()
    ) {
      final ConnectorDrill.ForFakeDownend forFakeDownend = drill.forFakeDownend() ;

      forFakeDownend.duplex().texting().emitWithDutyCall()
          .requestEcho( SketchLibrary.TAG_TR0, "Yay" ) ;
      drill.forUpendConnector().upwardDutyMock().requestEcho( any(), exactly( "Yay" ) ) ;

      // Can't send to Downend if there is no Session.
    }
  }

  @Disabled( "Work in progress" )
  @Test
  void sessionPrimer() throws Exception {

    final Semaphore intercepted = new Semaphore( 0 ) ;
    final CommandInterceptor commandInterceptor = new CommandInterceptor() {
      @Override
      public boolean interceptUpward( final Command command, final Sink sink ) {
        return false ;
      }
      @Override
      public boolean interceptDownward( final Command command, final Sink sink ) {
        return false ;
      }
    } ;
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .forDownendConnector().done()
        .forUpendConnector()
            .withCommandInterceptor( commandInterceptor )
            .done()
        .build()
    ) {
      final ConnectorDrill.ForFakeDownend forFakeDownend = drill.forFakeDownend() ;

      forFakeDownend.duplex().texting().emitWithDutyCall()
          .requestEcho( SketchLibrary.TAG_TR0, "Yay" ) ;
      drill.forUpendConnector().upwardDutyMock().requestEcho( any(), exactly( "Yay" ) ) ;

      // Can't send to Downend if there is no Session.
    }
  }

  @Test
  void httpOk() throws Exception {
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
  void httpNotFound() throws Exception {
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


}
