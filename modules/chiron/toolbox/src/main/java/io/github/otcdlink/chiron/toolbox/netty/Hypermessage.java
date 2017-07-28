package io.github.otcdlink.chiron.toolbox.netty;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.github.otcdlink.chiron.toolbox.internet.Hostname;
import io.github.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * HTTP requests and responses as immutable objects, with a limited set of features.
 */
public final class Hypermessage {

  private Hypermessage() { }

  public static final Charset CONTENT_ENCODING = Charsets.UTF_8 ;

  public static abstract class Request {

    /**
     * The {@link HostPort} to connect to, may differ of what appears in {@link #uri}.
     */
    public final SchemeHostPort schemeHostPort ;

    /**
     * The {@code URI} as we'll send it, it may lack scheme and authority.
     */
    public final URI uri ;

    public final ImmutableMultimap< String, String > headers ;

    protected Request( final URL url ) {
      this( url, null ) ;
    }

    protected Request( final URL url, final ImmutableMultimap< String, String > headers ) {
      this(
          SchemeHostPort.create( url.getProtocol(), url.getHost(), url.getPort() ),
          UrxTools.toUriQuiet( url ),
          headers
      ) ;
    }

    /**
     * An HTTP header with key {@link HttpHeaderNames#HOST} and this value (with
     * referential equality, e.g. {@code ==}) will be replaced by a value computed
     * from {@link #schemeHostPort}.
     */
    @SuppressWarnings( "WeakerAccess" )
    public static final String MAGIC_HEADER_HOST = "!!!magic host header!!!" ;

    /**
     * @param headers {@code null} triggers automatic header creation.
     *     A non-null value is kept as it is, with the exception of an entry with a unique key
     *     matching {@link HttpHeaderNames#HOST} and a value referentially equal ({@code ==})
     *     to {@link #MAGIC_HEADER_HOST} that triggers computation of the value from
     *     {@code schemeHostPort}.
     */
    protected Request(
        final SchemeHostPort schemeHostPort,
        final URI uri,
        final ImmutableMultimap< String, String > headers
    ) {
      this.schemeHostPort = checkNotNull( schemeHostPort ) ;
      this.uri = checkNotNull( uri ) ;

      if( headers == null ) {
        this.headers = createDefaultHeaderMap( schemeHostPort.hostPort ) ;
      } else {
        final ImmutableCollection< String > hostHeaders =
            headers.get( HttpHeaderNames.HOST.toString() ) ;
        if( hostHeaders.size() == 1 && isMagicHeaderHost( hostHeaders.iterator().next() ) ) {
          final Multimap< String, String > multimap = ArrayListMultimap.create( headers ) ;
          setHeaderHost( multimap, schemeHostPort.hostPort ) ;
          this.headers = ImmutableMultimap.copyOf( multimap ) ;
        } else {
          this.headers = headers ;
        }
      }
    }

    @SuppressWarnings( "StringEquality" )
    private static boolean isMagicHeaderHost( String headerValue ) {
      return headerValue == MAGIC_HEADER_HOST ;
    }

    private ImmutableMultimap< String, String > createDefaultHeaderMap(
        final HostPort hostPort
    ) {
      final Multimap< String, String > multimap = ArrayListMultimap.create() ;
      setHeaderHost( multimap, hostPort ) ;
      return ImmutableMultimap.copyOf( multimap ) ;
    }

    private static void setHeaderHost(
        final Multimap< String, String > multimap,
        final HostPort hostPort
    ) {
      multimap.removeAll( HttpHeaderNames.HOST.toString() ) ;
      multimap.put( HttpHeaderNames.HOST.toString(), hostPort.asString() ) ;
    }

    public abstract HttpMethod httpMethod() ;

    @Override
    public String toString() {
      final StringBuilder stringBuilder = new StringBuilder() ;
      stringBuilder
          .append( ToStringTools.getNiceClassName( this ) )
          .append( '{' )
          .append( schemeHostPort.uriString() )
          .append( ';' )
          .append( "uri=" )
          .append( uri.toASCIIString() )
      ;

      stringBuilder.append( ";headers=" ) ;
      appendMultimap( stringBuilder, headers ) ;

      extendTostring( stringBuilder ) ;
      stringBuilder.append( '}' ) ;
      return stringBuilder.toString() ;
    }

    @SuppressWarnings( { "unused", "WeakerAccess" } )
    protected void extendTostring( final StringBuilder stringBuilder ) { }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }

      final Request that = ( Request ) other ;

      if( ! uri.equals( that.uri ) ) {
        return false ;
      }
      if( ! headers.equals( that.headers ) ) {
        return false ;
      }
      return schemeHostPort.equals( that.schemeHostPort ) ;
    }

    @Override
    public int hashCode() {
      int result = uri.hashCode() ;
      result = 31 * result + schemeHostPort.hashCode() ;
      return result ;
    }

    public FullHttpRequest fullHttpRequest( final ByteBufAllocator allocator ) {
      return newHttpRequest( httpMethod() ) ;
    }

    public FullHttpRequest fullHttpRequest() {
      return fullHttpRequest( ByteBufAllocator.DEFAULT ) ;
    }


    protected final FullHttpRequest newHttpRequest( final HttpMethod httpMethod ) {
      final FullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(
          HttpVersion.HTTP_1_1, httpMethod, uri.toASCIIString() ) ;
      for( final Map.Entry< String, String > headerEntry : headers.entries() ) {
        fullHttpRequest.headers().add( headerEntry.getKey(), headerEntry.getValue() ) ;
      }
      return fullHttpRequest ;
    }

    public static class Get extends Request {
      public Get( final URL url ) {
        super( url ) ;
      }

      public Get( final URL url, final ImmutableMultimap< String, String > headers ) {
        super( url, headers ) ;
      }

      public Get( final SchemeHostPort schemeHostPort, final URI uri ) {
        this( schemeHostPort, uri, null ) ;
      }

      public Get(
          final SchemeHostPort schemeHostPort,
          final URI uri,
          final ImmutableMultimap< String, String > headers
      ) {
        super( schemeHostPort, uri, headers ) ;
      }

      @Override
      public HttpMethod httpMethod() {
        return HttpMethod.GET ;
      }
    }

    public static class Head extends Request {

      protected Head( final URL url ) {
        super( url ) ;
      }

      protected Head( final URL url, final ImmutableMultimap< String, String > headers ) {
        super( url, headers ) ;
      }

      public Head(
          final SchemeHostPort schemeHostPort,
          final URI uri,
          final ImmutableMultimap< String, String > headers
      ) {
        super( schemeHostPort, uri, headers ) ;
      }

      @Override
      public HttpMethod httpMethod() {
        return HttpMethod.HEAD ;
      }
    }

    public static class Post extends Request {

      public final ImmutableMultimap< String, String > formParameters ;

      public Post(
          final URL url,
          final ImmutableMultimap< String, String > headers,
          final ImmutableMultimap< String, String > formParameters
      ) {
        super( url, headers ) ;
        this.formParameters = checkNotNull( formParameters ) ;
      }

      public Post(
          final URL url,
          final ImmutableMultimap< String, String > formParameters
      ) {
        super( url ) ;
        this.formParameters = checkNotNull( formParameters ) ;
      }

      public Post(
          final SchemeHostPort schemeHostPort,
          final URI uri,
          final ImmutableMultimap< String, String > formParameters
      ) {
        this( schemeHostPort, uri, null, formParameters ) ;
      }

      public Post(
          final SchemeHostPort schemeHostPort,
          final URI uri,
          final ImmutableMultimap< String, String > headers,
          final ImmutableMultimap< String, String > formParameters
      ) {
        super( schemeHostPort, uri, headers ) ;
        this.formParameters = checkNotNull( formParameters ) ;
      }

      @Override
      protected void extendTostring( final StringBuilder stringBuilder ) {
        stringBuilder.append( ";form=" ) ;
        appendMultimap( stringBuilder, this.formParameters ) ;
      }

      @Override
      public boolean equals( final Object other ) {
        if( this == other ) {
          return true ;
        }
        if( other == null || getClass() != other.getClass() ) {
          return false ;
        }
        if( ! super.equals( other ) ) {
          return false ;
        }

        final Post post = ( Post ) other ;

        return formParameters.equals( post.formParameters ) ;
      }

      @Override
      public int hashCode() {
        int result = super.hashCode() ;
        result = 31 * result + formParameters.hashCode() ;
        return result ;
      }

      @Override
      public HttpMethod httpMethod() {
        return HttpMethod.POST ;
      }

      @Override
      public FullHttpRequest fullHttpRequest( final ByteBufAllocator allocator ) {
        final FullHttpRequest bareHttpRequest = super.fullHttpRequest( allocator ) ;
        bareHttpRequest.headers().set(
            HttpHeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded" ) ;

        if( formParameters.isEmpty() ) {
          return bareHttpRequest ;
        } else {
          final HttpPostRequestEncoder httpPostRequestEncoder = createEncoder( bareHttpRequest ) ;
          try {
            for( final Map.Entry< String, String > formEntry : formParameters.entries() ) {
              httpPostRequestEncoder.addBodyAttribute( formEntry.getKey(), formEntry.getValue() ) ;
            }
            final FullHttpRequest httpRequestWithParameters = ( FullHttpRequest )
                httpPostRequestEncoder.finalizeRequest() ;
            return httpRequestWithParameters ;
          } catch( final HttpPostRequestEncoder.ErrorDataEncoderException e ) {
            throw new RuntimeException( "Problem witn encoding", e ) ;
          }
        }
      }
    }

    private static HttpPostRequestEncoder createEncoder( final HttpRequest httpRequest ) {
      try {
        return new HttpPostRequestEncoder(
            new DefaultHttpDataFactory(),
            httpRequest,
            false,
            CONTENT_ENCODING,
            HttpPostRequestEncoder.EncoderMode.RFC1738
        ) ;
      } catch( final HttpPostRequestEncoder.ErrorDataEncoderException e ) {
        throw new RuntimeException( "Should not happen", e ) ;
      }
    }

    /**
     * @param scheme needed because it's optional in URI.
     */
    public static Request from(
        final SchemeHostPort.Scheme scheme,
        final FullHttpRequest fullHttpRequest
    ) throws URISyntaxException, HostPort.ParseException {
      final URI uri = new URI( fullHttpRequest.uri() ) ;
      final ImmutableMultimap< String, String > headers ;
      {
        final ImmutableMultimap.Builder< String, String > builder = ImmutableMultimap.builder() ;
        fullHttpRequest.headers().forEach( builder::put ) ;
        headers = builder.build() ;
      }

      final SchemeHostPort schemeHostPort ;
      {
        final HostPort hostPort ;
        final String hostMaybePort ;
        hostMaybePort = fullHttpRequest.headers().get( HttpHeaderNames.HOST ) ;
        if( hostMaybePort.indexOf( ':' ) > -1 ) {
          hostPort = HostPort.parse( hostMaybePort ) ;
        } else {
          final Hostname hostname = Hostname.parse( hostMaybePort ) ;
          hostPort = HostPort.create( hostname, scheme.defaultPort ) ;
        }
        schemeHostPort = SchemeHostPort.create( scheme, hostPort ) ;
      }

      final HttpMethod method = fullHttpRequest.method() ;

      if( HttpMethod.GET.equals( method ) ) {
        return new Get( schemeHostPort, uri, headers ) ;
      } else if( HttpMethod.HEAD.equals( method ) ) {
        return new Head( schemeHostPort, uri, headers ) ;
      } else if( HttpMethod.POST.equals( method ) ) {
        final ImmutableMultimap.Builder< String, String > builder = ImmutableMultimap.builder() ;
        NettyTools.collectFormParameters( fullHttpRequest, builder::put ) ;
        final ImmutableMultimap< String, String > formParameterMap = builder.build() ;
        return new Post( schemeHostPort, uri, headers, formParameterMap ) ;
      } else {
        throw new IllegalArgumentException( "Unsupported: " + method ) ;
      }
    }
  }

  private static void appendMultimap(
      final StringBuilder stringBuilder,
      final ImmutableMultimap< String, String > parameterMap
  ) {
    stringBuilder.append( '[' ) ;
    final String keyValues = Joiner.on( ',' ).withKeyValueSeparator( "=" )
        .join( parameterMap.entries() );
    stringBuilder.append( keyValues ) ;
    stringBuilder.append( ']' ) ;
  }

  public static final class Response {
    public final HttpResponseStatus responseStatus ;

    public final ImmutableMultimap< String, String > headers ;

    public final String contentAsString ;

    public Response(
        final HttpResponseStatus responseStatus,
        final ImmutableMultimap< String, String > headers,
        final String contentAsString
    ) {
      this.responseStatus = checkNotNull( responseStatus ) ;
      this.headers = checkNotNull( headers ) ;
      this.contentAsString = checkNotNull( contentAsString ) ;
    }

    @Override
    public String toString() {
      final StringBuilder stringBuilder = new StringBuilder() ;
      stringBuilder
          .append( ToStringTools.getNiceClassName( this ) )
          .append( '{' )
          .append( responseStatus.toString() )
      ;
      if( ! headers.isEmpty() ) {
        stringBuilder.append( ';' ) ;
        stringBuilder.append( "headers=" ) ;
        appendMultimap( stringBuilder, headers ) ;
      }
      stringBuilder.append( '}' ) ;
      return stringBuilder.toString() ;
    }

    public FullHttpResponse fullHttpResponse( final ByteBufAllocator byteBufAllocator ) {
      final ByteBuf content = byteBufAllocator.buffer() ;
      final FullHttpResponse fullHttpRequest = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1, responseStatus, content ) ;
      for( final Map.Entry< String, String > entry : headers.entries() ) {
        fullHttpRequest.headers().set( entry.getKey(), entry.getValue() ) ;
      }
      content.writeBytes( contentAsString.getBytes( CONTENT_ENCODING ) ) ;
      return fullHttpRequest ;
    }

    public static Response from( final FullHttpResponse fullHttpResponse ) {
      final String contentAsString = fullHttpResponse.content().toString( CONTENT_ENCODING ) ;
      final HttpResponseStatus responseStatus = fullHttpResponse.status() ;
      final ImmutableMultimap.Builder< String, String > headerMapBuilder =
          ImmutableMultimap.builder() ;
      fullHttpResponse.headers().forEach(
          entry -> headerMapBuilder.put( entry.getKey(), entry.getValue() ) ) ;
      return new Response( responseStatus, headerMapBuilder.build(), contentAsString ) ;
    }

  }
}
