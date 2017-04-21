package io.github.otcdlink.chiron.toolbox.netty;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import io.github.otcdlink.chiron.toolbox.ObjectTools;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class NettyTools {

  public static final Channel NULL_CHANNEL = ObjectTools.nullObject( Channel.class ) ;

  public static final ChannelPipeline NULL_CHANNEL_PIPELINE =
      ObjectTools.nullObject( ChannelPipeline.class ) ;

  public static final FullHttpRequest NULL_FULL_HTTP_REQUEST =
      ObjectTools.nullObject( FullHttpRequest.class ) ;

  /**
   * TODO: make {@link RichHttpRequest} immutable.
   * TODO: change the horror below into something that doesn't look like an atomic bomb.
   */
  public static final RichHttpRequest NULL_FULLHTTPREQUEST =
      RichHttpRequest.from(
          "/",
          NULL_CHANNEL,
          InetSocketAddress.createUnresolved( "0.0.0.0", 0 ),
          InetSocketAddress.createUnresolved( "0.0.0.0", 0 ),
          false
      )
  ;
  public static final Function<Channel, InetAddress> CHANNEL_REMOTE_ADDRESS_EXTRACTOR =
      c -> ( ( InetSocketAddress ) c.remoteAddress() ).getAddress() ;

  private NettyTools() { }

  private static final Logger LOGGER = LoggerFactory.getLogger( NettyTools.class ) ;

  public static void forceNettyClassesToLoad() {
    LOGGER.debug( "Forcing Netty initialization to log its stuff now." ) ;
    new NioEventLoopGroup() ;
    new PooledByteBufAllocator().buffer().release() ;
    NetUtil.intToIpAddress( 0 ) ;
    DefaultChannelId.newInstance() ;
    ByteBufUtil.swapShort( ( short ) 0 ) ;
    try {
      NettyTools.class.getClassLoader().loadClass( "io.netty.buffer.AdvancedLeakAwareByteBuf" ) ;
    } catch( ClassNotFoundException ignore ) { }
    LOGGER.debug( "End of forced Netty initialization." ) ;
  }

  /**
   * http://stackoverflow.com/a/2068407/1923328
   * http://stackoverflow.com/a/18516720/1923328
   */
  public static void noCache( final HttpResponse response ) {
    response.headers().set( HttpHeaderNames.EXPIRES, "0" ) ;
    response.headers().set( HttpHeaderNames.CACHE_CONTROL,
        "no-cache, no-store, max-age=0, must-revalidate, proxy-revalidate, s-" ) ;
    response.headers().set( HttpHeaderNames.PRAGMA, "no-cache" ) ;
    response.headers().set( HttpHeaderNames.VARY, "*" ) ;
  }

  /**
   * Returns {@code true} if {@link Channel#remoteAddress()} is {@code localhost} or something
   * like {@code 127.0.0.1}.
   * http://stackoverflow.com/a/2406819/1923328
   */
  public static boolean remoteAddressIsLocal( final Channel channel ) {
    final InetAddress remoteAddress = ( ( InetSocketAddress )
        channel.remoteAddress() ).getAddress() ;
    return remoteAddress.isAnyLocalAddress() || remoteAddress.isLoopbackAddress() ;
  }

  public static boolean methodIsPost( final HttpRequest httpRequest ) {
    return HttpMethod.POST.equals( httpRequest.method() ) ;
  }

  public static void collectFormParameters(
      final HttpRequest httpRequest,
      final NameValuePairCollector collector
  ) {
    if( ! methodIsPost( httpRequest ) ) {
      throw new IllegalArgumentException( "Cannot parse HttpData for requests other than POST" ) ;
    }

    try {
      final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(
          new DefaultHttpDataFactory( false ), httpRequest ) ;
      try {
        for( final InterfaceHttpData data : decoder.getBodyHttpDatas() ) {
          if( data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute ) {
            final Attribute attribute = ( Attribute ) data ;
            if( collector.collect( attribute.getName(), attribute.getValue() ) == null ) {
              return ;
            }
          }
        }
      } finally {
        decoder.destroy() ;
      }
    } catch( final IOException e ) {
      throw new IllegalStateException( "Cannot parse http request data", e ) ;
    }
  }

  public interface NameValuePairCollector {
    /**
     * @return {@code null} to signal premature termination. This is compatible with
     *     {@link ImmutableMultimap.Builder#put(Map.Entry)} which returns a non-{@code null}
     *     value, meaning it wants to collect every name-value pair.
     */
    Object collect( String name, String value ) ;
  }

  public static ImmutableMultimap< String, String > headers( final HttpMessage httpMessage ) {
    final ImmutableMultimap.Builder< String, String > builder = ImmutableMultimap.builder() ;
    collectHeaders( httpMessage, builder::put ) ;
    return builder.build() ;
  }

  public static void collectHeaders(
      final HttpMessage httpMessage,
      final NettyTools.NameValuePairCollector collector
  ) {
    final List< Map.Entry< String, String > > entries = httpMessage.headers().entries() ;
    for( int i = 0, entriesSize = entries.size() ; i < entriesSize ; i++ ) {
      final Map.Entry< String, String > entry = entries.get( i ) ;
      if( collector.collect( entry.getKey(), entry.getValue() ) == null ) {
        break ;
      }
    }
  }

  private static String capitalize( final CharSequence charSequence ) {
    return "" + Character.toUpperCase( charSequence.charAt( 0 ) ) +
        charSequence.subSequence( 1, charSequence.length() ) ;
  }

  private static String firstHeaderOrNull(
      final ImmutableMultimap< String, String > headers,
      final String name
  ) {
    final ImmutableCollection< String > headerList = headers.get( name ) ;
    if( headerList.isEmpty() ) {
      return null ;
    } else {
      return headerList.iterator().next() ;
    }
  }

  /**
   * Return the first header value found for the given name, or the first header value found
   * for the capitalized name, or null if none of the former succeeded.
   * This is useful for header names returned by {@link com.sun.net.httpserver.HttpServer} which
   * don't respect Netty's (HTTP/2) conventions.
   *
   * @see HttpHeaderNames
   */
  public static String headerWithOptionalCapitalisation(
      final ImmutableMultimap< String, String > headers,
      final String name
  ) {
    String relocation = firstHeaderOrNull( headers, name ) ;
    if( relocation == null ) {
      relocation = firstHeaderOrNull( headers, capitalize( name ) ) ;
    }
    return relocation ;
  }

  public static Map.Entry< String, ChannelHandler > after(
      final ChannelPipeline pipeline,
      final String name
  ) {
    final Iterator< Map.Entry< String, ChannelHandler > > iterator = pipeline.iterator() ;
    boolean found = false ;
    while( iterator.hasNext() ) {
      final Map.Entry< String, ChannelHandler > next = iterator.next() ;
      if( found ) {
        return next ;
      }
      if( name.equals( next.getKey() ) ) {
        found = true ;
      }
    }
    return null ;
  }

}
