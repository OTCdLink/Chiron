package com.otcdlink.chiron.integration.drill.fakeend;


import com.google.common.base.StandardSystemProperty;
import com.google.common.util.concurrent.Uninterruptibles;
import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.toolbox.internet.LocalAddressTools;
import com.otcdlink.chiron.toolbox.netty.NettySocketServer;
import com.otcdlink.chiron.toolbox.security.DefaultSslEngineFactory;
import com.otcdlink.chiron.toolbox.security.KeystoreAccess;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An HTTP server that blocks until asked to send responses.
 *
 */
public class FakeUpend extends NettySocketServer {

  private static final Logger LOGGER = LoggerFactory.getLogger( FakeUpend.class ) ;

  private final SslEngineFactory.ForServer sslEngineFactory ;
  public static final String WEBSOCKET_PATH = "/websocket" ;
  private final ConnectionDescriptor connectionDescriptor ;
  public UpendFullDuplexPack upendDuplexPack ;
  private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler ;

  /**
   * The unique {@link Channel} (the one passed to {@link #initializeChildChannel(Channel)}).
   * The value gets nullified when current {@link Channel} gets closed,
   * or when another {@link Channel} gets created (so next attempt to write will blow).
   *
   * TODO some mechanism to earmark registered {@link Channel}s so we can reference them.
   * We could do this with the help of the port number, in absence of a network hop.
   */
  private final AtomicReference< Channel > uniqueInitiatorChannel = new AtomicReference<>( null ) ;

  public FakeUpend(
      final InetAddress listenAddress,
      final int port,
      final SslEngineFactory.ForServer sslEngineFactory,
      final ConnectionDescriptor connectionDescriptor,
      final Thread.UncaughtExceptionHandler uncaughtExceptionHandler
  ) {
    super( new InetSocketAddress( listenAddress, port ), EventLoopGroupFactory::defaultFactory ) ;
    this.sslEngineFactory = sslEngineFactory ;
    this.connectionDescriptor = checkNotNull( connectionDescriptor ) ;
    this.uncaughtExceptionHandler = checkNotNull( uncaughtExceptionHandler ) ;
    newUpendDuplexPack() ;
  }

  private void newUpendDuplexPack() {
    this.upendDuplexPack = new UpendFullDuplexPack( this::writeToUniqueChannel ) ;
  }

  @Override
  protected void initializeChildChannel( final Channel initiatorChannel ) {
    if( sslEngineFactory != null ) {
      initiatorChannel.pipeline().addLast( new SslHandler( sslEngineFactory.newSslEngine() ) ) ;
    }
    initiatorChannel.pipeline().addLast( new HttpServerCodec() ) ;
    initiatorChannel.pipeline().addLast( new HttpObjectAggregator( 65536 ) ) ;
    initiatorChannel.pipeline().addLast( new FakeUpendTier(
        uncaughtExceptionHandler,
        sslEngineFactory != null,
        connectionDescriptor,
        channelGroup(),
        upendDuplexPack
    ) ) ;
    initiatorChannel.closeFuture().addListener( future -> uniqueInitiatorChannel.set( null ) ) ;
    uniqueInitiatorChannel.updateAndGet( c -> c == null ? initiatorChannel : null ) ;
  }

  private ChannelFuture writeToUniqueChannel( final Object message ) {
    return uniqueInitiatorChannel.get().writeAndFlush( message ) ;
  }

  @Override
  protected void customPreStop() {
    upendDuplexPack.shutdown() ;
    newUpendDuplexPack() ;
  }

  // ==============
// Standalone run
// ==============


  public static void main( final String... arguments ) throws Exception {
    System.out.println( "Running from '" + StandardSystemProperty.USER_DIR + "'." ) ;
    final FakeUpend fakeUpend ;
    InetAddress listenAddress = LocalAddressTools.LOCAL_ADDRESS ;
    int port  = 8080 ;
    SslEngineFactory.ForServer sslEngineFactory = null ;

    switch( arguments.length ) {
      case 0 :
        break ;
      case 3 :
        sslEngineFactory = new DefaultSslEngineFactory.ForServer(
            KeystoreAccess.parse( arguments[ 2 ] ) ) ;
      case 2 :
        listenAddress = InetAddress.getByName( arguments[ 0 ] ) ;
        port = Integer.parseInt( arguments[ 1 ] ) ;
        break ;
      case 1 :
        port = Integer.parseInt( arguments[ 0 ] ) ;
        break ;
      default :
        throw new IllegalArgumentException( FakeUpend.class.getSimpleName() +
            " [ <port> | < <listenAddress> <port> [keystoreAccess] > ]" ) ;
    }
    fakeUpend = new FakeUpend(
        listenAddress,
        port,
        sslEngineFactory,
        DEFAULT_CONNECTION_DESCRIPTOR,
        ( t, e ) -> LOGGER.error( "Caught: ", e )
    ) ;
    fakeUpend.start() ;

    Uninterruptibles.sleepUninterruptibly( 20, TimeUnit.SECONDS ) ;
    fakeUpend.stop().join() ;
    Uninterruptibles.sleepUninterruptibly( 100, TimeUnit.MILLISECONDS ) ; // Log flush.
  }

  private static final ConnectionDescriptor DEFAULT_CONNECTION_DESCRIPTOR =
      new ConnectionDescriptor( "NoVersion", false, TimeBoundary.DEFAULT ) ;

}
