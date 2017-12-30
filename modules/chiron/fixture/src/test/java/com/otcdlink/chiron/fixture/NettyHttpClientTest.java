package com.otcdlink.chiron.fixture;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.ByteStreams;
import com.otcdlink.chiron.fixture.http.WatchedResponseAssert;
import com.otcdlink.chiron.toolbox.TcpPortBooker;
import com.otcdlink.chiron.toolbox.netty.EventLoopGroupOwner;
import com.otcdlink.chiron.toolbox.netty.Hypermessage;
import com.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Injectable;
import mockit.VerificationsInOrder;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.otcdlink.chiron.toolbox.internet.LocalAddressTools.localSocketAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class NettyHttpClientTest {

  @Test
  @Ignore
  public void smokeTestForEmbeddedServer() throws Exception {
    final HttpServer httpServer = httpServer( port, "/", HttpHandlers.getOk() ) ;
    try {
      LOGGER.info( "Let you play a bit with your Web browser..." ) ;
      Thread.sleep( 20_000 ) ;
    } finally {
      httpServer.stop( 0 ) ;
    }
  }

  @Test( timeout = TIMEOUT )
  public void justGet(
      @Injectable final NettyHttpClient.Watcher watcher
  ) throws Exception {
    Thread.currentThread().setName( "JUnit" ) ;

    final Semaphore serverHandledRequestSemaphore = new Semaphore( 0 ) ;
    final HttpServer httpServer = httpServer(
        port,
        "/",
        compose(
            HttpHandlers.getOk(),
            HttpHandlers.releaseWhenDone( serverHandledRequestSemaphore )
        )
    ) ;

    final NettyHttpClient httpClient = new NettyHttpClient() ;
    httpClient.start() ;

    try {
      final URL url = new URL( "http://localhost:" + port + "/" ) ;

      final BlockingMonolist<Hypermessage.Request> requestCapture = new BlockingMonolist<>() ;
      final BlockingMonolist< Hypermessage.Response > responseCapture = new BlockingMonolist<>() ;

      new Expectations() {{
        watcher.complete(
            withCapture( requestCapture ),
            withCapture( responseCapture )
        ) ;
      }} ;

      httpClient.httpGet( url, watcher ) ;

      LOGGER.info( "Waiting for every verification to happen before exiting the test ..." );
      serverHandledRequestSemaphore.acquire() ;

      Assertions.assertThat( requestCapture.getOrWait().uri.toASCIIString() )
          .isEqualTo( url.toExternalForm() ) ;

      final Hypermessage.Response response = responseCapture.getOrWait() ;
      assertThat( ( response.headers ).get( "Content-length" ) )
          .isEqualTo( ImmutableList.of( "14" ) ) ;

      new VerificationsInOrder() {{ }} ;
      LOGGER.info( "Verifications succeeded." ) ;
    } finally {
      httpClient.stop() ;
      httpServer.stop( 0 ) ;
    }
  }


  @Test( timeout = TIMEOUT )
  public void httpsWithNoSslEngineFactory( @Injectable final NettyHttpClient.Watcher watcher ) throws Exception {
    Thread.currentThread().setName( "JUnit" ) ;

    try( final NettyHttpClient httpClient = NettyHttpClient.createStarted() ) {
      final URL url = new URL( "https://localhost:" + port + "/" ) ;

      assertThatThrownBy( () -> httpClient.httpGet( url, watcher ) )
          .isInstanceOf( IllegalStateException.class )
          .hasMessageContaining( "without an SslEngineFactory" )
      ;

    }
  }

  @Test( timeout = TIMEOUT )
  public void justGetWithQueingWatcher() throws Exception {
    Thread.currentThread().setName( "JUnit" ) ;

    final Semaphore serverHandledRequestSemaphore = new Semaphore( 0 ) ;
    final HttpServer httpServer = httpServer(
        port,
        "/",
        compose(
            HttpHandlers.getOk(),
            HttpHandlers.releaseWhenDone( serverHandledRequestSemaphore )
        )
    ) ;

    try( final NettyHttpClient httpClient = NettyHttpClient.createStarted() )  {
      final URL url = new URL( "http://localhost:" + port + "/" ) ;

      final NettyHttpClient.Recorder recorder = new NettyHttpClient.Recorder() ;
      httpClient.httpGet( url, recorder ) ;

      WatchedResponseAssert.assertThat( recorder.nextOutcome() ).isComplete() ;

    } finally {
      httpServer.stop( 0 ) ;
    }
  }

  @Test( timeout = TIMEOUT )
  public void followRedirect() throws Exception {
    Thread.currentThread().setName( "JUnit" ) ;

    final Semaphore serverHandledRequestSemaphore = new Semaphore( 0 ) ;
    final AtomicInteger redirectionCounter = new AtomicInteger( 3 ) ;
    final HttpHandler redirectionHandler = HttpHandlers.redirect( 302 ) ;

    final HttpServer httpServer = httpServer(
        port,
        "/",
        httpExchange -> {
          if( redirectionCounter.decrementAndGet() >= 0 ) {
            redirectionHandler.handle( httpExchange ) ;
          } else {
            HttpHandlers.getOk().handle( httpExchange ) ;
            serverHandledRequestSemaphore.release() ;
          }
        }
    ) ;

    try( final NettyHttpClient httpClient = NettyHttpClient.createStarted() ) {
      final URL url = new URL( "http://localhost:" + port + "/" ) ;

      final NettyHttpClient.Recorder recorder = new NettyHttpClient.Recorder() ;
      httpClient.httpGet( url, recorder ) ;

      final NettyHttpClient.Outcome outcome =
          recorder.followRedirects( httpClient, HttpResponseStatus.FOUND ) ;
      WatchedResponseAssert.assertThat( outcome ).isComplete() ;

    } finally {
      httpServer.stop( 0 ) ;
    }
  }


  @Test( timeout = TIMEOUT )
  public void justPost( @Injectable final NettyHttpClient.Watcher watcher ) throws Exception {
    final ImmutableMultimap< String, String > formParameters =
        ImmutableMultimap.of( "foo", "bar", "boo", "baz" ) ;
    final int expectedContentLength = 127 ;

    verifyPost( watcher, formParameters, expectedContentLength ) ;

  }

  @Test( timeout = TIMEOUT )
  public void postEmpty( @Injectable final NettyHttpClient.Watcher watcher ) throws Exception {

    final int expectedContentLength = 41 ;
    final ImmutableMultimap< String, String > formParameters = ImmutableMultimap.of() ;

    verifyPost( watcher, formParameters, expectedContentLength ) ;
  }



  @Test( timeout = TIMEOUT )
  public void timeout( @Injectable final NettyHttpClient.Watcher watcher ) throws Exception {
    Thread.currentThread().setName( "JUnit" ) ;

    final Semaphore watcherHandledRequestSemaphore = new Semaphore( 0 ) ;
    final HttpServer httpServer = httpServer( port, "/", compose() ) ;


    try(
        // Timeout of 0 will probably cause cancellation before the request happens but it's OK.
        final NettyHttpClient httpClient = new NettyHttpClient(
            EventLoopGroupOwner.EventLoopGroupFactory::defaultFactory, 0, null )
    ) {
      httpClient.start() ;
      final URL url = new URL( "http://localhost:" + port + "/" ) ;
      new Expectations() { {
        watcher.timeout(
            with( new Delegate< Hypermessage.Request.Get >() {
              @SuppressWarnings( "unused" )
              public void __( final Hypermessage.Request.Get httpRequest ) {
                assertThat( httpRequest.uri.toASCIIString() ).isEqualTo( url.toExternalForm() ) ;
                watcherHandledRequestSemaphore.release() ;
              }
            } )
        ) ;
      } } ;

      httpClient.httpGet( url, watcher ) ;

      // Get sure everything happened before exiting the test and verifying Expectations.
      watcherHandledRequestSemaphore.acquire() ;

    } finally {
      httpServer.stop( 0 ) ;
    }
  }

  @Test( timeout = TIMEOUT )
  public void restart() throws Exception {
    Thread.currentThread().setName( "JUnit" ) ;

    final Semaphore watcherNotifiedSemaphore = new Semaphore( 0 ) ;
    final AtomicInteger failureCount = new AtomicInteger( 0 ) ;

    final URL url = new URL( "http://localhost:" + port + "/" ) ;

    final NettyHttpClient.Watcher watcher = new NettyHttpClient.Watcher() {
          @Override
          public void complete(
              final Hypermessage.Request request,
              final Hypermessage.Response response
          ) {
            Assertions.assertThat( NettyHttpClient.Watcher.success( response.responseStatus.code() ) ).isTrue() ;
            watcherNotifiedSemaphore.release() ;
          }

          @Override
          public void failed( final Hypermessage.Request request, final Throwable throwable ) {
            failureCount.incrementAndGet() ;
          }

          @Override
          public void timeout( final Hypermessage.Request request ) {
            failureCount.incrementAndGet() ;
          }

          @Override
          public void cancelled( final Hypermessage.Request request ) { }
        }
    ;

    final HttpServer httpServer = httpServer( port, "/", HttpHandlers.getOk() ) ;
    final NettyHttpClient httpClient = new NettyHttpClient() ;

    httpClient.start().join() ;
    httpClient.httpGet( url, watcher ) ;
    watcherNotifiedSemaphore.acquire( 1 ) ;
    httpClient.stop().join() ;

    httpClient.start().join() ;
    httpClient.httpGet( url, watcher ) ;
    watcherNotifiedSemaphore.acquire( 1 ) ;
    httpClient.stop() ;

    assertThat( failureCount.get() ).isEqualTo( 0 ) ;

    httpServer.stop( 0 ) ;

  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( NettyHttpClientTest.class ) ;
  private final int port = TcpPortBooker.THIS.find() ;

  private static HttpServer httpServer(
      final int port,
      final String uriContext,
      final HttpHandler httpHandler
  ) throws IOException {
    final HttpServer server = HttpServer.create( localSocketAddress( port ), 0 ) ;
    server.setExecutor( Executors.newSingleThreadExecutor( runnable -> {
      final Thread thread = new Thread( runnable, HttpServer.class.getSimpleName() ) ;
      thread.setDaemon( true ) ;
      return thread ;
    } ) ) ;
    server.createContext( uriContext, httpHandler ) ;
    server.start() ;
    LOGGER.info( "Created and started " + HttpServer.class.getSimpleName() +
        ":" + port + uriContext ) ;
    return server ;
  }

  private static HttpHandler compose( final HttpHandler... httpHandlers ) {
    return httpExchange -> {
      for( final HttpHandler httpHandler : httpHandlers ) {
        httpHandler.handle( httpExchange ) ;
      }
    } ;
  }

  private interface HttpHandlers {

    static HttpHandler redirect( final int redirectionCode ) {
      return httpExchange -> {
        LOGGER.info( "Received " + asLoggableString( httpExchange ) ) ;
        final String redirectionTarget = httpExchange.getRequestURI().toASCIIString() ;
        LOGGER.info( "Sending redirection to '" + redirectionTarget +
            "' with status code " + redirectionCode + " ..." ) ;
        httpExchange.getResponseHeaders().set(
            HttpHeaderNames.LOCATION.toString(), redirectionTarget ) ;
        final String response = "Redirected." ;
        httpExchange.sendResponseHeaders( redirectionCode, response.length() ) ;
        final OutputStream outputStream = httpExchange.getResponseBody() ;
        outputStream.write( response.getBytes() ) ;
        outputStream.close() ;
      } ;
    }

    static HttpHandler getOk() {
      return httpExchange -> {
        LOGGER.info( "Received " + asLoggableString( httpExchange ) ) ;
        final String response = "It just works." ;
        httpExchange.sendResponseHeaders( 200, response.length() ) ;
        final OutputStream outputStream = httpExchange.getResponseBody() ;
        outputStream.write( response.getBytes() ) ;
        outputStream.close() ;
      } ;
    }

    static HttpHandler dump( final Consumer< String > responseSink ) {
      return httpExchange -> {
        final String response =
            "Headers: " + httpExchange.getRequestHeaders().entrySet() + ", " +
            "Body: " + new String(
                ByteStreams.toByteArray( httpExchange.getRequestBody() ),
                Charsets.US_ASCII
            )
        ;
        LOGGER.info( "Received " + asLoggableString( httpExchange ) + ", " + response ) ;
        httpExchange.sendResponseHeaders( 200, response.length() ) ;
        final OutputStream outputStream = httpExchange.getResponseBody() ;
        outputStream.write( response.getBytes() ) ;
        outputStream.close() ;
        responseSink.accept( response ) ;
      } ;
    }

    static HttpHandler releaseWhenDone( final Semaphore semaphore ) {
      LOGGER.info( "Releasing " + semaphore + " ..." ) ;
      return httpExchange -> semaphore.release() ;
    }
  }

  private static String asLoggableString( final HttpExchange httpExchange ) {
    return
        "protocol: " + httpExchange.getProtocol() + ", " +
        "localAddress: " + httpExchange.getLocalAddress() + ", " +
        "requestMethod: " + httpExchange.getRequestMethod() + ", " +
        "requestUri: " + httpExchange.getRequestURI()
    ;
  }

//  private static final long TIMEOUT = 5_000 ;
  private static final long TIMEOUT = 5_000_000 ;

  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.info( "=== Test begins here ===" ) ;
  }


  private void verifyPost(
      final NettyHttpClient.Watcher watcher,
      final ImmutableMultimap< String, String > formParameters,
      final int expectedContentLength
  ) throws IOException, InterruptedException {
    Thread.currentThread().setName( "JUnit" ) ;

    final BlockingMonolist< String > responseDumpQueue = new BlockingMonolist<>() ;

    final BlockingMonolist< Hypermessage.Request > requestCapture = new BlockingMonolist<>() ;
    final BlockingMonolist< Hypermessage.Response > responseCapture = new BlockingMonolist<>() ;

    final HttpServer httpServer = httpServer(
        port,
        "/",
        HttpHandlers.dump( responseDumpQueue::add )
    ) ;

    try( final NettyHttpClient httpClient = NettyHttpClient.createStarted() ) {
      final URL url = new URL( "http://localhost:" + port + "/" ) ;
      new Expectations() { {
        watcher.complete(
            withCapture( requestCapture ),
            withCapture( responseCapture )
        ) ;
      } } ;

      final Hypermessage.Request.Post post = new Hypermessage.Request.Post( url, formParameters ) ;
      httpClient.httpRequest( post, watcher ) ;

      final Hypermessage.Request request = requestCapture.getOrWait() ;
      final Hypermessage.Response response = responseCapture.getOrWait() ;
      final String content = response.contentAsString ;

      assertThat( request.uri.toASCIIString() )
          .isEqualTo( url.toExternalForm() ) ;

      assertThat( response.headers.get( "Content-length" ) )
          .isEqualTo( ImmutableList.of( "" + expectedContentLength ) ) ;

      assertThat( content ).isEqualTo( responseDumpQueue.getOrWait() ) ;

    } finally {
      httpServer.stop( 0 ) ;
    }
  }

}