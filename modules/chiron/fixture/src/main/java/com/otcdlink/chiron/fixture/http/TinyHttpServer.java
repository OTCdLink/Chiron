package com.otcdlink.chiron.fixture.http;

import com.otcdlink.chiron.toolbox.internet.HostPort;
import com.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import com.otcdlink.chiron.toolbox.netty.Hypermessage;
import com.otcdlink.chiron.toolbox.netty.NettySocketServer;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.netty.channel.ChannelFutureListener.CLOSE;

public class TinyHttpServer extends NettySocketServer {

  private static final Logger LOGGER = LoggerFactory.getLogger( TinyHttpServer.class ) ;

  private final SslEngineFactory.ForServer sslEngineFactory ;
  private final Responder responder ;

  public TinyHttpServer(
      final EventLoopGroup eventLoopGroup,
      final int port,
      final Responder responder
  ) {
    this( eventLoopGroup, null, port, responder ) ;
  }

  public TinyHttpServer(
      final EventLoopGroup eventLoopGroup,
      final SslEngineFactory.ForServer sslEngineFactory,
      final int port,
      final Responder responder
  ) {
    this( null, eventLoopGroup, sslEngineFactory, port, responder ) ;
  }

  private TinyHttpServer(
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactorySupplier,
      final EventLoopGroup eventLoopGroup,
      final SslEngineFactory.ForServer sslEngineFactory,
      final int port,
      final Responder responder
  ) {
    super(
        HostPort.createForLocalhost( port ).asInetSocketAddressQuiet(),
        eventLoopGroupFactorySupplier,
        eventLoopGroup
    ) ;
    this.sslEngineFactory = sslEngineFactory ;
    this.responder = checkNotNull( responder ) ;
  }

  public TinyHttpServer( final int port, final Responder responder ) {
    this( EventLoopGroupFactory::defaultFactory, null, null, port, responder ) ;
  }

  public interface Responder {
    /**
     * @return {@code null} if something went wrong; then {@link TinyHttpServer} should
     *     send some meaningful error code.
     */
    Hypermessage.Response respondTo( Hypermessage.Request request ) ;
  }

  @Override
  protected void initializeChildChannel( final Channel initiatorChannel ) throws Exception {
    if( sslEngineFactory != null ) {
      initiatorChannel.pipeline().addFirst( new SslHandler( sslEngineFactory.newSslEngine() ) ) ;
    }
    initiatorChannel.pipeline().addLast( new HttpServerCodec() ) ;
    initiatorChannel.pipeline().addLast( new HttpObjectAggregator( 4096 ) ) ;
    initiatorChannel.pipeline().addLast( new ResponderTier() ) ;
  }


  private class ResponderTier extends ChannelDuplexHandler {
    @Override
    public void channelRead(
        final ChannelHandlerContext channelHandlerContext,
        final Object message
    ) throws Exception {
      if( message instanceof FullHttpRequest ) {
        final Hypermessage.Request request =
            Hypermessage.Request.from( SchemeHostPort.Scheme.HTTP, ( FullHttpRequest ) message ) ;
        final Hypermessage.Response response = responder.respondTo( request ) ;
        if( response == null ) {
          LOGGER.error(
              "Could not obtain a response for " + request + " from " + responder + ", closing " +
              channelHandlerContext + " which should trigger an error on the client side."
          ) ;
          channelHandlerContext.close() ;
        } else {
          final ByteBufAllocator allocator = channelHandlerContext.alloc() ;
          channelHandlerContext.writeAndFlush(
              response.fullHttpResponse( allocator ) ).addListener( CLOSE ) ;
        }
      } else {
        super.channelRead( channelHandlerContext, message ) ;
      }
    }
  }
}
