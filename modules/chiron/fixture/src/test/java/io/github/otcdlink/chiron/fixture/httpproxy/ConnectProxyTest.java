package io.github.otcdlink.chiron.fixture.httpproxy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.github.otcdlink.chiron.toolbox.TcpPortBooker;
import io.github.otcdlink.chiron.toolbox.netty.EventLoopGroupOwner;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;
import io.github.otcdlink.chiron.toolbox.text.ByteArrayEmbellisher;
import io.github.otcdlink.chiron.toolbox.text.Plural;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Edge.INITIATOR;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Edge.TARGET;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Watcher.Progress.DELAYED;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Watcher.Progress.EMITTED;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Watcher.Progress.RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;

public class ConnectProxyTest {

  @Test
  public void startStop() throws Exception {
    final ConnectProxy proxy = newProxyStarted() ;
    LOGGER.info( "On Mavericks you can list open ports with: lsof -P -i TCP@localhost:10001" ) ;
    proxy.stop()
        .whenComplete( EventLoopGroupOwner.logStopCompletion( LOGGER ) )
        .join()
    ;
    proxy.start().join() ;
    proxy.stop().join() ;
  }

  @Test
  public void echo() throws Exception {
    try(
        final EchoServer ignored1 = EchoServer.newStarted( targetAddress ) ;
        final ConnectProxy ignored2 = newProxyStarted() ;
        final EchoClient echoClient = EchoClient.newStarted( listenAddress )
    ) {
      EchoClient.connectToProxy( echoClient, targetAddress ) ;

      echoClient.write( "Hello" ) ;
      assertThat( echoClient.read() ).isEqualTo( "Hello" ) ;

      checkNextEvent( INITIATOR, RECEIVED ) ;
      checkNextEvent( INITIATOR, EMITTED ) ;
      checkNextEvent( TARGET, RECEIVED ) ;
      checkNextEvent( TARGET, EMITTED ) ;

    }
  }

  @Test
  public void noTarget() throws Exception {
    try(
        final ConnectProxy ignored1 = newProxyStarted() ;
        final EchoClient echoClient = EchoClient.newStarted( listenAddress )
    ) {
      EchoClient.tryConnectToProxy( echoClient, targetAddress ) ;
      assertThat( echoClient.read() )
          .describedAs( "Null read means remote socket got closed" )
          .isNull()
      ;
    }
  }

  @Test
  public void echoWithLag() throws Exception {
    try(
        final EchoServer ignored1 = EchoServer.newStarted( targetAddress ) ;
        final ConnectProxy connectProxy = newProxyStarted() ;
        final EchoClient echoClient = EchoClient.newStarted( listenAddress )
    ) {
      EchoClient.connectToProxy( echoClient, targetAddress ) ;

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

  @Test
  public void propagateClosedServerSocket() throws Exception {
    try(
        final EchoServer echoServer = EchoServer.newStarted( targetAddress ) ;
        final ConnectProxy ignored = newProxyStarted() ;
        final EchoClient echoClient = EchoClient.newStarted( listenAddress )
    ) {
      EchoClient.connectToProxy( echoClient, targetAddress ) ;

      echoClient.write( "Hello" ) ;
      assertThat( echoClient.read() ).isEqualTo( "Hello" ) ;

      checkNextEvent( INITIATOR, RECEIVED ) ;
      checkNextEvent( INITIATOR, EMITTED ) ;
      checkNextEvent( TARGET, RECEIVED ) ;
      checkNextEvent( TARGET, EMITTED ) ;

      echoServer.stop() ;

      assertThat( echoClient.read() ).isNull() ;

    }
  }


  @Test
  public void nanoStress() throws Exception {
    stress( 1, 1, 1 ) ;
  }

  @Test
  public void miniStress() throws Exception {
    stress( 1, 1, 1025 ) ; // 1024 is OK, 1025 used to break some tests.
  }

  @Test
  public void mediumStress() throws Exception {
    stress( 3, 11, 10_007 ) ;
  }

  @Test
  @Ignore( "Takes too long and breaks IDE if forgot to disable payload dump" )
  public void bigStress() throws Exception {
    stress( 140, 200, 10_007 ) ;
  }



// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( ConnectProxyTest.class ) ;

  private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup() ;

  private final InetSocketAddress listenAddress =
      new InetSocketAddress( "localhost", TcpPortBooker.THIS.find() ) ;

  private final InetSocketAddress targetAddress =
      new InetSocketAddress( "localhost", TcpPortBooker.THIS.find() ) ;

  private final RecordingWatcher recordingWatcher = new RecordingWatcher() ;

  private ConnectProxy newProxyStarted() throws Exception {
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

  @After
  public void tearDown() throws Exception {
    eventLoopGroup.shutdownGracefully() ;
  }

  private void checkNextEvent(
      final HttpProxy.Edge edge,
      final HttpProxy.Watcher.Progress progress
  ) throws InterruptedException {
    final HttpProxy.Transfer.Event transferEvent = recordingWatcher.next() ;
    final String description = HttpProxy.Transfer.Event.class.getSimpleName() + ": " + transferEvent ;
    assertThat( transferEvent.onset() ).describedAs( description ).isEqualTo( edge ) ;
    assertThat( transferEvent.progress() ).describedAs( description ).isEqualTo( progress ) ;
  }


  private static class RecordingWatcher implements HttpProxy.Watcher {

    private final BlockingQueue< HttpProxy.Transfer.Event > queue = new LinkedBlockingDeque<>() ;

    public HttpProxy.Transfer.Event next() throws InterruptedException {
      return queue.take() ;
    }

    @Override
    public boolean bytesInDetail() {
      return LOG_BYTES ;
    }

    @Override
    public void onTransfer( final HttpProxy.Transfer.Event transferEvent ) {
      final StringBuilder stringBuilder = new StringBuilder() ;
      stringBuilder.append( "Transfer happening: " ) ;
      stringBuilder.append( transferEvent.toString() ) ;
      if( transferEvent.hasPayload() && LOG_BYTES ) {
        @SuppressWarnings( "UnusedAssignment" )
        final ByteArrayEmbellisher byteArrayEmbellisher =
            new ByteArrayEmbellisher( transferEvent.payload() ) ;
        stringBuilder
            .append( "\n  " ).append( byteArrayEmbellisher.hexadecimalString )
            .append( "\n  " ).append( byteArrayEmbellisher.humanReadableString )
        ;
      }

      queue.add( transferEvent ) ;
      LOGGER.info( stringBuilder.toString() ) ;
    }
  }

  private void stress(
      final int initiatorCount,
      final int salvoCount,
      final int salvoSize
  ) throws Exception {

    final ImmutableList< EchoStressClient > stressClients ;
    {
      final ImmutableList.Builder< EchoStressClient > stressClientBuilder = ImmutableList.builder() ;
      for( int i = 0 ; i < initiatorCount ; i ++ ) {
        stressClientBuilder.add(
            new EchoStressClient( i, listenAddress, targetAddress, salvoCount, salvoSize ) ) ;
      }
      stressClients = stressClientBuilder.build() ;
    }

    LOGGER.info(
        "Preparing to run " + Plural.s( initiatorCount, "initiator" ) + " " +
            "with " + Plural.s( salvoCount, "salvo" ) + " of " + Plural.bytes( salvoSize ) + " each, " +
            "for a total of " + Plural.bytes( ( long ) salvoCount * ( long ) salvoSize ) + " " +
            "per initiator ..."
    ) ;
    try(
        final EchoServer echoServer = new EchoServer( targetAddress, initiatorCount, 10_000 ) ;
        final ConnectProxy ignored2 = newProxyStarted()
    ) {
      echoServer.start() ;
      final ListenableFuture< ? extends List< ? > > allTerminatedFuture =
          Futures.allAsList( Iterables.transform( stressClients, EchoStressClient::run ) ) ;
      allTerminatedFuture.get() ;
    }
  }

  private static void checkAggregatedRead(
      final AsciiStringReader asciiStringReader,
      final String expected
  )
      throws IOException
  {
    final StringBuilder accumulator = new StringBuilder() ;
    while( true ) {
      accumulator.append( asciiStringReader.read() ) ;
      final String aggregated = accumulator.toString() ;
      LOGGER.debug( "Aggregated so far: \n" ) ;
      if( expected.equals( aggregated ) ) {
        break ;
      }
    }
  }

  private static final boolean LOG_BYTES = false ;

  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.debug( "====== Ready to run tests ======" ) ;
  }


}