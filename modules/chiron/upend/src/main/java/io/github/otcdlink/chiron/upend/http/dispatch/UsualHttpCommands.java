package io.github.otcdlink.chiron.upend.http.dispatch;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.github.otcdlink.chiron.toolbox.text.Plural;
import io.github.otcdlink.chiron.upend.http.caching.BytebufContent;
import io.github.otcdlink.chiron.upend.http.caching.StaticContent;
import io.github.otcdlink.chiron.upend.http.caching.StaticContentCache;
import io.github.otcdlink.chiron.upend.http.caching.StaticContentResolver;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;

public final class UsualHttpCommands {

  private UsualHttpCommands() { }

  public static class AbstractAnymlResponse implements PipelineFeeder {

    public final String content;
    public final HttpResponseStatus responseStatus ;
    public final String mimeType ;

    public AbstractAnymlResponse(
        final HttpResponseStatus responseStatus,
        final String mimeType,
        final String content
    ) {
      this.responseStatus = checkNotNull( responseStatus ) ;
      this.mimeType = checkNotNull( mimeType ) ;
      this.content = checkNotNull( content ) ;
    }

    @Override
    public final void feed( final ChannelHandlerContext channelHandlerContext ) {
      final FullHttpResponse fullHttpResponse = newHttpResponse( channelHandlerContext.alloc() ) ;
      final FullHttpResponse httpResponse = fullHttpResponse ;
      httpResponse.headers().set( HttpHeaderNames.CONTENT_TYPE, "text/html" ) ;
      httpResponse.retain() ;
      writeAndFlushAndClose( channelHandlerContext, httpResponse ) ;
    }

    public FullHttpResponse newHttpResponse( final ByteBufAllocator byteBufAllocator ) {
      return UsualHttpCommands.newHttpResponse( byteBufAllocator, responseStatus, content ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" +
          "status=" + responseStatus + ";" +
          "content=" + content +
      "}" ;
    }
  }

  public static class NotFound extends AbstractAnymlResponse {

    public NotFound( final String uri ) {
      super( NOT_FOUND, "text/html", "Not found: '" + uri + "'" ) ;
    }

    public static HttpResponder.Outbound outbound() {
      return ( evaluationContext, httpRequest ) -> new NotFound( httpRequest.uri() ) ;
    }

  }

  public static class Forbidden extends AbstractAnymlResponse {

    public Forbidden( final String uri ) {
      super( FORBIDDEN, "text/html", "Forbidden: '" + uri + "'" ) ;
    }

    public static HttpResponder.Outbound outbound() {
      return ( evaluationContext, httpRequest ) -> new Forbidden( httpRequest.uri() ) ;
    }


  }

  public static class ServerError extends AbstractAnymlResponse {
    public ServerError( final String message ) {
      super( INTERNAL_SERVER_ERROR, "text/html", message ) ;
    }
  }

  public static class BadRequest extends AbstractAnymlResponse {
    public BadRequest( final String message ) {
      super( BAD_REQUEST, "text/html", message ) ;
    }
  }

  public static class ServiceUnavailable extends AbstractAnymlResponse {
    public ServiceUnavailable( final String message ) {
      super( SERVICE_UNAVAILABLE, "text/html", message ) ;
    }
    public static HttpResponder.Outbound outbound( final String message ) {
      return ( evaluationContext, httpRequest ) -> new ServiceUnavailable( message ) ;
    }
  }

  public static class Xml extends AbstractAnymlResponse {

    public Xml( final String xmlContent ) {
      this( OK, xmlContent ) ;
    }

    public Xml( final HttpResponseStatus httpResponseStatus, final String xmlContent ) {
      super( httpResponseStatus, "application/xml", xmlContent ) ;
    }

    public static HttpResponder.Outbound outbound( final String htmlBody ) {
      return ( evaluationContext, httpRequest ) -> new Xml( htmlBody ) ;
    }

    @Override
    public FullHttpResponse newHttpResponse( final ByteBufAllocator byteBufAllocator ) {
      return super.newHttpResponse( byteBufAllocator ) ;
    }
  }

  public static class Html extends AbstractAnymlResponse {

    public Html( final String htmlBody ) {
      this( OK, htmlBody ) ;
    }

    public Html( final HttpResponseStatus httpResponseStatus, final String htmlBody ) {
      super( httpResponseStatus, "text/html", "<html><body>" + htmlBody + "</body></html>" ) ;
    }

    public static HttpResponder.Outbound outbound( final String htmlBody ) {
      return ( evaluationContext, httpRequest ) -> new Html( htmlBody ) ;
    }

    @Override
    public FullHttpResponse newHttpResponse( final ByteBufAllocator byteBufAllocator ) {
      return super.newHttpResponse( byteBufAllocator ) ;
    }
  }


  public static class Redirect implements PipelineFeeder {

    /**
     * Non-private for tests.
     */
    final String redirectionTargetUri ;

    public Redirect( final String redirectionTargetUri ) {
      this.redirectionTargetUri = checkNotNull( redirectionTargetUri ) ;
    }

    @Override
    public void feed( final ChannelHandlerContext channelHandlerContext ) {
      final FullHttpResponse httpResponse =
          httpResponse( channelHandlerContext.alloc(), redirectionTargetUri ) ;
      writeAndFlushAndClose( channelHandlerContext, httpResponse ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" + redirectionTargetUri + "}" ;
    }

    public static FullHttpResponse httpResponse(
        final ByteBufAllocator byteBufAllocator,
        final String redirectionTargetUri
    ) {
      final FullHttpResponse fullHttpResponse = newHttpResponse(
          byteBufAllocator,
          HttpResponseStatus.FOUND,
          "Redirecting to: " + redirectionTargetUri,
          HttpHeaderNames.LOCATION.toString(),
          redirectionTargetUri
      ) ;
      return fullHttpResponse ;
    }

    public static HttpResponder.Outbound outbound( final String redirectionTargetUri ) {
      return ( evaluationContext, httpRequest ) ->
          new Redirect( redirectionTargetUri )
      ;
    }

    public static final HttpResponder.Outbound APPEND_TRAILING_SLASH_IF_CONTEXT_PATH_MATCHES =
        new HttpResponder.Outbound() {
          @Override
          public PipelineFeeder outbound(
              final EvaluationContext evaluationContext,
              final RichHttpRequest httpRequest
          ) {
            final UriPath.MatchKind match =
                evaluationContext.contextPath().pathMatch( httpRequest.uriPath ) ;
            if( match == UriPath.MatchKind.TOTAL_MATCH ) {
              final StringBuilder redirectionUriBuilder = new StringBuilder() ;
              if( httpRequest.uriScheme != null ) {
                redirectionUriBuilder.append( httpRequest.uriScheme ).append( "://" ) ;
              }
              if( httpRequest.requestedHost != null ) {
                redirectionUriBuilder.append( httpRequest.requestedHost ) ;
              }
              if( ! httpRequest.usesDefaultPort ) {
                redirectionUriBuilder.append( ':' ).append( httpRequest.requestedPort ) ;
              }
              redirectionUriBuilder.append( httpRequest.uriPath ).append( '/' ) ;
              final String redirectionUri = redirectionUriBuilder.toString() ;

              return new Redirect( redirectionUri ) ;
            }
            return null ;
          }

          @Override
          public String toString() {
            return ToStringTools.nameAndCompactHash( this ) + "#APPEND_TRAILING_SLASH{}" ;
          }
        }
    ;
  }

  /**
   * Serves resources obtained from {@link StaticContentResolver} then {@link StaticContentCache}
   * that caches binary blobs.
   * Resources are held as {@link ByteSource}s because it's more convenient than an URL.
   */
  public static class JustBytebuf implements PipelineFeeder {
    private final BytebufContent staticContentAsByteBuf ;

    /**
     * Only for reporting errors, resource path already known by {@link #staticContentAsByteBuf}.
     */
    private final String absoluteUriPath ;

    public JustBytebuf(
        final BytebufContent staticContentAsByteBuf,
        final String absoluteUriPath
    ) {
      this.staticContentAsByteBuf = staticContentAsByteBuf ;
      this.absoluteUriPath = checkNotNull( absoluteUriPath ) ;
    }

    @Override
    public void feed( final ChannelHandlerContext channelHandlerContext ) {
      if( staticContentAsByteBuf == null ) {
        new NotFound( absoluteUriPath ).feed( channelHandlerContext ) ;
      } else {
        final FullHttpResponse httpResponse = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, OK, staticContentAsByteBuf.bytebuf() ) ;
        httpResponse.headers().set(
            HttpHeaderNames.CONTENT_TYPE, staticContentAsByteBuf.mimeType ) ;
        NettyTools.noCache( httpResponse ) ;  // One day we'll be less brutal.
        httpResponse.retain() ;
        channelHandlerContext.writeAndFlush( httpResponse ).addListener( CLOSE ) ;
      }
    }

  }


  /**
   * Serves resources obtained from a plain {@code File} from local filesystem.
   * The purpose of this class is to avoid caching, using Netty streaming instead.
   */
  public static final class JustFile implements PipelineFeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger( JustFile.class ) ;
    private final StaticContent.FromFile staticContentFromFile ;

    /**
     * Only for error-reporting, file path already given by {@link #staticContentFromFile}.
     */
    private final String requestUri ;

    private final boolean justHead ;
    private final boolean keepAlive ;
    private final boolean tls ;

    public JustFile(
        final StaticContent.FromFile staticContentFromFile,
        final RichHttpRequest httpRequest
    ) {
      this(
          staticContentFromFile,
          httpRequest.uriPath,
          HttpMethod.HEAD.equals( httpRequest.method() ),
          HttpUtil.isKeepAlive( httpRequest ),
          httpRequest.channel().pipeline().get( SslHandler.class ) != null
      ) ;
    }

    /**
     *
     * @param staticContentFromFile may be {@code null} because sometimes we don't know
     *     in advance if the file exists. If it does not exist, requesting it will cause
     *     a {@link NotFound}.
     */
    public JustFile(
        final StaticContent.FromFile staticContentFromFile,
        final String requestUri,
        final boolean justHead,
        final boolean keepAlive,
        final boolean tls
    ) {
      this.staticContentFromFile = staticContentFromFile ;
      this.requestUri = checkNotNull( requestUri ) ;
      this.justHead = justHead ;
      this.keepAlive = keepAlive ;
      this.tls = tls ;
    }

    @Override
    public void feed( final ChannelHandlerContext channelHandlerContext ) {
      if( staticContentFromFile == null ) {
        new NotFound( requestUri ).feed( channelHandlerContext ) ;
        return ;
      }

      final RandomAccessFile randomAccessFile ;
      try {
        randomAccessFile = new RandomAccessFile( staticContentFromFile.file, "r" ) ;
      } catch( final FileNotFoundException e ) {
        new NotFound( requestUri ).feed( channelHandlerContext ) ;
        return ;
      }
      final long fileLength = staticContentFromFile.file.length() ;
      LOGGER.debug( "Ready to send '" + staticContentFromFile.file.getAbsolutePath() + "' " +
          "(" + Plural.bytes( fileLength ) + ") to " + channelHandlerContext + "." ) ;

      final DefaultHttpResponse httpResponse = new DefaultHttpResponse(
          HttpVersion.HTTP_1_1, OK ) ;
      HttpUtil.setContentLength( httpResponse, fileLength ) ;
      httpResponse.headers().set(
          HttpHeaderNames.CONTENT_TYPE, staticContentFromFile.mimeType ) ;
      NettyTools.noCache( httpResponse ) ;

      if( keepAlive ) {
        httpResponse.headers().set( HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE ) ;
      }

      channelHandlerContext.write( httpResponse ) ;

      final ChannelFuture lastContentFuture ;
      if( justHead ) {
        lastContentFuture = channelHandlerContext.pipeline().writeAndFlush(
            LastHttpContent.EMPTY_LAST_CONTENT ) ;
      } else {

        final ChannelFuture sendFileFuture ;

        if( tls ) {
          final ChunkedFile chunkedFile ;
          try {
            chunkedFile = new ChunkedFile( randomAccessFile, 0, fileLength, 8192 ) ;
          } catch( final IOException e ) {
            LOGGER.error( "Could not load '" +
                staticContentFromFile.file.getAbsolutePath() + "'", e ) ;
            writeAndFlushAndClose(
                channelHandlerContext,
                new ServerError( "Could not load " + requestUri )
                    .newHttpResponse( channelHandlerContext.alloc() )
            ) ;
            return ;
          }
          lastContentFuture = sendFileFuture = channelHandlerContext.pipeline().writeAndFlush(
              new HttpChunkedInput( chunkedFile ),
              channelHandlerContext.newProgressivePromise()
          ) ;
          /** {@link HttpChunkedInput} will write the end marker ({@link LastHttpContent}) for us. */
        } else {
          sendFileFuture = channelHandlerContext.pipeline().write(
              new DefaultFileRegion( randomAccessFile.getChannel(), 0, fileLength ),
              channelHandlerContext.newProgressivePromise()
          ) ;
          lastContentFuture = channelHandlerContext.pipeline().writeAndFlush(
              LastHttpContent.EMPTY_LAST_CONTENT ) ;
        }


        sendFileFuture.addListener( new ChannelProgressiveFutureListener() {
          @Override
          public void operationProgressed(
              final ChannelProgressiveFuture future,
              final long progress,
              final long total
          ) {
//            if( total < 0 ) { // total unknown
//              LOGGER.debug( "Transfer progress: " + progress + " for " + future.channel() + "." ) ;
//            } else {
//              LOGGER.debug( "Transfer progress: " + progress + " / " + total + " " +
//                  "for " + future.channel() + "." ) ;
//            }
          }

          @Override
          public void operationComplete( final ChannelProgressiveFuture future ) {
            LOGGER.debug( "Transfer complete for " + future.channel() + "." ) ;
          }
        } ) ;
      }

      if( ! keepAlive ) {
        lastContentFuture.addListener( ChannelFutureListener.CLOSE ) ;
      }
    }

    public static HttpResponder.Outbound outbound(
        final StaticContent.FromFile staticContentFromFile
    ) {
      return ( evaluationContext, httpRequest ) -> new JustFile(
          staticContentFromFile,
          httpRequest
      ) ;
    }

  }


// ===============
// Utility methods
// ===============


  public static FullHttpResponse newHttpResponse(
      final ByteBufAllocator byteBufAllocator,
      final HttpResponseStatus httpResponseStatus,
      final String htmlBody,
      final String... headers
  ) {
    final String responseText = htmlBody ;
    final FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        httpResponseStatus,
        byteBufAllocator.buffer().writeBytes( responseText.getBytes( Charsets.UTF_8 ) )
    ) ;

    if( headers.length > 0 ) {
      checkArgument( headers.length % 2 == 0 ) ;
      for( int i = 0 ; i < headers.length ; ) {
        fullHttpResponse.headers().set( headers[ i ++ ], headers[ i ++ ] ) ;
      }
    }
    return fullHttpResponse ;
  }

  public static void writeAndFlushAndClose(
      final ChannelHandlerContext channelHandlerContext,
      final FullHttpResponse httpResponse
  ) {
    httpResponse.retain() ;
    channelHandlerContext
        .writeAndFlush( httpResponse )
        .addListener( ChannelFutureListener.CLOSE )
    ;
  }
}
