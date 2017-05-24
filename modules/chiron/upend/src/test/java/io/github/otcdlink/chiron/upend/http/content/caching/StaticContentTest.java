package io.github.otcdlink.chiron.upend.http.content.caching;

import io.github.otcdlink.chiron.toolbox.TcpPortBooker;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.github.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.github.otcdlink.chiron.upend.TimeKit;
import io.github.otcdlink.chiron.upend.UpendConnector;
import io.github.otcdlink.chiron.upend.http.dispatch.BareHttpDispatcher;
import io.github.otcdlink.chiron.upend.http.dispatch.HttpDispatcher;
import io.github.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.function.Consumer;

public abstract class StaticContentTest {

  protected final NettyHttpClient.Recorder httpResponseRecorder = new NettyHttpClient.Recorder() ;

  private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup( 6 ) ;

  private final TimeKit timeKit = TimeKit.fromSystemClock() ;

  private final InetSocketAddress serverAddress =
      HostPort.createForLocalhost( TcpPortBooker.THIS.find() ).asInetSocketAddress() ;

  protected final NettyHttpClient httpClient = new NettyHttpClient(
      eventLoopGroup, httpClientTimeoutMs() ) ;
  {
    httpClient.start().join() ;
  }

  protected int httpClientTimeoutMs() {
    return 1000 ;
  }

  private UpendConnector upendConnector = null ;

  protected StaticContentTest() throws UnknownHostException { }

  protected final URI httpRequestUri( final String resourcePath ) {
    return UrxTools.parseUriQuiet(
        "http://" + serverAddress.getHostName() + ":" + serverAddress.getPort() + resourcePath ) ;
  }

  protected final URL httpRequestUrl( final String resourcePath ) {
    return UrxTools.parseUrlQuiet(
        "http://" + serverAddress.getHostName() + ":" + serverAddress.getPort() + resourcePath ) ;
  }

  protected final void httpGet( final String resourcePath ) {
    httpClient.httpGet( httpRequestUrl( resourcePath ), httpResponseRecorder ) ;
  }

  @SafeVarargs
  protected final void initialize(
      final Consumer< HttpDispatcher< ?, ?, Void, Void, ? extends HttpDispatcher > >...
          httpDispatcherConfigurators
  ) throws Exception {
    HttpDispatcher< ?, ?, Void, Void, ? extends HttpDispatcher > httpDispatcher =
        HttpDispatcher.newDispatcher( timeKit.designatorFactory ) ;
    for(
        final Consumer< ? extends BareHttpDispatcher > configurator : httpDispatcherConfigurators
    ) {
      httpDispatcher = httpDispatcher.include( configurator ) ;
    }
    final HttpRequestRelayer httpRequestRelayer = httpDispatcher.notFound().build() ;

    upendConnector = new UpendConnector<>( new UpendConnector.Setup< Void >(
        eventLoopGroup,
        serverAddress,
        null,
        null,
        "some-version",
        null,
        null,
        null,
        null,
        httpRequestRelayer,
        null,
        null,
        null,
        null
    ) ) ;

    upendConnector.start().join() ;

  }

  @After
  public void tearDown() throws Exception {
    if( upendConnector != null ) {
      upendConnector.stop() ;
    }
    eventLoopGroup.shutdownGracefully() ;
  }
}
