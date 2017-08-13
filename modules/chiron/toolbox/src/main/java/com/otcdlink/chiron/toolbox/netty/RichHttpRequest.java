package com.otcdlink.chiron.toolbox.netty;

import com.google.common.base.Strings;
import com.otcdlink.chiron.toolbox.UrxTools;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.ssl.SslHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Decorates a {@link FullHttpRequest} with additional information.
 * Instances of this class guarantee that {@link #uri()} matches
 * {@link UrxTools.Parsing#URI_PATTERN}. This means that instantiators should handle a non-valid
 * URI. Since {@code HttpDispatcher} requires a {@link RichHttpRequest} this check is
 * out of its scope.
 * <p>
 * The use of the "context" word doesn't mean such an {@link HttpRequest} carries something like
 * {@code HttpServlet}'s context path.
 *
 * <h1>Discussion about possible enhancements</h1>
 * <h2>Renaming</h2>
 * <p>
 * We could rename into something better. Candidates (favorites first):
 * - IntegralHttpRequest
 * - RichHttpRequest
 * - JumboHttpRequest
 * - TallyHttpRequest
 * - RefinedHttpRequest
 * - ThickHttpRequest
 * - BulkyHttpRequest
 * - ExtensiveHttpRequest
 * - TotalHttpRequest
 * - OutrightHttpRequest
 * - CompleteHttpRequest
 * - ProperHttpRequest
 * - FatHttpRequest
 * - MassiveHttpRequest
 * - HeavyHttpRequest
 * - ChunkyHttpRequest
 * <p>
 * <h2>Immutability</h2>
 * The {@link #channel} is a mutable things and prevents from making this class
 * fully immutable. We encapsulate the access to the {@link Channel} with some methods
 * but it sounds bad because we probably need plain {@link Channel}s elsewehere.
 * For making this class immutable we can keep the delegation approach (to a
 * {@link FullHttpRequest}) or rewrite the {@link HttpObjectAggregator}.
 */
public final class RichHttpRequest extends DefaultFullHttpRequest {

  private final InetSocketAddress remoteAddress ;

  private final InetSocketAddress localAddress ;

  private final Channel channel ;

  private final QueryStringDecoder queryStringDecoder ;


  /**
   * A pure URI scheme, guessed from TLS usage if it does not appear in the URI passed to the
   * constructor.
   */
  public final String uriScheme ;

  public final String requestedHost ;
  public final int requestedPort ;

  public final boolean usesDefaultPort ;

  /**
   * A pure URI path with no scheme/host/port/query/fragment part.
   * {@link #uri()} offers so such guarantee.
   */
  public final String uriPath ;

  public final String uriQuery ;


  public RichHttpRequest(
      final FullHttpRequest original,
      final ChannelPipeline channelPipeline
  ) {
    this(
        original,
        channelPipeline.channel(),
        ( InetSocketAddress ) channelPipeline.channel().remoteAddress(),
        ( InetSocketAddress ) channelPipeline.channel().localAddress(),
        channelPipeline.get( SslHandler.class ) != null
    ) ;
  }

  /**
   * Constructor performing a shallow copy.
   * This leaves {@link #refCnt()} unchanged since constructor keeps a reference on original's
   * {@link ByteBuf}.
   */
  private RichHttpRequest(
      final FullHttpRequest original,
      final Channel channel,
      final InetSocketAddress remoteAddress,
      final InetSocketAddress localAddress,
      final boolean tls
  ) {
    super(
        original.protocolVersion(),
        original.method(),
        original.uri(),
        original.content(),
        original.headers(),
        original.trailingHeaders()
    ) ;
    this.remoteAddress = checkNotNull( remoteAddress ) ;
    this.localAddress = checkNotNull( localAddress ) ;
    this.channel = checkNotNull( channel ) ;
    final Matcher matcher = UrxTools.Parsing.safeMatcher( original.uri() ) ;

    {
      final String schemeMaybe = UrxTools.Parsing.Part.SCHEME.extract( matcher ) ;
      uriScheme = schemeMaybe == null ? ( tls ? "https" : "http" ) : schemeMaybe ;
    }

    final String uriHost = UrxTools.Parsing.Part.HOST.extract( matcher ) ;
    final String requestHeaderHost = extractHostFromHeader( original ) ;
    if( uriHost != null ) {
      checkArgument(
          uriHost.equals( requestHeaderHost ),
          "Host should be the same within URI ('" + uriHost + "') and " +
          "'Host' request header ('" + requestHeaderHost + "')"
      ) ;
    }

    final String uriPort = UrxTools.Parsing.Part.PORT.extract( matcher ) ;
    final String requestHeaderPort = extractPortFromHeader( original ) ;
    if( uriPort == null ) {
      if( requestHeaderPort == null ) {
        if( uriScheme == null ) {
          requestedPort = localAddress.getPort() ;
        } else {
          switch( uriScheme ) {
            case "http" :
              requestedPort = 80 ;
              break ;
            case "https" :
              requestedPort = 443 ;
              break ;
            default : throw new IllegalArgumentException(
                "Unknown scheme, can't guess port from it: '" + uriScheme + "'" ) ;
          }
        }
        usesDefaultPort = true ;
      } else {
        requestedPort = Integer.parseInt( requestHeaderPort ) ;
        usesDefaultPort = false ;
      }
    } else {
      requestedPort = Integer.parseInt( uriPort ) ;
      usesDefaultPort = false ;
    }

    requestedHost = requestHeaderHost ;
    uriPath = UrxTools.Parsing.Part.PATH.extract( matcher ) ;
    uriQuery = UrxTools.Parsing.Part.QUERY.extract( matcher ) ;

    if( uriQuery == null ) {
      this.queryStringDecoder = null ;
    } else {
      this.queryStringDecoder = new QueryStringDecoder( uriQuery, false ) ;
    }

  }

  private static String extractHostFromHeader( final FullHttpRequest original ) {
    final String requestHeaderHost ;
    final String wholeValue = extractHostPortFromHeader( original ) ;

    final int colonPosition = wholeValue.indexOf( ':' ) ;
    if( colonPosition > 0 ) {
      requestHeaderHost = wholeValue.substring( 0, colonPosition ) ;
    } else {
      requestHeaderHost = wholeValue ;
    }
    return requestHeaderHost ;
  }
  private static String extractPortFromHeader( final FullHttpRequest httpRequest ) {
    final String wholeValue = extractHostPortFromHeader( httpRequest ) ;
    final int colonPosition = wholeValue.indexOf( ':' ) ;
    if( colonPosition > 0 ) {
      return wholeValue.substring( colonPosition + 1 ) ;
    } else {
      return null ;
    }
  }

  private static String extractHostPortFromHeader( final FullHttpRequest original ) {
    final String wholeValue = original.headers().get( HttpHeaderNames.HOST ) ;
    checkArgument(
        ! Strings.isNullOrEmpty( wholeValue ),
        "HTTP specification says 'Host' parameter is mandatory, " +
            "see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.23"
    ) ;
    return wholeValue ;
  }

  /**
   * For tests only.
   */
  public static RichHttpRequest from(
      final String uriPath,
      final Channel channel,
      final InetSocketAddress remoteAddress,
      final InetSocketAddress localAddress,
      final boolean tls
  ) {
    return from( HttpMethod.GET, uriPath, channel, remoteAddress, localAddress, tls ) ;
  }
  /**
   * For tests only.
   */
  public static RichHttpRequest from(
      final HttpMethod httpMethod,
      final String uriPath,
      final Channel channel,
      final InetSocketAddress remoteAddress,
      final InetSocketAddress localAddress,
      final boolean tls
  ) {

    if( ! UrxTools.Parsing.URI_PATTERN.matcher( uriPath ).matches() ) {
      throw new IllegalArgumentException( "Incorrect URL: '" + uriPath + "'" ) ;
    }
    final DefaultFullHttpRequest original = new DefaultFullHttpRequest(
        HttpVersion.HTTP_1_1, httpMethod, uriPath ) ;
    original.headers().set( HttpHeaderNames.HOST, localAddress.getHostString() ) ;
    final RichHttpRequest richHttpRequest = new RichHttpRequest(
        original,
        channel,
        remoteAddress,
        localAddress,
        tls
    ) ;
    return richHttpRequest;

  }

  public InetSocketAddress remoteAddress() {
    return remoteAddress ;
  }

  public InetSocketAddress localAddress() {
    return localAddress ;
  }

  /**
   * May return a {@link ChannelPipeline} that is no longer valid because the
   * {@link RichHttpRequest} outlived it.
   */
  public Channel channel() {
    return channel ;
  }



// =====
// Query
// =====

  private void collectQueryParameters( final NettyTools.NameValuePairCollector collector ) {
    if( queryStringDecoder != null ) {
      for( final Map.Entry< String, List< String > > entry :
          queryStringDecoder.parameters().entrySet()
          ) {
        for( final String listElement : entry.getValue() ) {
          if( collector.collect( entry.getKey(), listElement ) == null ) {
            return ;
          }
        }
      }
    }
  }


// ====
// Form
// ====

  private static boolean methodIsPost( final HttpRequest httpRequest ) {
    return HttpMethod.POST.equals( httpRequest.method() ) ;
  }


  private static void collectFormParameters(
      final HttpRequest httpRequest,
      final NettyTools.NameValuePairCollector collector
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



// ===============
// Headers or Form
// ===============

  public String parameter( final String name ) {
    // We return one single value so we need only one slot.
    final String[] result = { null } ;
    final NettyTools.NameValuePairCollector resultFeeder = ( someName, someValue ) -> {
      if( result[ 0 ] == null && name.equals( someName ) ) {
        result[ 0 ] = someValue ;
        return null ;
      }
      return this ;
    } ;

    // First try to resolve as POST.
    if( methodIsPost( this ) ) {
      collectFormParameters( this, resultFeeder ) ;
      if( result[ 0 ] != null ) {
        return result[ 0 ] ;
      }
    }

    // No parameter found, could be in query string.

    if( queryStringDecoder == null ) {
      return null ;
    } else {
      collectQueryParameters( resultFeeder ) ;
      return result[ 0 ] ;
    }
  }


}
