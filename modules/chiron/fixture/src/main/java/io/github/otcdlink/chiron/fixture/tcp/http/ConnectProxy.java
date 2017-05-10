package io.github.otcdlink.chiron.fixture.tcp.http;

import io.github.otcdlink.chiron.fixture.tcp.AbstractTcpTransitServer;
import io.github.otcdlink.chiron.fixture.tcp.Junction;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitServer.Watcher.NULL;

/**
 * HTTP proxy for CONNECT method only.
 */
public class ConnectProxy extends AbstractTcpTransitServer implements HttpProxy {

  private static final Logger LOGGER = LoggerFactory.getLogger( HttpProxy.class ) ;

  private final AtomicInteger lagMs = new AtomicInteger( 0 ) ;

  public static ConnectProxy createAndStart(
      final EventLoopGroup eventLoopGroup,
      final InetSocketAddress listenAddress
  ) throws Exception {
    return createAndStart(
        eventLoopGroup,
        listenAddress,
        NULL,
        PipelineConfigurator.Factory.NULL_FACTORY,
        10
    ) ;
  }

  public static ConnectProxy createAndStart(
      final InetSocketAddress listenAddress
  ) throws Exception {
    return createAndStart(
        null,
        listenAddress,
        NULL,
        PipelineConfigurator.Factory.NULL_FACTORY,
        10
    ) ;
  }

  public static ConnectProxy createAndStart(
      final EventLoopGroup eventLoopGroup,
      final InetSocketAddress listenAddress,
      final Watcher watcher,
      final PipelineConfigurator.Factory pipelineConfiguratorFactory,
      final int connectTimeoutMs
  ) throws Exception {
    final ConnectProxy connectProxy = new ConnectProxy(
        listenAddress,
        eventLoopGroup == null ? EventLoopGroupFactory::defaultFactory : null,
        eventLoopGroup,
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
    final CompletableFuture< ? > onceDone = connectProxy.start() ;
    onceDone.join() ;
    return connectProxy ;
  }

  public ConnectProxy(
      final InetSocketAddress listenAddress,
      final Watcher watcher
  ) {
    this(
        listenAddress,
        EventLoopGroupFactory::defaultFactory,
        null,
        watcher,
        PipelineConfigurator.Factory.NULL_FACTORY,
        1000
    ) ;
  }

  public ConnectProxy(
      final InetSocketAddress listenAddress,
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
    LOGGER.info( "Created " + this + "." ) ;
  }


  @Override
  public final void lag( final int lagMs ) {
    checkArgument( lagMs >= 0 ) ;
    final int previous = this.lagMs.getAndSet( lagMs );
    if( previous != lagMs ) {
      applyToAll( junction -> junction.lag( lagMs ) ) ;
      LOGGER.info( "Lag set to " + lagMs + " ms.") ;
    }
  }



// =========
// Lifecycle
// =========


  protected HttpAwareJunctor newJunctor( final Channel initiatorChannel ) {
    return new HttpAwareJunctor(
        LOGGER,
        listenAddress(),
        initiatorChannel,
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
  }

  @Override
  protected void prepare( Junction junction ) {
    junction.lag( lagMs.get() ) ;
  }

  public static void main( final String... arguments ) throws Exception {
    ConnectProxy.createAndStart( new InetSocketAddress( "localhost", 10002 ) ) ;
  }




}
