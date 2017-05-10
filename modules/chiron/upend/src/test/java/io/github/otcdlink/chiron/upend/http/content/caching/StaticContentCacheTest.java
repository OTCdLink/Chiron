package io.github.otcdlink.chiron.upend.http.content.caching;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import io.github.otcdlink.chiron.fixture.http.WatchedResponseAssert;
import io.github.otcdlink.chiron.upend.http.dispatch.HttpDispatcher;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Tests for {@link StaticContentCache} using
 * {@link HttpDispatcher#resourceMatch(StaticContentCache)}.
 */
public class StaticContentCacheTest extends StaticContentTest {

  @Test
  public void justServe() throws Exception {
    initialize( map(
        RESOURCE_FAVICON, ByteSource.wrap( faviconAsBytes() )
    ) ) ;

    httpGet( "/" + RESOURCE_FAVICON ) ;
    WatchedResponseAssert.assertThat( httpResponseRecorder.nextOutcome() )
        .isComplete()
        .hasContent( new String( faviconAsBytes() ) )
        .hasHeader( HttpHeaderNames.CONTENT_TYPE, "image/x-icon" )
    ;

    httpGet( "/does-not-exist.ico" ) ;
    WatchedResponseAssert.assertThat( httpResponseRecorder.nextOutcome() )
        .isComplete()
        .hasStatusCode( HttpResponseStatus.NOT_FOUND )
    ;

  }

  @Test
  public void serveWithinContextPath() throws Exception {
    initialize( httpDispatcher -> httpDispatcher
        .beginPathSegment( "/static" )
            .beginPathSegment( "/images" )
                .resourceMatch( staticContentCache(
                    RESOURCE_LOGO, ByteSource.wrap( logoAsBytes() )
                ) )
            .endPathSegment()
            .beginPathSegment( "/js" )
                .resourceMatch( staticContentCache(
                    RESOURCE_JS1, ByteSource.wrap( script1AsBytes() ),
                    RESOURCE_JS2, ByteSource.wrap( script2AsBytes() )
                ) )
            .endPathSegment()
        .endPathSegment()
    ) ;

    checkHttpResponse( "/static/images/" + RESOURCE_LOGO, logoAsBytes(), "image/png" ) ;
    checkHttpResponse( "/static/js/" + RESOURCE_JS1, script1AsBytes(), "application/javascript" ) ;
    checkHttpResponse( "/static/js/" + RESOURCE_JS2, script2AsBytes(), "application/javascript" ) ;

  }

  @Test
  public void sharedContentCache() throws Exception {
    final StaticContentCache contentCache = staticContentCache(
        "images/" + RESOURCE_LOGO, ByteSource.wrap( logoAsBytes() ),
        "js/" + RESOURCE_JS1, ByteSource.wrap( script1AsBytes() ),
        "js/" + RESOURCE_JS2, ByteSource.wrap( script2AsBytes() )
    ) ;
    initialize( httpDispatcher -> httpDispatcher
        .beginPathSegment( "/static" )
            .resourceMatch( contentCache )
        .endPathSegment()
    ) ;

    checkHttpResponse( "/static/images/" + RESOURCE_LOGO, logoAsBytes(), "image/png" ) ;
    checkHttpResponse( "/static/js/" + RESOURCE_JS1, script1AsBytes(), "application/javascript" ) ;
    checkHttpResponse( "/static/js/" + RESOURCE_JS2, script2AsBytes(), "application/javascript" ) ;

  }


// =======
// Fixture
// =======

  private static final String RESOURCE_FAVICON = "favicon.ico" ;
  private static final String RESOURCE_LOGO = "logo.png" ;
  private static final String RESOURCE_JS1 = "script1.js" ;
  private static final String RESOURCE_JS2 = "script2.js" ;

  private static byte[] faviconAsBytes() {
    return "Favicon here".getBytes( Charsets.UTF_8 ) ;
  }

  private static byte[] logoAsBytes() {
    return "Logo here".getBytes( Charsets.UTF_8 ) ;
  }

  private static byte[] script1AsBytes() {
    return "Script 1".getBytes( Charsets.UTF_8 ) ;
  }

  private static byte[] script2AsBytes() {
    return "Script 2".getBytes( Charsets.UTF_8 ) ;
  }


  private static StaticContentCache staticContentCache(
      final String resource0, final ByteSource byteSource0,
      final String resource1, final ByteSource byteSource1
  ) {
    return StaticContentCacheFactory.newCache(
        MIMETYPE_MAP,
        ImmutableMap.of(
            resource0, byteSource0,
            resource1, byteSource1
        )
    ) ;
  }

  private static StaticContentCache staticContentCache(
      final String resource0, final ByteSource byteSource0,
      final String resource1, final ByteSource byteSource1,
      final String resource2, final ByteSource byteSource2
  ) {
    return StaticContentCacheFactory.newCache(
        MIMETYPE_MAP,
        ImmutableMap.of(
            resource0, byteSource0,
            resource1, byteSource1,
            resource2, byteSource2
        ),
        Executors.newFixedThreadPool( 3 )
    ) ;
  }

  private static StaticContentCache staticContentCache(
      final String resource0, final ByteSource byteSource0
  ) {
    return StaticContentCacheFactory.newCache( MIMETYPE_MAP, ImmutableMap.of( resource0, byteSource0 ) ) ;
  }

  private Consumer< HttpDispatcher< ?, ?, Void, Void, ? extends HttpDispatcher > > map(
      final String resource0,
      final ByteSource byteSource0
  ) {
    return configurator( ImmutableMap.of( resource0, byteSource0 ) ) ;
  }

  private Consumer< HttpDispatcher< ?, ?, Void, Void, ? extends HttpDispatcher > > configurator(
      final ImmutableMap< String, ByteSource > map
  ) {
    return httpDispatcher -> {
      final StaticContentCache contentCache =
          staticContentCacheFactory.sharedCache( MIMETYPE_MAP, map ) ;
      httpDispatcher.resourceMatch( contentCache ) ;
    } ;
  }


  private static final ImmutableMap< String, String > MIMETYPE_MAP = ImmutableMap.of(
      "js", "application/javascript",
      "png", "image/png",
      "ico", "image/x-icon"  // Not a IANA type: http://stackoverflow.com/a/13828914/1923328
  ) ;

  public StaticContentCacheTest() throws UnknownHostException { }

  private void checkHttpResponse(
      final String absoluteResourcePath,
      final byte[] contentAsBytes,
      final String contentType
  ) throws InterruptedException {
    httpGet( absoluteResourcePath ) ;
    WatchedResponseAssert.assertThat( httpResponseRecorder.nextOutcome() )
        .isComplete()
        .hasStatusCode( HttpResponseStatus.OK )
        .hasContent( new String( contentAsBytes ) )
        .hasHeader( HttpHeaderNames.CONTENT_TYPE, contentType )
    ;
  }

  protected final StaticContentCacheFactory staticContentCacheFactory =
      new StaticContentCacheFactory() ;

  @Override
  public void tearDown() throws Exception {
    super.tearDown() ;
    staticContentCacheFactory.flushShare() ;
  }
}
