package io.github.otcdlink.chiron.fixture.httpproxy;

import io.github.otcdlink.chiron.toolbox.netty.NettySocketServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Watcher.NULL;

/**
 * HTTP proxy for CONNECT method only.
 */
public class ConnectProxy extends NettySocketServer implements HttpProxy {

  private static final Logger LOGGER = LoggerFactory.getLogger( HttpProxy.class ) ;
  static final boolean DUMP_PAYLOADS = false ;

  private final Watcher watcher ;
  private final PipelineConfigurator.Factory pipelineConfiguratorFactory ;

  /**
   * May be ignored.
   * @see ChannelConfig#setConnectTimeoutMillis(int)
   */
  private final int connectTimeoutMs ;


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
      final EventLoopGroup eventLoopGroup,
      final InetSocketAddress listenAddress
  ) {
    this(
        listenAddress,
        null,
        eventLoopGroup,
        Watcher.NULL,
        PipelineConfigurator.Factory.NULL_FACTORY,
        1000
    ) ;
  }

  public ConnectProxy(
      final InetSocketAddress listenAddress,
      final EventLoopGroup eventLoopGroup,
      final Watcher watcher,
      final int connectTimeoutMs,
      final PipelineConfigurator.Factory pipelineConfiguratorFactory
  ) {
    this(
        listenAddress,
        null,
        checkNotNull( eventLoopGroup ),
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
  }

  public ConnectProxy(
      final InetSocketAddress listenAddress,
      final Watcher watcher,
      final int connectTimeoutMs,
      final PipelineConfigurator.Factory pipelineConfiguratorFactory
  ) {
    this(
        listenAddress,
        EventLoopGroupFactory::defaultFactory,
        null,
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
  }

  protected ConnectProxy(
      final InetSocketAddress listenAddress,
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactorySupplier,
      final EventLoopGroup eventLoopGroup,
      final Watcher watcher,
      final PipelineConfigurator.Factory pipelineConfiguratorFactory,
      final int connectTimeoutMs
  ) {
    super( listenAddress, eventLoopGroupFactorySupplier, eventLoopGroup ) ;
    checkArgument( connectTimeoutMs >= 0 ) ;
    this.connectTimeoutMs = connectTimeoutMs ;
    this.watcher = checkNotNull( watcher ) ;
    this.pipelineConfiguratorFactory = checkNotNull( pipelineConfiguratorFactory ) ;
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



  private void applyToAll( final Consumer< Junction > visitor ) {
    applyToAllChannels( channel -> {
        final Junction junction = channel.attr( HttpProxyTools.JUNCTION ).get() ;
        /** Leave synchronisation to {@link Junction} object, which has hands on both
         * initiator and target {@link Channel}s. */
        if( junction != null ) {
          visitor.accept( junction ) ;
        }
      }
    ) ;
  }



// ========
// Creation
// ========


  @Override
  protected void initializeChildChannel( final Channel initiatorChannel ) throws Exception {
    final Junctor junctor = newJunctor( initiatorChannel ) ;

    HttpProxyTools.addLoggingHandlerFirst(
        initiatorChannel.pipeline(), "initiator-side-out" ) ;

    final CompletableFuture< Junction > junctionFuture =
        junctor.initializeInitiatorChannel() ;
    junctionFuture.whenCompleteAsync( ( junction, throwable ) -> {
      if( junction == null ) {
        initiatorChannel.close() ;
      } else {
        initiatorChannel.attr( HttpProxyTools.JUNCTION ).set( junction ) ;
        junction.lag( lagMs.get() ) ;
        junction.allowNextReads() ;
      }
    } ) ;
  }

  @Override
  protected void addChildOptions( final ChannelOptionAdder channelOptionAdder ) {
    channelOptionAdder.addOption( ChannelOption.AUTO_READ, Boolean.FALSE ) ;
  }

// =========
// Lifecycle
// =========


  private Junctor newJunctor( final Channel initiatorChannel ) {
    return new Junctor(
        LOGGER,
        listenAddress(),
        initiatorChannel,
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
  }



  public static void main( final String... arguments ) throws Exception {
    ConnectProxy.createAndStart( new InetSocketAddress( "localhost", 10002 ) ) ;
  }




}
