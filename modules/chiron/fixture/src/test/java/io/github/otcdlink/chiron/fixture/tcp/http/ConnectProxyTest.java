package io.github.otcdlink.chiron.fixture.tcp.http;

import io.github.otcdlink.chiron.fixture.NettyLeakDetectorRule;
import io.github.otcdlink.chiron.fixture.tcp.AbstractTcpTransitServerTest;
import io.github.otcdlink.chiron.fixture.tcp.EchoClient;
import io.github.otcdlink.chiron.fixture.tcp.EchoServer;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.github.otcdlink.chiron.toolbox.text.LineBreak;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitServer.Edge.INITIATOR;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitServer.Edge.TARGET;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitServer.Watcher.Progress.DELAYED;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitServer.Watcher.Progress.EMITTED;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitServer.Watcher.Progress.RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;

public class ConnectProxyTest extends AbstractTcpTransitServerTest {

  @Test
  public void echoWithLag() throws Exception {
    try(
        final EchoServer ignored1 = EchoServer.newStarted( targetAddress ) ;
        final ConnectProxy connectProxy = newTransitServerStarted() ;
        final EchoClient echoClient = newClientConnected( true )
    ) {
      connectProxy.lag( 200 ) ;
      echoClient.write( "Hello" ) ;
      assertThat( echoClient.read() ).isEqualTo( "Hello" ) ;

      checkNextEvent( INITIATOR, RECEIVED ) ;
      checkNextEvent( INITIATOR, DELAYED ) ;
      checkNextEvent( INITIATOR, EMITTED ) ;
      checkNextEvent( TARGET, RECEIVED ) ;
      checkNextEvent( TARGET, DELAYED ) ;
      checkNextEvent( TARGET, EMITTED ) ;

      echoClient.write( "World" ) ;
      assertThat( echoClient.read() ).isEqualTo( "World" ) ;


      checkNextEvent( INITIATOR, RECEIVED ) ;
      checkNextEvent( INITIATOR, DELAYED ) ;
      checkNextEvent( INITIATOR, EMITTED ) ;
      checkNextEvent( TARGET, RECEIVED ) ;
      checkNextEvent( TARGET, DELAYED ) ;
      checkNextEvent( TARGET, EMITTED ) ;
    }
  }



// =======
// Fixture
// =======

  @Rule
  public final NettyLeakDetectorRule nettyLeakDetectorRule = new NettyLeakDetectorRule() ;

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