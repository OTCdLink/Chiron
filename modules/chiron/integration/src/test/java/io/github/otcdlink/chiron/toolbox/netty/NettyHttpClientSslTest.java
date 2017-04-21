package io.github.otcdlink.chiron.toolbox.netty;

import io.github.otcdlink.chiron.fixture.http.EchoResponder;
import io.github.otcdlink.chiron.fixture.http.TinyHttpServer;
import io.github.otcdlink.chiron.middle.AutosignerFixture;
import io.github.otcdlink.chiron.toolbox.TcpPortBooker;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.github.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import io.github.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

public class NettyHttpClientSslTest {

  @Test
  public void helloNoHttps() throws Exception {
    try(
        final TinyHttpServer ignored = createAndStartServer( eventLoopGroup, hostPort, null ) ;
        final NettyHttpClient nettyHttpClient = createAndStartClient( eventLoopGroup, null )
    ) {
      httpQuickConversation( nettyHttpClient, SchemeHostPort.Scheme.HTTP ) ;
    }
   }

  @Test
  public void helloWithHttps() throws Exception {
    try(
        final TinyHttpServer ignored = createAndStartServer(
            eventLoopGroup, hostPort, AutosignerFixture.sslEngineFactoryForServer() ) ;
        final NettyHttpClient nettyHttpClient = createAndStartClient(
            eventLoopGroup, AutosignerFixture.sslEngineFactoryForClient() )
    ) {
      httpQuickConversation( nettyHttpClient, SchemeHostPort.Scheme.HTTPS ) ;
    }
   }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( NettyHttpClientSslTest.class ) ;

  private void httpQuickConversation(
      final NettyHttpClient nettyHttpClient,
      final SchemeHostPort.Scheme scheme
  )
      throws InterruptedException, java.util.concurrent.ExecutionException
  {
    final CompletableFuture< NettyHttpClient.CompleteResponse > responseFuture =
        nettyHttpClient.httpRequest( new Hypermessage.Request.Get(
            SchemeHostPort.create( scheme, hostPort ),
            UrxTools.parseUriQuiet( "/whatever" ) )
        )
        ;
    assertThat( responseFuture.get().response.responseStatus )
        .isEqualTo( HttpResponseStatus.OK ) ;
  }


  private final HostPort hostPort = HostPort.createForLocalhost( TcpPortBooker.THIS.find() ) ;

  private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup( 4 ) ;

  //  private static final long TIMEOUT = 5_000 ;
  private static final long TIMEOUT = 5_000_000 ;

  private TinyHttpServer createAndStartServer(
      final EventLoopGroup eventLoopGroup,
      final HostPort hostPort,
      final SslEngineFactory.ForServer sslEngineFactory
  ) {
    final TinyHttpServer nettySocketServer = new TinyHttpServer(
        eventLoopGroup,
        sslEngineFactory,
        hostPort.port,
        new EchoResponder()
    ) ;
    nettySocketServer.start().join() ;
    return nettySocketServer ;
  }

  private NettyHttpClient createAndStartClient(
      final EventLoopGroup eventLoopGroup,
      final SslEngineFactory.ForClient sslEngineFactory
  ) {
    final NettyHttpClient nettyHttpClient = new NettyHttpClient(
        eventLoopGroup, 10_000, sslEngineFactory ) ;
    nettyHttpClient.start().join() ;
    return nettyHttpClient ;
  }

  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.info( "=== Test begins here ===" ) ;
  }


}