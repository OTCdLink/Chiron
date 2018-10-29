package com.otcdlink.chiron.fixture.tcp.http;

import com.otcdlink.chiron.fixture.NettyLeakDetectorExtension;
import com.otcdlink.chiron.fixture.tcp.AbstractTcpTransitServerTest;
import com.otcdlink.chiron.fixture.tcp.EchoClient;
import com.otcdlink.chiron.fixture.tcp.EchoServer;
import com.otcdlink.chiron.fixture.tcp.TcpTransitServer;
import com.otcdlink.chiron.toolbox.internet.HostPort;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith( NettyLeakDetectorExtension.class )
public class ConnectProxyTest extends AbstractTcpTransitServerTest {

  @Test
  void echoWithLag() throws Exception {
    try(
        final EchoServer ignored1 = EchoServer.newStarted( targetAddress ) ;
        final ConnectProxy connectProxy = newTransitServerStarted() ;
        final EchoClient echoClient = newClientConnected( true )
    ) {
      connectProxy.lag( 200 ) ;
      echoClient.write( "Hello" ) ;
      assertThat( echoClient.read() ).isEqualTo( "Hello" ) ;

      checkNextEvent( TcpTransitServer.Edge.INITIATOR, TcpTransitServer.Watcher.Progress.RECEIVED ) ;
      checkNextEvent( TcpTransitServer.Edge.INITIATOR, TcpTransitServer.Watcher.Progress.DELAYED ) ;
      checkNextEvent( TcpTransitServer.Edge.INITIATOR, TcpTransitServer.Watcher.Progress.EMITTED ) ;
      checkNextEvent( TcpTransitServer.Edge.TARGET, TcpTransitServer.Watcher.Progress.RECEIVED ) ;
      checkNextEvent( TcpTransitServer.Edge.TARGET, TcpTransitServer.Watcher.Progress.DELAYED ) ;
      checkNextEvent( TcpTransitServer.Edge.TARGET, TcpTransitServer.Watcher.Progress.EMITTED ) ;

      echoClient.write( "World" ) ;
      assertThat( echoClient.read() ).isEqualTo( "World" ) ;


      checkNextEvent( TcpTransitServer.Edge.INITIATOR, TcpTransitServer.Watcher.Progress.RECEIVED ) ;
      checkNextEvent( TcpTransitServer.Edge.INITIATOR, TcpTransitServer.Watcher.Progress.DELAYED ) ;
      checkNextEvent( TcpTransitServer.Edge.INITIATOR, TcpTransitServer.Watcher.Progress.EMITTED ) ;
      checkNextEvent( TcpTransitServer.Edge.TARGET, TcpTransitServer.Watcher.Progress.RECEIVED ) ;
      checkNextEvent( TcpTransitServer.Edge.TARGET, TcpTransitServer.Watcher.Progress.DELAYED ) ;
      checkNextEvent( TcpTransitServer.Edge.TARGET, TcpTransitServer.Watcher.Progress.EMITTED ) ;
    }
  }



// =======
// Fixture
// =======

  @Override
  protected ConnectProxy newTransitServerStarted() throws Exception {
    return newProxyStarted( HttpProxy.PipelineConfigurator.Factory.NULL_FACTORY ) ;
  }

  private ConnectProxy newProxyStarted(
      final HttpProxy.PipelineConfigurator.Factory pipelineConfiguratorFactory
  ) throws Exception {
    return ConnectProxy.createAndStart(
        eventLoopGroup,
        listenAddress,
        recordingWatcher,
        pipelineConfiguratorFactory,
        100_000  // Big stress test may take a long to start.
    ) ;
  }


  private static final String BREAK = LineBreak.CRLF_HTTP11.asString ;

  @Override
  protected EchoClient newClientConnected( boolean ensureConnectionSuccess ) throws IOException {
    final EchoClient echoClient = new EchoClient( listenAddress, 1000 ) ;
    echoClient.start() ;
    if( ensureConnectionSuccess ) {
      assertThat( tryConnectToTransitServer( echoClient, targetAddress ) ).isEqualTo(
          "HTTP/1.1 200 Connection established" + BREAK +
              "via: 1.1 localhost" + BREAK +
              BREAK
      ) ;
    }
    return echoClient ;
  }

  protected String tryConnectToTransitServer(
      final EchoClient echoClient,
      final InetSocketAddress targetAddress
  ) throws IOException {
    final HostPort hostPort = HostPort.create( targetAddress ) ;
    echoClient.write(
        "CONNECT " + hostPort.asString() + " HTTP/1.1" + BREAK +
            "connection: keep-alive" + BREAK +
            "proxy-connection: keep-alive" + BREAK +
            "host: " + hostPort.asString() + BREAK +
            BREAK
    ) ;
    return echoClient.read() ;
  }

}