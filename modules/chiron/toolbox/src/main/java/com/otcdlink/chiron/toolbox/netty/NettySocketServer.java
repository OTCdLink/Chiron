package com.otcdlink.chiron.toolbox.netty;

import com.otcdlink.chiron.toolbox.internet.HostPort;
import com.otcdlink.chiron.toolbox.internet.LocalAddressTools;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public class NettySocketServer extends EventLoopGroupOwner implements SocketServer {

  private static final Logger LOGGER = LoggerFactory.getLogger( NettySocketServer.class ) ;
  private final InetSocketAddress listenAddress ;

  private ChannelGroup channels = null ;

  public NettySocketServer(
      final InetSocketAddress listenAddress,
      final EventLoopGroup eventLoopGroup
  ) {
    this( listenAddress, null, eventLoopGroup ) ;
  }

  public NettySocketServer( final int port ) {
    this(
        new InetSocketAddress( LocalAddressTools.LOCAL_ADDRESS, port ),
        EventLoopGroupFactory::defaultFactory
    ) ;
  }

  public NettySocketServer(
      final InetSocketAddress listenAddress,
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactorySupplier
  ) {
    this( listenAddress, eventLoopGroupFactorySupplier, null ) ;
  }

  protected NettySocketServer(
      final InetSocketAddress listenAddress,
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactorySupplier,
      final EventLoopGroup eventLoopGroup
  ) {
    super( eventLoopGroupFactorySupplier, eventLoopGroup ) ;
    this.listenAddress = checkNotNull( listenAddress ) ;
  }


  @Override
  public final InetSocketAddress listenAddress() {
    return listenAddress ;
  }

  protected ChannelGroup channelGroup() {
    return channels ;
  }

  @Override
  @SuppressWarnings( "WeakerAccess" )
  protected void augmentToString(
      @SuppressWarnings( "unused" ) final StringBuilder stringBuilder
  ) {
    stringBuilder.append( HostPort.niceHumanReadableString( listenAddress ) ) ;
  }

  @SuppressWarnings( "WeakerAccess, unused" )
  protected void initializeListenerChannel( final Channel serverChannel ) throws Exception { }

  protected void addChildOptions( final ChannelOptionAdder channelOptionAdder ) {}

  public interface ChannelOptionAdder {
    < T > void addOption( ChannelOption< T > option, T value ) ;
  }

  protected void initializeChildChannel( final Channel initiatorChannel ) throws Exception { }

  protected final void applyToAllChannels( final Consumer< Channel > channelConsumer ) {
    checkNotNull( channelConsumer ) ;
    final ChannelGroup currentChannels = this.channels ;
    if( currentChannels != null ) {
      for( final Channel channel : currentChannels ) {
        channelConsumer.accept( channel ) ;
      }
    }
  }

// =========
// Lifecycle
// =========

  /**
   *
   * @return a {@link CompletableFuture} that completes once acceptor {@link Channel} is open
   *     and ready, or which is already completed if {@link NettySocketServer} was already
   *     {@link State#STARTED}.
   */
  @Override
  public CompletableFuture< Void > start() {
    final CompletableFuture< Void > completableFuture = new CompletableFuture<>() ;
    eventLoopGroup().execute( () -> {
      synchronized( lock ) {
        if( state == State.STOPPED ) {
          state = State.STARTING ;
          channels = new DefaultChannelGroup( eventLoopGroup().next() ) ;
          final ServerBootstrap bootstrap = new ServerBootstrap() ;
          bootstrap.group( eventLoopGroup() )
              .channel( NioServerSocketChannel.class )
              .handler( new ChannelInitializer< Channel >() {
                @Override
                protected void initChannel( final Channel listenerChannel ) throws Exception {
                  channels.add( listenerChannel ) ;
                  NettySocketServer.this.initializeListenerChannel( listenerChannel ) ;
                }
              } )
              .childHandler( new ChannelInitializer< Channel >() {
                @Override
                protected void initChannel( final Channel initiatorChannel ) throws Exception {
                  channels.add( initiatorChannel ) ;
                  NettySocketServer.this.initializeChildChannel( initiatorChannel ) ;
                }
              } )
          ;
          addChildOptions( bootstrap::childOption ) ;
          final ChannelFuture channelFuture = bootstrap.bind( listenAddress ) ;

          channelFuture.addListener( ( ChannelFutureListener ) future -> {
            synchronized( lock ) {
              if( future.isSuccess() ) {
                state = State.STARTED ;
                LOGGER.info( "Started " + this + "." ) ;
                completableFuture.complete( null ) ;
              } else {
                LOGGER.error( "Could not start " + this + ". " , future.cause() ) ;
                state = State.STOPPED ;
                completableFuture.completeExceptionally( future.cause() ) ;
              }
            }
          } ) ;
        } else {
          LOGGER.warn( "Already in state " + state + "." ) ;
          completableFuture.complete( null ) ;
        }
      }

    } ) ;

    return completableFuture ;
  }


  /**
   *
   * @return a {@link CompletableFuture} that completes after every {@link Channel} is closed.
   *     If the {@link EventLoopGroup} was internally created (because of a non-null
   *     {@link #eventLoopGroupFactory}) then completion happens only after graceful shutdown of
   *     {@link #eventLoopGroup} which implies completing every pending task.
   */
  @Override
  public final CompletableFuture< Void > stop() {

    final CompletableFuture< Void > closeAllFuture = new CompletableFuture<>() ;

    synchronized( lock ) {
      if( state == State.STARTED ) {
        state = State.STOPPING ;

        LOGGER.info( "Preparing to stop " + this + " ..." ) ;
        customPreStop() ;

        final ChannelGroupFuture channelGroupFuture = channels.flush().close() ;
        channels = null ;

        channelGroupFuture.addListener( future -> {
          synchronized( lock ) {
            if( future.cause() != null ) {
              LOGGER.error( "Problem while stopping " + this + ".", future.cause() ) ;
            }
            state = State.STOPPED ;
            final CompletableFuture< ? > regenerationFuture = regenerateEventLoopIfNeeded() ;
            regenerationFuture.whenCompleteAsync( ( Ø, t ) -> {
              if( t == null ) {
                closeAllFuture.complete( null ) ;
              } else {
                closeAllFuture.completeExceptionally( t ) ;
              }
              LOGGER.debug(
                  "Fully stopped " + this + ( t == null ? "" : " (with error: " + t + ")" ) + ".",
                  future.cause()
              ) ;
            }, ForkJoinPool.commonPool() ) ;
          }
        } ) ;
      } else {
        LOGGER.warn( "Already in state " + state + "." ) ;
        return CompletableFuture.completedFuture( null ) ;
      }
    }

    /**
     * We should not use a dedicated {@code Executor} here because:
     * - The code triggering the stop is rather short.
     * - The callback after closing the {@link ChannelGroup} happens in {@link #eventLoopGroup()}.
     * - The callback after calling {@link #regenerateEventLoopIfNeeded()} must not happen in
     *   the already-closed {@link #eventLoopGroup()}.
     */
    return closeAllFuture ;

  }

  /**
   * Called at the beginning of {@link #stop()}, with ownership of {@link #lock} and
   * when all {@link Channel}s are still available.
   */
  protected void customPreStop() { }

  @Override
  public void close() throws IOException {
    stop().join() ;  // Tests really need complete stop.
  }


}
