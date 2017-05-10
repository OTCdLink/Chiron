package io.github.otcdlink.chiron.fixture.tcp.forwarder;

import io.github.otcdlink.chiron.fixture.tcp.AbstractTcpTransitServerTest;
import io.github.otcdlink.chiron.fixture.tcp.EchoClient;
import io.github.otcdlink.chiron.fixture.tcp.TcpTransitServer;
import io.github.otcdlink.chiron.fixture.tcp.forward.PortForwarder;

import java.io.IOException;
import java.net.InetSocketAddress;

public class PortForwarderTest extends AbstractTcpTransitServerTest {


// =======
// Fixture
// =======


  @Override
  protected PortForwarder newTransitServerStarted() throws Exception {
    return newProxyStarted( TcpTransitServer.PipelineConfigurator.Factory.NULL_FACTORY ) ;
  }

  private PortForwarder newProxyStarted(
      final TcpTransitServer.PipelineConfigurator.Factory pipelineConfiguratorFactory
  ) throws Exception {
    return PortForwarder.createAndStart(
        eventLoopGroup,
        listenAddress,
        targetAddress,
        recordingWatcher,
        pipelineConfiguratorFactory,
        100_000  // Big stress test may take a long to start.
    ) ;
  }

  @Override
  protected EchoClient newClientConnected( boolean ensureConnectionSuccess ) throws IOException {
    final EchoClient echoClient = new EchoClient( listenAddress, 1000 ) ;
    echoClient.start() ;
    return echoClient ;
  }

  @Override
  protected String tryConnectToTransitServer(
      final EchoClient echoClient,
      final InetSocketAddress targetAddress
  ) throws IOException {
    return "" ;
  }
}