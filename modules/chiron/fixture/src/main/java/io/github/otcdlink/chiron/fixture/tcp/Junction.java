package io.github.otcdlink.chiron.fixture.tcp;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.fixture.tcp.http.HttpProxy;
import io.github.otcdlink.chiron.toolbox.ImmutableCollectionTools;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.EGRESS_EXIT;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.INGRESS_ENTRY;
import static io.github.otcdlink.chiron.fixture.tcp.TcpTransitTools.ChannelHandlerName.INGRESS_EXIT;

/**
 * Ties two {@link Channel}s together by configuring their {@link ChannelPipeline}s, and
 * keeping ongoing state.
 * The {@link Junction} creates the {@link Channel} connecting to the target.
 * Some behaviors are symmetrical for the two pipelines (like adding lag) so we use "ingress"
 * and "egress" to distinguish them.
 */
public class Junction {

  private final Logger logger ;
  private final TcpTransitTools.ForwardingRoute forwardingRoute ;
  private final TcpTransitServer.Watcher watcher ;

  private final ImmutableMap<TcpTransitServer.Edge, Channel > originChannelMap ;

  private final ImmutableMap<TcpTransitServer.Edge, Collection<TcpTransitServer.Transfer.Delayed< ? >> >
  destinationDelayedTransferMap = ImmutableCollectionTools.fullImmutableEnumMap(
          TcpTransitServer.Edge.class, Ø -> new ArrayList<>( 100 ) ) ;


  public Junction(
      final Logger logger,
      final TcpTransitTools.ForwardingRoute forwardingRoute,
      final Channel initiatorChannel,
      final Channel targetChannel,
      final TcpTransitServer.Watcher watcher
  ) {
    this.logger = checkNotNull( logger ) ;
    this.forwardingRoute = checkNotNull( forwardingRoute ) ;
    this.watcher = checkNotNull( watcher ) ;

    originChannelMap = ImmutableCollectionTools.fullImmutableEnumMap( ImmutableMap.of(
        HttpProxy.Edge.INITIATOR, initiatorChannel,
        HttpProxy.Edge.TARGET, targetChannel
    ) ) ;

  }

  private volatile int lagMs = 0 ;

  public void lag( final int lagMs ) {
    checkArgument( lagMs >= 0 ) ;
    this.lagMs = lagMs ;
  }


// =====================
// Pipeline configurator
// =====================

  public void configure(
      final TcpTransitServer.Edge edge,
      final TcpTransitServer.PipelineConfigurator pipelineConfigurator
  ) {
    pipelineConfigurator.configure(
        // forwardingRoute.targetAdress(), // Good for PortForwarder.
        forwardingRoute.listenAdress(),
        edge,
        originChannelMap.get( edge ).pipeline()
    ) ;
  }


// =======
// Logging
// =======


  @Override
  public String toString() {
    return
        ToStringTools.nameAndCompactHash( this ) + "{" + forwardingRoute.asString() + "}" ;
  }

// ========
// Transfer
// ========

  private static void forwardNow(
      final TcpTransitServer.Transfer.IngressExit transferAtIngressExit,
      final ChannelHandlerContext destinationChannelHandlerContext
  ) {
    transferAtIngressExit.writeAndFlushTo( destinationChannelHandlerContext ) ;
  }
  /**
   * Must execute within proper {@code Executor}.
   */
  private void forwardWithDelay(
      final TcpTransitServer.Transfer.IngressExit transferAtIngressExit,
      final ChannelHandlerContext destinationChannelHandlerContext,
      final Integer delayMs,
      final Collection<TcpTransitServer.Transfer.Delayed< ? >> collector
  ) {

    /** Since {@link TcpTransitServer.Transfer.Delayed}'s constructor needs a {@link ScheduledFuture},
     * and {@link ScheduledFuture} needs a {@link TcpTransitServer.Transfer.Delayed} we solve this
     * by creating another {@link TcpTransitServer.Transfer.Delayed} without the not-yet-available
     * {@link ScheduledFuture} that we don't need in tasks' body anyway.
     * Another solution was to pass the {@link TcpTransitServer.Transfer.Delayed} using a {@link Future}
     * but we should not use blocking primitives in Netty's event loop. */
    final TcpTransitServer.Transfer.Delayed< ? > delayed1 = new TcpTransitServer.Transfer.Delayed<>(
        TcpTransitTools.NULL_SCHEDULED_FUTURE, transferAtIngressExit, delayMs ) ;

    final ScheduledFuture< ? > scheduledFuture =
        destinationChannelHandlerContext.executor().schedule(
            () -> delayed1.writeAndFlushTo( destinationChannelHandlerContext ),
            delayMs,
            TimeUnit.MILLISECONDS
        )
    ;

    final TcpTransitServer.Transfer.Delayed< ? > delayed0 = new TcpTransitServer.Transfer.Delayed<>(
        scheduledFuture, transferAtIngressExit, delayMs ) ;
    collector.add( delayed0 ) ;
    watcher.onTransfer( delayed0 ) ;
  }


  private static final long TASK_SAFETY_INTERVAL_MS = 10 ;


  private void runFor( final TcpTransitServer.Edge edge, final Runnable runnable ) {
    originChannelMap.get( edge ).eventLoop().execute( runnable ) ;
  }


// =============
// Channel stuff
// =============


  public void disconnect() {
    for( final TcpTransitServer.Edge edge : HttpProxy.Edge.values() ) {
      runFor( edge, () -> {
        final Channel channel = originChannelMap.get( edge ) ;
        if( channel != null ) {
          channel.close() ;
          logger.debug( "Closed " + channel + "." ) ;
        }
      } ) ;
    }
  }

  /**
   * Call this only after {@link #lag(int)} so we are sure to
   * "start" in expected configuration.
   */
  public void allowNextReads() {
    for( final TcpTransitServer.Edge edge : HttpProxy.Edge.values() ) {
      TcpTransitTools.allowNextRead( originChannelMap.get( edge ) ) ;
    }
  }

// ===========================
// Channel configuration stuff
// ===========================

  private void ingressEntry( final TcpTransitServer.Transfer.IngressEntry ingressEntry ) {
    watcher.onTransfer( ingressEntry ) ;
  }

  private void ingressExit( final TcpTransitServer.Transfer.IngressExit transferAtIngressExit ) {
//    logger.debug( "Reached Ingress exit: " + transferAtIngressExit + "." ) ;
    final int currentLag = this.lagMs ;
    final Collection<TcpTransitServer.Transfer.Delayed< ? >> pendingTasks =
        destinationDelayedTransferMap.get( transferAtIngressExit.origin() ) ;

    final Channel destinationChannel = originChannelMap.get(
        transferAtIngressExit.origin().other() ) ;

    /** Writing in {@link ChannelPipeline} starts with last {@link ChannelHandlerContext}. */
    final ChannelHandlerContext destinationChannelHandlerContext =
        destinationChannel.pipeline().lastContext() ;

    /** {@link Channel} closing may cause a null value to appear. */
    final EventExecutor executor = destinationChannelHandlerContext ==
        null ? null : destinationChannelHandlerContext.executor() ;

    if( executor == null ) {
      final Channel originChannel = originChannelMap.get( transferAtIngressExit.origin() ) ;
      originChannel.close() ;
    } else {
      executor.execute( () -> {
        pendingTasks.removeIf( Future::isDone ) ;
        if( currentLag == 0 && pendingTasks.isEmpty() ) {
            forwardNow( transferAtIngressExit, destinationChannelHandlerContext ) ;
        } else {

          /** Do everything in destination {@link Channel} since we access to
           * {@link #destinationDelayedTransferMap}. */
          destinationChannel.eventLoop().execute( () -> {
            /** Guarantee we schedule nothing prior last scheduled {@link TcpTransitServer.Transfer}. */
            final int delay = Math.toIntExact( Math.max(
                currentLag,
                TcpTransitTools.greatestDelayMs( pendingTasks ) + TASK_SAFETY_INTERVAL_MS
            ) ) ;

            forwardWithDelay(
                transferAtIngressExit, destinationChannelHandlerContext, delay, pendingTasks ) ;
          } ) ;
        }
      } ) ;
    }
    TcpTransitTools.allowNextRead( originChannelMap.get( transferAtIngressExit.origin() ) ) ;

  }

  public Junction addIngressConfiguration(
      final ChannelPipeline channelPipeline,
      final TcpTransitServer.Edge onset,
      final boolean bytesInDetail
  ) {
    channelPipeline.addFirst( INGRESS_ENTRY.handlerName(), new ChannelInboundHandlerAdapter() {
      @Override
      public void channelRead(
          final ChannelHandlerContext channelHandlerContext,
          final Object message
      ) throws Exception {
        final TcpTransitServer.Transfer.IngressEntry transferAtIngressEntry = new TcpTransitServer.Transfer.IngressEntry(
            forwardingRoute, onset, bytesInDetail, message ) ;
        ingressEntry( transferAtIngressEntry ) ;
        super.channelRead( channelHandlerContext, message ) ;
      }
    } ) ;

    channelPipeline.addLast( INGRESS_EXIT.handlerName(), new ChannelInboundHandlerAdapter() {
      @Override
      public void channelRead(
          final ChannelHandlerContext channelHandlerContext,
          final Object message
      ) throws Exception {
        final TcpTransitServer.Transfer.IngressExit transferAtIngressExit = new TcpTransitServer.Transfer.IngressExit(
            forwardingRoute, onset, bytesInDetail, message ) ;
        ingressExit( transferAtIngressExit ) ;
      }
    } ) ;

    return this ;
  }

  private void egressExit(
      final ChannelHandlerContext channelHandlerContext,
      final TcpTransitServer.Transfer.EgressExit transferAtEgressExit
  ) {
    transferAtEgressExit.writeAndFlushTo( channelHandlerContext )
        .addListener( ( ChannelFutureListener ) Ø -> watcher.onTransfer( transferAtEgressExit ) ) ;
  }


  public Junction addEgressConfiguration(
      final ChannelPipeline channelPipeline,
      final TcpTransitServer.Edge onset,
      final boolean bytesInDetail
  ) {
    channelPipeline.addFirst( EGRESS_EXIT.handlerName(), new ChannelOutboundHandlerAdapter() {
      @Override
      public void write(
          final ChannelHandlerContext channelHandlerContext,
          final Object message,
          final ChannelPromise promise
      ) throws Exception {
        final TcpTransitServer.Transfer.EgressExit transferAtEgressExit = new TcpTransitServer.Transfer.EgressExit(
            forwardingRoute, onset.other(), bytesInDetail, message ) ;
        egressExit( channelHandlerContext, transferAtEgressExit ) ;
      }
    } ) ;

    return this ;
  }

}
