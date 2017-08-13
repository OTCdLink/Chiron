package com.otcdlink.chiron.fixture.tcp.forward;

import com.otcdlink.chiron.fixture.tcp.Junction;
import com.otcdlink.chiron.fixture.tcp.Junctor;
import com.otcdlink.chiron.fixture.tcp.TcpTransitServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Establishes the connection with the Target, then creates a {@link Junction}.
 */
class ForwardJunctor extends Junctor {

  private final InetSocketAddress targetAddress ;

  ForwardJunctor(
      final Logger logger,
      final InetSocketAddress proxyListenAddress,
      final InetSocketAddress targetAddress,
      final Channel initiatorChannel,
      final TcpTransitServer.Watcher watcher,
       final TcpTransitServer.PipelineConfigurator.Factory pipelineConfiguratorFactory,
      final int connectTimeoutMs
  ) {
    super(
        logger,
        proxyListenAddress,
        initiatorChannel,
        watcher,
        pipelineConfiguratorFactory,
        connectTimeoutMs
    ) ;
    this.targetAddress = checkNotNull( targetAddress ) ;
  }


  public CompletableFuture< Junction > initializeInitiatorChannel() {
    final CompletableFuture< Junction > junctionFuture = new CompletableFuture<>() ;
    connectToTarget(
        this.initiatorChannel.pipeline().firstContext(),
        targetAddress,
        junctionFuture
    ) ;
    return junctionFuture ;
  }

  @Override
  protected void junctionEstablished(
      final ChannelHandlerContext initiatorContext,
      final Channel targetChannel,
      final CompletableFuture< Junction > junctionFuture,
      final Junction junction
  ) {
    configureJunction( targetChannel, junction ) ;
    logger.debug( "Configured " + junction + "." ) ;
    junctionFuture.complete( junction ) ;
    this.initiatorChannel.read() ;
  }
}
