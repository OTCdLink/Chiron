package com.otcdlink.chiron.toolbox.netty;

import com.google.common.collect.ImmutableMultimap;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.otcdlink.chiron.toolbox.UrxTools.safePort;

/**
 * <i>The</i> HTTP client to use for tests.
 * <pre>
 final Recorder&lt; Request.Get > recorder = new Recorder&lt;>() ;
 final TinyHttpClient httpClient = new TinyHttpClient() ;
 httpClient.start().join() ;
 httpClient.httpGet( "http://localhost:8080/test, recorder ) ;
 final Recorder.CompleteResponse response = recorder.nextResponse() ;
 WatchedResponseAssert.assertThat( response )
     .isComplete()
     .hasStatusCode( HttpResponseStatus.OK )
 ;

 * </pre>
 *
 * @see Hypermessage.Request
 * @see Watcher the callback notifying of request's status, including timeout.
 * @see Recorder#nextOutcome() for waiting until something happens (including timeout).
 *
 */
public class NettyHttpClient extends NettySocketClient {

  private static final Logger LOGGER = LoggerFactory.getLogger( NettyHttpClient.class ) ;

  /**
   * Delay after which we call 
   * {@link Watcher#timeout(Hypermessage.Request)}.
   */
  private final int timeoutMs ;

  private final SslEngineFactory.ForClient sslEngineFactory ;

  public NettyHttpClient( final EventLoopGroup eventLoopGroup, final int timeoutMs ) {
    this( null, checkNotNull( eventLoopGroup ), timeoutMs, null ) ;
  }

  public static NettyHttpClient createStarted() {
    final NettyHttpClient nettyHttpClient = new NettyHttpClient() ;
    nettyHttpClient.start().join() ;
    return nettyHttpClient;
  }

  public NettyHttpClient() {
    this( EventLoopGroupFactory::defaultFactory, null, DEFAULT_TIMEOUT_MS, null ) ;
  }

  public NettyHttpClient(
      final EventLoopGroup eventLoopGroup,
      final int timeoutMs,
      final SslEngineFactory.ForClient sslEngineFactory
  ) {
    this( null, eventLoopGroup, timeoutMs, sslEngineFactory ) ;
  }

  public NettyHttpClient(
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactoryResolver,
      final int timeoutMs,
      final SslEngineFactory.ForClient sslEngineFactory
  ) {
    this( eventLoopGroupFactoryResolver, null, timeoutMs, sslEngineFactory ) ;
  }

  protected NettyHttpClient(
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactorySupplier,
      final EventLoopGroup eventLoopGroup,
      final int timeoutMs,
      final SslEngineFactory.ForClient sslEngineFactory
  ) {
    super( eventLoopGroupFactorySupplier, eventLoopGroup ) ;
    checkArgument( timeoutMs >= 0 ) ;
    this.timeoutMs = timeoutMs ;
    this.sslEngineFactory = sslEngineFactory ;
  }

  public final ScheduledExecutorService scheduledExecutorService() {
    return eventLoopGroup() ;
  }


  private ChannelInitializer< Channel > channelInitializer(
      final SslEngineFactory sslEngineFactory
  ) {
    return new ChannelInitializer< Channel >() {
      @Override
      protected void initChannel( Channel channel ) throws Exception {
        if( sslEngineFactory != null ) {
          channel.pipeline().addFirst( new SslHandler( sslEngineFactory.newSslEngine() ) ) ;
        }
        channel.pipeline().addLast( new HttpResponseDecoder() ) ;
        channel.pipeline().addLast( new HttpRequestEncoder() ) ;
        channel.pipeline().addLast( new HttpObjectAggregator( MAX_CONTENT_LENGTH ) ) ;
        channel.pipeline().addLast( new HttpResponseTier() ) ;
      }
    } ;
  } ;





// =======================
// Callback-based contract
// =======================

  /**
   * Receives notifications from {@link #eventLoopGroup} so its methods should execute quickly.
   */
  public interface Watcher {

    /**
     * This is a very partial modelisation of an HTTP response but it is enough for testing.
     * We don't depend on Netty classes which contain too much mutable stuff for testing.
     */
    void complete(
        Hypermessage.Request request,
        Hypermessage.Response response
    ) ;

    void failed( Hypermessage.Request request, Throwable throwable ) ;

    void timeout( Hypermessage.Request request ) ;

    void cancelled( Hypermessage.Request request ) ;

    static boolean success( final int statusCode ) {
      return HttpResponseStatus.OK.code() == statusCode ;
    }

  }

  public void httpGet(
      final URL url,
      final Watcher watcher
  ) {
    httpRequest( new Hypermessage.Request.Get( url ), watcher ) ;
  }

  public void httpGet(
      final SchemeHostPort schemeHostPort,
      final URI uri,
      final Watcher watcher
  ) {
    httpRequest( new Hypermessage.Request.Get( schemeHostPort, uri ), watcher ) ;
  }

  public void checkStarted() {
    checkState( state() == State.STARTED, "Not started: " + this ) ;
  }

  public void httpPost(
      final URL url,
      final ImmutableMultimap< String, String > formParameters,
      final Watcher watcher
  ) {
    httpRequest( new Hypermessage.Request.Post( url, formParameters ), watcher ) ;
  }


// =======
// Outcome
// =======


  /**
   * Base class for capturing a {@link Watcher}'s method call and store them for asserting
   * later on them.
   */
  public static class Outcome {

    /**
     * The {@link Hypermessage.Request} that caused current {@link Outcome}. This is useful for calculating
     * redirection given a relative URI.
     */
    public final Hypermessage.Request request ;

    public Outcome( final Hypermessage.Request request ) {
      this.request = checkNotNull( request ) ;
    }

    @Override
    public final String toString() {
      return ToStringTools.getNiceClassName( this ) + '{' +
          "request:" + request + ';' +
          tostringBody() +
          '}'
          ;
    }

    protected String tostringBody() {
      return "" ;
    }
  }

  public static class CompleteResponse extends Outcome {
    public final Hypermessage.Response response ;

    public CompleteResponse(
        final Hypermessage.Request request,
        final Hypermessage.Response response
    ) {
      super( request ) ;
      this.response= checkNotNull( response ) ;
    }

    @Override
    protected String tostringBody() {
      final String saneContent ;
      {
        final String nobreak = response.contentAsString
            .replace( ( char ) 10, ' ' )
            .replace( ( char ) 13, ' ' )
            ;
        if( response.contentAsString.length() > 50 ) {
          saneContent = nobreak.substring( 0, 50 ) + "[...]" ;
        } else {
          saneContent = nobreak ;
        }
      }
      return "statusCode:" + response.responseStatus + ';' +
          "contentAsString:'" + saneContent + "'" ;
    }

    /**
     * Calculate redirection with support for relative URL in redirection location.
     */
    public URL redirectionTargetUrl() {
      final String relocation = NettyTools.headerWithOptionalCapitalisation(
          response.headers,
          HttpHeaderNames.LOCATION.toString()
      ) ;
      if( relocation == null ) {
        return null ;
      }
      final URL redirectionUrl ;
      final Matcher matcher = UrxTools.Parsing.safeMatcher( relocation ) ;
      final String redirectionTargetScheme = UrxTools.Parsing.Part.SCHEME.extract( matcher ) ;
      final String redirectionTargetHost = UrxTools.Parsing.Part.HOST.extract( matcher ) ;
      if( redirectionTargetScheme != null && redirectionTargetHost != null ) {
        // Redirection gave an absolute URI with scheme and host name.
        redirectionUrl = UrxTools.parseUrlQuiet( relocation );
      } else {
        final String originalScheme = request.uri.getScheme() ;
        final String originalHost = request.uri.getHost() ;

        final URL originalBaseUrl ;
        final int originalPort = safePort( request.uri ) ;
        final String originalBase = originalScheme + "://" + originalHost + ":" + originalPort ;
        if( relocation.startsWith( "/" ) ) {
          // Relocation is absolute, calculate base URL with no path.
          originalBaseUrl = UrxTools.parseUrlQuiet( originalBase ) ;
        } else {
          // Relocation is relative, calculate base URL with path.
          final String originalPath = request.uri.getPath() ;
          originalBaseUrl = UrxTools.parseUrlQuiet( originalBase + originalPath ) ;
        }
        redirectionUrl = UrxTools.derive( originalBaseUrl, relocation ) ;
      }
      return redirectionUrl ;
    }
    /**
     * Redirection target as it is, may be relative with no scheme-host-port.
     */
    public URI redirectionTargetUri() {
      final String relocation = NettyTools.headerWithOptionalCapitalisation(
          response.headers,
          HttpHeaderNames.LOCATION.toString()
      ) ;
      if( relocation == null ) {
        return null ;
      }
      return UrxTools.parseUriQuiet( relocation ) ;
    }

  }

  public static class ResponseWithThrowable extends Outcome {
    public final Throwable throwable ;

    public ResponseWithThrowable( final Hypermessage.Request request, final Throwable throwable ) {
      super( request ) ;
      this.throwable = throwable ;
    }

    @Override
    protected String tostringBody() {
      return throwable.getClass().getName() ;
    }
  }

  public static class NoResponse extends Outcome {

    public final Recorder.NoResponseCause cause ;

    public NoResponse( final Hypermessage.Request request, final Recorder.NoResponseCause cause ) {
      super( request ) ;
      this.cause = checkNotNull( cause ) ;
    }
  }

  public static class BadOutcomeException extends Exception {
    public BadOutcomeException( final Outcome outcome ) {
      super( "Bad outcome: " + outcome ) ;
    }
  }


// ========
// Recorder
// ========

  public abstract static class OutcomeAdapter implements NettyHttpClient.Watcher {

    protected abstract void outcome( final Outcome outcome ) ;

    @Override
    public void complete(
        final Hypermessage.Request request,
        final Hypermessage.Response response
    ) {
      outcome( new CompleteResponse( request, response ) ) ;
    }

    @Override
    public void failed( final Hypermessage.Request request, final Throwable throwable ) {
      outcome( new ResponseWithThrowable( request, throwable ) ) ;
    }

    @Override
    public void timeout( final Hypermessage.Request request ) {
      outcome( new NoResponse( request, Recorder.NoResponseCause.TIMEOUT ) ) ;
    }

    @Override
    public void cancelled( final Hypermessage.Request request ) {
      outcome( new NoResponse( request, Recorder.NoResponseCause.CANCELLED ) ) ;
    }

  }

  public static class Recorder extends OutcomeAdapter
  {
    public enum NoResponseCause {
      TIMEOUT, CANCELLED, ;
    }


    private final BlockingQueue< Outcome > responseQueue = new LinkedBlockingQueue<>( 10 ) ;

    @Override
    protected void outcome( Outcome outcome ) {
      responseQueue.add( outcome ) ;
    }

    /**
     * Blocks until there is an {@link Outcome} available in {@link #responseQueue}.
     */
    public Outcome nextOutcome() throws InterruptedException {
      return responseQueue.take() ;
    }

    public Outcome followRedirects(
        final NettyHttpClient httpClient,
        final HttpResponseStatus responseStatus
    ) throws InterruptedException {
      checkArgument( responseStatus.codeClass() == HttpStatusClass.REDIRECTION ) ;
      Outcome outcome ;
      while( true ) {
        outcome = nextOutcome() ;
        if( outcome instanceof CompleteResponse &&
            ( ( CompleteResponse ) outcome ).response.responseStatus.code() == responseStatus.code()
        ) {
          httpClient.httpGet(
              ( ( CompleteResponse ) outcome ).redirectionTargetUrl(), this ) ;
        } else {
          break ;
        }
      }
      return outcome ;
    }


    /**
     * Use only when everything is stopped.
     */
    public boolean hasNextResponse() {
      return ! responseQueue.isEmpty() ;
    }

    /**
     * Use only when everything is stopped.
     */
    public Collection< Outcome > pendingResponses() {
      final Collection< Outcome > pending = new ArrayList<>() ;
      responseQueue.drainTo( pending ) ;
      return pending ;
    }
  }


// =================
// HTTP conversation
// =================

  public final void httpRequest(
      final Hypermessage.Request request,
      final Watcher watcher
  ) {
    if( request.schemeHostPort.scheme.secure ) {
      checkState(
          sslEngineFactory != null,
          "Can't send " + request + " without an " + SslEngineFactory.class.getSimpleName() + "."
      ) ;
    }
    checkStarted() ;

    final HttpRequest nettyHttpRequest = request.fullHttpRequest( ) ;

    connect(
        request.schemeHostPort.hostPort.asInetSocketAddressQuiet(),
        channelInitializer( request.schemeHostPort.scheme.secure ? sslEngineFactory : null )
    )
        .addListener( ( ChannelFutureListener ) future -> {
          @SuppressWarnings( "ThrowableResultOfMethodCallIgnored" )
          final Throwable cause = future.cause() ;
          if( cause == null ) {
            final Channel channel = future.channel() ;
            final ScheduledFuture< ? > timeoutFuture ;
            try {
              timeoutFuture = channel.eventLoop().schedule(
                  () -> timeout( channel, watcher, request ),
                  timeoutMs,
                  TimeUnit.MILLISECONDS
              ) ;
              extractHandler( channel ).configure( request, watcher, timeoutFuture ) ;
              channel.writeAndFlush( nettyHttpRequest ) ;
              LOGGER.debug( "Sent " + request + "." ) ;
            } catch( final RejectedExecutionException e ) {
              watcher.cancelled( request ) ;
            }
          } else {
            LOGGER.info( "Failed to connect to " + request.uri + "." ) ;
            watcher.failed( request, cause ) ;
          }
        } )
    ;
  }


  public CompletableFuture< CompleteResponse > httpRequest(
      final Hypermessage.Request request
  ) {
    final CompletableFuture< CompleteResponse > completableFuture = new CompletableFuture<>() ;
    final OutcomeAdapter outcomeAdapter = new OutcomeAdapter() {
      @Override
      protected void outcome( Outcome outcome ) {
        if( outcome instanceof CompleteResponse ) {
          completableFuture.complete( ( CompleteResponse ) outcome ) ;
        } else {
          completableFuture.completeExceptionally( new BadOutcomeException( outcome ) ) ;
        }
      }
    } ;
    httpRequest( request, outcomeAdapter ) ;
    return completableFuture ;
  }



// =========
// Utilities
// =========  
  
  private static HttpResponseTier extractHandler( final Channel channel ) {
    return ( HttpResponseTier ) channel.pipeline().last() ;
  }


  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger( 0 ) ;

  /**
   * Maximum length of a single HTTP request/response, because we're tiny and don't need chunks.
   */
  private static final int MAX_CONTENT_LENGTH = Integer.MAX_VALUE ; // 100_000 ;

//  private static final long DEFAULT_TIMEOUT_MS = 5_000 ;
  private static final int DEFAULT_TIMEOUT_MS = 5_000_000 ;

  public static final int DEFAULT_THREAD_COUNT = 2 ;


  @Deprecated
  public static ThreadFactory defaultThreadFactory( final String radix ) {
    final ThreadGroup threadGroup = new ThreadGroup( radix ) ;
    return runnable -> {
      final Thread thread = new Thread(
          threadGroup,
          runnable,
          radix + '-' + THREAD_COUNTER.getAndIncrement()
      ) ;
      thread.setDaemon( true ) ;
      thread.setUncaughtExceptionHandler( ( emittingThread, throwable ) ->
          LOGGER.error( "Thread " + emittingThread + " had a problem.", throwable ) ) ;
      return thread ;
    } ;
  }

  @Deprecated
  public static Supplier< EventLoopGroup > defaultEventLoopGroupSupplier(
      final int threadCount,
      final ThreadFactory threadFactory
  ) {
    return () -> new NioEventLoopGroup( threadCount, threadFactory ) ;
  }

  private static void timeout(
      final Channel channel,
      final Watcher watcher,
      final Hypermessage.Request request
  ) {
    channel.close() ;
    watcher.timeout( request ) ;
  }





// ====================
// Netty ChannelHandler
// ====================

  private static class HttpResponseTier extends SimpleChannelInboundHandler< HttpObject > {

    private Watcher watcher = null ;
    private Hypermessage.Request httpRequest = null ;
    private ScheduledFuture< ? > timeoutFuture = null ;

    private Integer statusCode = null ;
    private String contentAsString = null ;

    public void configure(
        final Hypermessage.Request httpRequest,
        final Watcher watcher,
        final ScheduledFuture< ? > timeoutFuture
    ) {
      this.httpRequest = checkNotNull( httpRequest ) ;
      this.watcher = checkNotNull( watcher ) ;
      this.timeoutFuture = checkNotNull( timeoutFuture ) ;
    }

    /**
     * Implicitely makes use of {@link SimpleChannelInboundHandler#autoRelease} which
     * is {@code true} by default.
     */
    @Override
    protected void channelRead0(
        final ChannelHandlerContext channelHandlerContext,
        final HttpObject httpObject
    ) {
      checkState( watcher != null ) ;
      checkState( httpRequest != null ) ;

      if( httpObject instanceof FullHttpResponse ) {
        final FullHttpResponse fullHttpResponse = ( FullHttpResponse ) httpObject ;
        statusCode = fullHttpResponse.status().code() ;
        contentAsString = fullHttpResponse.content().toString( Hypermessage.CONTENT_ENCODING ) ;
        LOGGER.debug(
            "Received as a response to " + httpRequest + " a " +
                FullHttpResponse.class.getSimpleName() + " with: " +
                "status code=" + statusCode + ", " +
                "content='" +
                truncateIfTooLong( contentAsString )
        ) ;
      }

      if( httpObject instanceof LastHttpContent ) {
        if( ! ( httpObject instanceof FullHttpResponse ) ) {
          LOGGER.debug( "End of content for the response to " + httpRequest + " handled by " +
              this + "." ) ;
        }
        channelHandlerContext.close() ;
        if( statusCode != null && contentAsString != null ) {
          if( timeoutFuture.cancel( false ) ) {
            final ImmutableMultimap< String, String > headers =
                NettyTools.headers( ( HttpMessage ) httpObject ) ;
            watcher.complete(
                httpRequest,
                new Hypermessage.Response(
                    HttpResponseStatus.valueOf( statusCode ),
                    headers,
                    contentAsString
                )
            ) ;
          }
        }
      }
    }

    private static String truncateIfTooLong( String contentAsString ) {
      final int maximumLength = 4000 ;
      return contentAsString.substring( 0, Math.min( maximumLength, contentAsString.length() ) ) +
          ( contentAsString.length() > maximumLength ? " !!!too long, truncated!!!'" : "'" ) ;
    }

    @Override
    public void exceptionCaught(
        final ChannelHandlerContext channelHandlerContext,
        final Throwable cause
    ) {
      LOGGER.warn( "Caught exception from " + this + ".", cause ) ;
      watcher.failed( httpRequest, cause ) ;
      channelHandlerContext.close() ;
    }

    @Override
    public String toString() {
      return
          ToStringTools.nameAndCompactHash( this ) + '{' +
              ( httpRequest == null ? null : httpRequest.uri.toASCIIString() ) +
              '}'
          ;
    }
  }

}
