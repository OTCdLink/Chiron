package io.github.otcdlink.chiron.fixture.http;

import io.github.otcdlink.chiron.toolbox.TcpPortBooker;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import io.github.otcdlink.chiron.toolbox.internet.Hostname;
import io.github.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class TinyHttpServerTest {

  @Test
  public void justRespond() throws Exception {
    final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(
        0, ExecutorTools.newThreadFactory( TinyHttpServerTest.class.getSimpleName() ) ) ;
    try(
        final TinyHttpServer httpServer = new TinyHttpServer(
            eventLoopGroup, port, new EchoResponder() ) ;
        final NettyHttpClient httpClient = new NettyHttpClient( eventLoopGroup, 10_000 )
    ) {
      CompletableFuture.allOf( httpServer.start(), httpClient.start() ).join() ;
      LOGGER.info( "All started.") ;
      final NettyHttpClient.Recorder recordingWatcher = new NettyHttpClient.Recorder() ;
      httpClient.httpGet( url( "/hello" ), recordingWatcher ) ;
      final NettyHttpClient.Outcome outcome = recordingWatcher.nextOutcome() ;
      WatchedResponseAssert.assertThat( outcome )
          .isComplete()
          .hasContent( "Echoed 'http://localhost:" + port + "/hello'." )
      ;
    } finally {
      eventLoopGroup.shutdownGracefully() ;
    }

  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( TinyHttpServerTest.class ) ;
  private final int port = TcpPortBooker.THIS.find() ;

  private URL url( final String path ) {
    return UrxTools.parseUrlQuiet( "http://" + Hostname.LOCALHOST.asString() + ":" + port + path ) ;
  }

  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.info( "=== All Netty classes loaded, tests can begin ===" ) ;
  }
}