package io.github.otcdlink.chiron.fixture.tcp.forward;

import io.github.otcdlink.chiron.fixture.tcp.AbstractTcpTransitServer;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitServer.Watcher.NULL;

/**
 * Listens on {@link #listenAddress}, connects to {@link #targetAddress}, and forwards everything.
 */
public final class PortForwarder extends AbstractTcpTransitServer {

  private static final Logger LOGGER = LoggerFactory.getLogger( PortForwarder.class ) ;

  public static PortForwarder createAndStart(
      final EventLoopGroup eventLoopGroup,
      final InetSocketAddress listenAddress,
      final InetSocketAddress targetAddress
      ) throws Exception {
    return createAndStart(
        eventLoopGroup,
        listenAddress,
        targetAddress,
        NULL,
        PipelineConfigurator.Factory.NULL_FACTORY,
        10
    ) ;
  }

  public static PortForwarder createAndStart(
      final InetSocketAddress listenAddress,
      final InetSocketAddress targetAddress
  ) throws Exception {
    return createAndStart(
        null,
        listenAddress,
        targetAddress,
        NULL,
        PipelineConfigurator.Factory.NULL_FACTORY,
        10
    ) ;
  }

  public static PortForwarder createAndStart(
      final EventLoopGroup eventLoopGroup,
      final InetSocketAddress listenAddress,
      final InetSocketAddress targetAddress,
      final Watcher watcher,
      final PipelineConfigurator.Factory pipelineConfiguratorFactory,
      final int connectTimeoutMs
  ) throws Exception {
    final PortForwarder portForwarder = new PortForwarder(
        listenAddress,
        targetAddress,
        eventLoopGroup == null ? EventLoopGroupFactory::defaultFactory : null,
        eventLoopGroup,
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
    final CompletableFuture< ? > onceDone = portForwarder.start() ;
    onceDone.join() ;
    return portForwarder ;
  }

  public PortForwarder(
      final InetSocketAddress listenAddress,
      final InetSocketAddress targetAddress,
      final Watcher watcher
  ) {
    this(
        listenAddress,
        targetAddress,
        EventLoopGroupFactory::defaultFactory,
        null,
        watcher,
        PipelineConfigurator.Factory.NULL_FACTORY,
        1000
    ) ;
  }


  private final InetSocketAddress targetAddress ;

  public PortForwarder(
      final InetSocketAddress listenAddress,
      final InetSocketAddress targetAddress,
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactorySupplier,
      final EventLoopGroup eventLoopGroup,
      final Watcher watcher,
      final PipelineConfigurator.Factory pipelineConfiguratorFactory,
      final int connectTimeoutMs
  ) {
    super(
        listenAddress,
        eventLoopGroupFactorySupplier,
        eventLoopGroup,
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
    checkArgument( ! listenAddress.equals( targetAddress ),
        "Can't listen on target address " + targetAddress ) ;
    this.targetAddress = checkNotNull( targetAddress ) ;
    LOGGER.info( "Created " + this + "." ) ;
  }

  protected void augmentToString( final StringBuilder stringBuilder ) {
    stringBuilder
        .append( HostPort.niceHumanReadableString( listenAddress() ) )
        .append( "=>" )
        .append( HostPort.niceHumanReadableString( targetAddress ) )
    ;
  }



// =========
// Lifecycle
// =========

  protected ForwardJunctor newJunctor( final Channel initiatorChannel ) {
    return new ForwardJunctor(
        LOGGER,
        listenAddress(),
        targetAddress,
        initiatorChannel,
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
  }

  public static void main( final String... arguments ) throws Exception {
    if( arguments.length != 2 ) {
      throw new IllegalArgumentException( PortForwarder.class.getSimpleName() +
          " <listen_address:port> <target_address:port>" ) ;
    }
    final HostPort listenAddress = HostPort.parse( arguments[ 0 ] ) ;
    final HostPort targetAddress = HostPort.parse( arguments[ 1 ] ) ;
    PortForwarder.createAndStart(
        new NioEventLoopGroup(),
        listenAddress.asInetSocketAddress(),
        targetAddress.asInetSocketAddress()
    ) ;
  }


}
