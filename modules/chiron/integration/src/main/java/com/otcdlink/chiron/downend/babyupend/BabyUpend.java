package com.otcdlink.chiron.downend.babyupend;


import com.google.common.base.StandardSystemProperty;
import com.otcdlink.chiron.conductor.ConductorTools;
import com.otcdlink.chiron.conductor.Responder;
import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.toolbox.internet.LocalAddressTools;
import com.otcdlink.chiron.toolbox.netty.NettySocketServer;
import com.otcdlink.chiron.toolbox.security.DefaultSslEngineFactory;
import com.otcdlink.chiron.toolbox.security.KeystoreAccess;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A minimalistic HTTP server with WebSocket support for tests.
 */
public class BabyUpend extends NettySocketServer {

  private final SslEngineFactory.ForServer sslEngineFactory ;
  public static final String WEBSOCKET_PATH = "/websocket" ;
  private final ConnectionDescriptor connectionDescriptor ;

  private final Responder< TextWebSocketFrame, TextWebSocketFrame > textFrameResponder ;
  private final Responder< PingWebSocketFrame, PongWebSocketFrame > pingResponder;
  private final Responder< CloseWebSocketFrame, Void > closeFrameResponder ;


  public BabyUpend( final int port ) {
    this( LocalAddressTools.LOCAL_ADDRESS, port, null ) ;
  }

  public BabyUpend(
      final InetAddress listenAddress,
      final int port,
      final SslEngineFactory.ForServer sslEngineFactory
  ) {
    this(
        listenAddress,
        port,
        sslEngineFactory,
        DEFAULT_CONNECTION_DESCRIPTOR,
        null,
        null,
        null
    ) ;
  }

  public BabyUpend(
      final int port,
      final ConnectionDescriptor connectionDescriptor,
      final Responder< TextWebSocketFrame, TextWebSocketFrame > textFrameResponder,
      final Responder< PingWebSocketFrame, PongWebSocketFrame > pingResponder,
      final Responder< CloseWebSocketFrame, Void > closeFrameResponder
  ) {
    this(
        LocalAddressTools.LOCAL_ADDRESS,
        port,
        null,
        connectionDescriptor,
        textFrameResponder,
        pingResponder,
        closeFrameResponder
    ) ;
  }

  public BabyUpend(
      final int port,
      final SslEngineFactory.ForServer sslEngineFactory,
      final ConnectionDescriptor connectionDescriptor,
      final Responder< TextWebSocketFrame, TextWebSocketFrame > textFrameResponder,
      final Responder< PingWebSocketFrame, PongWebSocketFrame > pingResponder,
      final Responder< CloseWebSocketFrame, Void > closeFrameResponder
  ) {
    this(
        LocalAddressTools.LOCAL_ADDRESS,
        port,
        sslEngineFactory,
        connectionDescriptor,
        textFrameResponder,
        pingResponder,
        closeFrameResponder
    ) ;
  }

  public BabyUpend(
      final InetAddress listenAddress,
      final int port,
      final SslEngineFactory.ForServer sslEngineFactory,
      final ConnectionDescriptor connectionDescriptor,
      final Responder< TextWebSocketFrame, TextWebSocketFrame > textFrameResponder,
      final Responder< PingWebSocketFrame, PongWebSocketFrame > pingResponder,
      final Responder< CloseWebSocketFrame, Void > closeFrameResponder
  ) {
    super( new InetSocketAddress( listenAddress, port ), EventLoopGroupFactory::defaultFactory ) ;
    this.sslEngineFactory = sslEngineFactory ;
    this.connectionDescriptor = checkNotNull( connectionDescriptor ) ;
    this.textFrameResponder = textFrameResponder == null ?
        ConductorTools.AutomaticWebsocketframeResponder.uppercase() : textFrameResponder ;
    this.pingResponder = pingResponder == null ?
        ConductorTools.AutomaticPingwebsocketframeResponder.justPong() : pingResponder ;
    this.closeFrameResponder = closeFrameResponder == null ?
        ConductorTools.AutomaticClosewebsocketframeResponder.doNothing() : closeFrameResponder
    ;

  }

  @Override
  protected void initializeChildChannel( final Channel initiatorChannel ) throws Exception {
    if( sslEngineFactory != null ) {
      initiatorChannel.pipeline().addLast( new SslHandler( sslEngineFactory.newSslEngine() ) ) ;
    }
    initiatorChannel.pipeline().addLast( new HttpServerCodec() ) ;
    initiatorChannel.pipeline().addLast( new HttpObjectAggregator( 65536 ) ) ;
    initiatorChannel.pipeline().addLast( new BabyTier(
        sslEngineFactory != null,
        connectionDescriptor,
        channelGroup(),
        textFrameResponder,
        pingResponder,
        closeFrameResponder
    ) ) ;
  }



// ====================
// More live operations
// ====================

  public void sendToAllNow( final WebSocketFrame websocketframe ) {
    channelGroup().writeAndFlush( websocketframe ) ;
  }

// ==============
// Standalone run
// ==============


  public static void main( final String... arguments ) throws Exception {
    System.out.println( "Running from '" + StandardSystemProperty.USER_DIR + "'." ) ;
    final BabyUpend babyUpend ;
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
        throw new IllegalArgumentException( BabyUpend.class.getSimpleName() +
            " [ <port> | < <listenAddress> <port> [keystoreAccess] > ]" ) ;
    }
    babyUpend = new BabyUpend( listenAddress, port, sslEngineFactory ) ;
    babyUpend.start() ;

//    Uninterruptibles.sleepUninterruptibly( 1, TimeUnit.SECONDS ) ;
//    babyUpend.stop() ;
//    Uninterruptibles.sleepUninterruptibly( 100, TimeUnit.MILLISECONDS ) ; // Log flush.
  }

  public static final ConnectionDescriptor DEFAULT_CONNECTION_DESCRIPTOR =
      new ConnectionDescriptor( "NoVersion", false, TimeBoundary.DEFAULT ) ;

}
