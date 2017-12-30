package com.otcdlink.chiron.toolbox.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;

public class NettySocketClient extends EventLoopGroupOwner implements InputOutputLifecycled {

  private static final Logger LOGGER = LoggerFactory.getLogger( NettySocketClient.class ) ;

  public NettySocketClient() {
    this( EventLoopGroupFactory::defaultFactory, null ) ;
  }

  protected NettySocketClient(
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactoryResolver,
      final EventLoopGroup eventLoopGroup
  ) {
    super( eventLoopGroupFactoryResolver, eventLoopGroup ) ;
  }

  private Bootstrap bootstrap = null ;

  public final ChannelFuture connect(
      final InetSocketAddress remoteAddress,
      final ChannelInitializer< Channel > channelInitializer
  ) {
    checkState( state == State.STARTED, "Not started." ) ;
    final Bootstrap bootstrap = new Bootstrap() ;
    bootstrap.group( eventLoopGroup() )
        .channel( NioSocketChannel.class )
        .handler( channelInitializer )
    ;
    return bootstrap.connect( remoteAddress ) ;
  }


  @Override
  public final CompletableFuture< Void > start() {
    synchronized( lock ) {
      checkState( bootstrap == null ) ;
      if( state == State.STOPPED ) {
        state = State.STARTED ;
        LOGGER.info( "Started " + this + "." ) ;
      } else {
        LOGGER.warn( "Already in state " + state + "." ) ;
      }
    }
    return COMPLETED_FUTURE ;
  }

  @Override
  public final CompletableFuture< Void > stop() {
    synchronized( lock ) {
      if( state == State.STARTED ) {
        bootstrap = null ;
        state = State.STOPPED ;
        LOGGER.info( "Stopped " + this + "." ) ;
        return regenerateEventLoopIfNeeded() ;
      } else {
        LOGGER.warn( "Can't stop while in state " + state + "." ) ;
        return COMPLETED_FUTURE ;
      }
    }
  }

  public boolean isStarted() {
    return state() == State.STARTED ;
  }

}
