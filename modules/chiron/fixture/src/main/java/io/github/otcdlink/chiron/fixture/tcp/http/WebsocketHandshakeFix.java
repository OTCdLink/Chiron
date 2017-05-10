package io.github.otcdlink.chiron.fixture.tcp.http;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.toolbox.ImmutableCollectionTools;
import io.github.otcdlink.chiron.toolbox.ObjectTools;
import io.github.otcdlink.chiron.toolbox.collection.ConstantShelf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Kept for sentimental reasons.
 *
 * <h1>Original purpose</h1>
 * The original purpose was to rewrites HTTP headers for correct WebSocket handshake,
 * so Wireshark doesn't get fooled.
 *
 * <h1>Why it can't work with an HTTP proxy</h1>
 * The HTTP proxy receives HTTP requests which already contain the target host.
 * Those shouldn't be modified, and Wireshark already saw them and got fooled.
 *
 * <h1>Future</h1>
 * This class could be easily turned into a generic HTTP header rewriter.
 */
@SuppressWarnings( "unused" )
public class WebsocketHandshakeFix implements HttpProxy.PipelineConfigurator {

  /**
   * A value of {@value} should be enough for a WebSocket upgrade.
   */
  private static final int MAX_CONTENT_LENGTH = 1024 * 1024 ;

  public static final class ChannelHandlerFixName extends ConstantShelf {

    private static ChannelHandlerFixName createNew() {
      return new ChannelHandlerFixName() ;
    }

    public static final ChannelHandlerFixName HTTP_REQUEST_DECODER = createNew() ;
    public static final ChannelHandlerFixName HTTP_REQUEST_ENCODER = createNew() ;
    public static final ChannelHandlerFixName HTTP_RESPONSE_DECODER = createNew() ;
    public static final ChannelHandlerFixName HTTP_RESPONSE_ENCODER = createNew() ;
    public static final ChannelHandlerFixName HTTP_AGGREGATOR = createNew() ;
    public static final ChannelHandlerFixName HEADER_REWRITER = createNew() ;
    public static final ChannelHandlerFixName CLEANER = createNew() ;

    public static final Map< String, ChannelHandlerFixName > MAP =
        valueMap( ChannelHandlerFixName.class ) ;

    public final String handlerName() {
      return WebsocketHandshakeFix.class.getSimpleName().toLowerCase() + "-" +
          CaseFormat.UPPER_UNDERSCORE.to( CaseFormat.LOWER_HYPHEN, name() ) ;
    }

  }

  /**
   * Implementation of {@link HttpProxy.PipelineConfigurator.Factory}.
   */
  public static WebsocketHandshakeFix createNew( final Logger logger ) {
    return new WebsocketHandshakeFix( logger ) ;
  }

  private final Logger logger ;

  private final ImmutableMap<HttpProxy.Edge, ObjectTools.Holder< ChannelPipeline > >
  pipelineMap = ImmutableCollectionTools.fullImmutableEnumMap(
      HttpProxy.Edge.class, Ø -> ObjectTools.newHolder() ) ;

  public WebsocketHandshakeFix( final Logger logger ) {
    this.logger = checkNotNull( logger ) ;
  }


  @Override
  public void configure(
      final InetSocketAddress newAddress,
      final HttpProxy.Edge edge,
      final ChannelPipeline channelPipeline
  ) {
    pipelineMap.get( edge ).set( channelPipeline ) ;

    final HeaderRewriterChannelHandler headerRewriterChannelHandler =
        new HeaderRewriterChannelHandler(
            ImmutableMap.of(
                HttpHeaderNames.HOST.toString(), Ø -> hostPortAsString( newAddress ),
                HttpHeaderNames.SEC_WEBSOCKET_ORIGIN.toString(), value ->
                    ( value != null && value.startsWith( "https://" ) ? "https://" : "http://" ) +
                        hostPortAsString( newAddress )
            ),
            null
        )
    ;

    switch( edge ) {

      case INITIATOR :
        channelPipeline.addLast( ChannelHandlerFixName.HTTP_REQUEST_DECODER.handlerName(),
            new HttpRequestDecoder() ) ;
        channelPipeline.addLast( ChannelHandlerFixName.HTTP_RESPONSE_ENCODER.handlerName(),
            new HttpResponseEncoder() ) ;
        channelPipeline.addLast( ChannelHandlerFixName.HTTP_AGGREGATOR.handlerName(),
            new HttpObjectAggregator( MAX_CONTENT_LENGTH ) ) ;

        channelPipeline.addLast( ChannelHandlerFixName.HEADER_REWRITER.handlerName(),
            headerRewriterChannelHandler ) ;
        channelPipeline.addLast( ChannelHandlerFixName.CLEANER.handlerName(),
            new CleanupChannelHandler() ) ;
        break ;

      case TARGET :
        channelPipeline.addLast( ChannelHandlerFixName.HTTP_RESPONSE_DECODER.handlerName(),
            new HttpResponseDecoder() ) ;
        channelPipeline.addLast( ChannelHandlerFixName.HTTP_REQUEST_ENCODER.handlerName(),
            new HttpRequestEncoder() ) ;
        channelPipeline.addLast( ChannelHandlerFixName.HTTP_AGGREGATOR.handlerName(),
            new HttpObjectAggregator( MAX_CONTENT_LENGTH ) ) ;
        break ;
      default :
        throw new IllegalArgumentException( "Unsupported: " + edge ) ;
    }

  }

  private void cleanup() {
    for( final Map.Entry<HttpProxy.Edge, ObjectTools.Holder< ChannelPipeline > > entry :
        pipelineMap.entrySet()
    ) {
      final ChannelPipeline channelPipeline = entry.getValue().get() ;
      channelPipeline.channel().eventLoop().execute( () -> {
        for( final ChannelHandlerFixName name : ChannelHandlerFixName.MAP.values() ) {
          final ChannelHandler channelHandler = channelPipeline.get( name.handlerName() ) ;
          if( channelHandler != null ) {
            channelPipeline.remove( channelHandler ) ;
          }
        }
        logger.debug( "Cleaned up " + channelPipeline ) ;
      } ) ;
    }
  }

  private static String hostPortAsString( final InetSocketAddress inetSocketAddress ) {
    return inetSocketAddress.getHostString() + ":" + inetSocketAddress.getPort() ;
  }

  private static class HeaderRewriterChannelHandler extends ChannelDuplexHandler {
    private final ImmutableMap< String, Function< String, String > > inboundRewriteMap ;
    private final ImmutableMap< String, Function< String, String > > outboundRewriteMap ;

    public HeaderRewriterChannelHandler(
        final ImmutableMap< String, Function< String, String > > inboundRewriteMap,
        final ImmutableMap< String, Function< String, String > > outboundRewriteMap
    ) {
      this.inboundRewriteMap = inboundRewriteMap ;
      this.outboundRewriteMap = outboundRewriteMap ;
    }

    @Override
    public void channelRead(
        final ChannelHandlerContext channelHandlerContext,
        final Object message
    ) throws Exception {
      mutateHttpHeaders( message, inboundRewriteMap ) ;
      channelHandlerContext.fireChannelRead( message ) ;
    }

    @Override
    public void write(
        final ChannelHandlerContext channelHandlerContext,
        final Object message,
        final ChannelPromise promise
    ) throws Exception {
      mutateHttpHeaders( message, outboundRewriteMap ) ;
      channelHandlerContext.write( message, promise ) ;
    }

    private static void mutateHttpHeaders(
        final Object message,
        final ImmutableMap< String, Function< String, String > > outboundRewriteMap
    ) {
      if( outboundRewriteMap != null ) {
        if( message instanceof HttpRequest ) {
          final HttpRequest httpRequest = ( HttpRequest ) message ;
          for( final Map.Entry< String, Function< String, String > > entry :
              outboundRewriteMap.entrySet()
          ) {
            final String key = entry.getKey() ;
            final String old = httpRequest.headers().get( key ) ;
            final String newValue = entry.getValue().apply( old ) ;
            httpRequest.headers().set( key, newValue ) ;
          }
        }
      }
    }
  }

  private class CleanupChannelHandler extends ChannelDuplexHandler {

    @Override
    public void write(
        final ChannelHandlerContext channelHandlerContext,
        final Object message,
        final ChannelPromise promise
    ) throws Exception {
      if( message instanceof HttpResponse ) {
        final HttpResponse httpResponse = ( HttpResponse ) message ;
        if( httpResponse.status().equals( HttpResponseStatus.SWITCHING_PROTOCOLS ) ) {
          cleanup() ;
        }
      }
      super.write( channelHandlerContext, message, promise ) ;
    }
  }

}
