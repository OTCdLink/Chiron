package io.github.otcdlink.chiron.fixture.tcp;

import io.github.otcdlink.chiron.fixture.tcp.http.HttpProxy;
import io.github.otcdlink.chiron.toolbox.netty.NettySocketServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract class for some kind of TCP forwarding.
 */
public abstract class AbstractTcpTransitServer extends NettySocketServer implements TcpTransitServer {

  private static final Logger LOGGER = LoggerFactory.getLogger( HttpProxy.class ) ;

  protected final Watcher watcher ;
  protected final PipelineConfigurator.Factory pipelineConfiguratorFactory ;

  /**
   * May be ignored.
   * @see ChannelConfig#setConnectTimeoutMillis(int)
   */
  protected final int connectTimeoutMs ;


  protected AbstractTcpTransitServer(
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
    // Don't log now, a non-initialized field in a subclass could wreck toString().
  }


  protected void applyToAll( final Consumer< Junction > visitor ) {
    applyToAllChannels( channel -> {
        final Junction junction = channel.attr( TcpTransitTools.JUNCTION ).get() ;
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

    TcpTransitTools.addLoggingHandlerFirst(
        initiatorChannel.pipeline(), "initiator-side-out" ) ;

    final CompletableFuture< Junction > junctionFuture = junctor.initializeInitiatorChannel() ;
    junctionFuture.whenCompleteAsync( ( junction, throwable ) -> {
      if( junction == null ) {
        initiatorChannel.close() ;
      } else {
        initiatorChannel.attr( TcpTransitTools.JUNCTION ).set( junction ) ;
        prepare( junction ) ;
        junction.allowNextReads() ;
      }
    } ) ;
  }

  protected void prepare( Junction junction ) { }

  @Override
  protected void addChildOptions( final ChannelOptionAdder channelOptionAdder ) {
    channelOptionAdder.addOption( ChannelOption.AUTO_READ, Boolean.FALSE ) ;
  }

// =========
// Lifecycle
// =========


  protected abstract Junctor newJunctor( final Channel initiatorChannel ) ;






}
