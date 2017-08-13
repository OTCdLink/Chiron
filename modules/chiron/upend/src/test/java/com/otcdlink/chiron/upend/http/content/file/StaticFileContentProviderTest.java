package com.otcdlink.chiron.upend.http.content.file;

import com.google.common.base.Charsets;
import com.otcdlink.chiron.fixture.http.WatchedResponseAssert;
import com.otcdlink.chiron.testing.MethodSupport;
import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import com.otcdlink.chiron.upend.http.content.caching.StaticContentCacheTest;
import com.otcdlink.chiron.upend.http.content.caching.StaticContentTest;
import com.otcdlink.chiron.upend.http.dispatch.HttpDispatcher;
import com.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StaticFileContentProvider} using
 * {@link HttpDispatcher#file(StaticFileContentProvider)}.
 */
public class StaticFileContentProviderTest extends StaticContentTest {

  @Test
  @Ignore( "Requires manual startup")
  public void useNettySampleStartedInteractively() throws Exception {
    httpClient.httpGet(
        // File must not be too large for an HTTP client that doesn't support chunking.
        UrxTools.parseUrlQuiet( "http://127.0.0.1:8080/Derived.class" ),
        httpResponseRecorder
    ) ;
    final NettyHttpClient.Outcome response = httpResponseRecorder.nextOutcome() ;
    WatchedResponseAssert.assertThat( response )
        .isComplete().hasStatusCode( HttpResponseStatus.OK ) ;
  }

  @Test
  public void loadFileContent() throws Exception {
    final FileFixture fileFixture = new FileFixture( methodSupport.getDirectory() ) ;
    final StaticFileContentProvider fileContentProvider = new StaticFileContentProvider(
        fileFixture.directory, fileFixture.mimeTypeMap() ) ;
    initialize( httpDispatcher -> httpDispatcher.file( fileContentProvider ) ) ;

    for( final FileFixture.FileElement fileElement : fileFixture.fileElements() ) {
      httpGet( "/" + fileElement.relativePath ) ;
      WatchedResponseAssert.assertThat( httpResponseRecorder.nextOutcome() )
          .isComplete()
          .hasStatusCode( HttpResponseStatus.OK )
          .hasHeader( HttpHeaderNames.CONTENT_TYPE, fileElement.mimeType() )
          .hasContent( fileElement.content )
      ;
    }

    httpGet( "/does-not-exist.ico" ) ;
    WatchedResponseAssert.assertThat( httpResponseRecorder.nextOutcome() )
        .isComplete()
        .hasStatusCode( HttpResponseStatus.NOT_FOUND )
    ;

  }

  /**
   * Check if we send no content in the response to a HEAD request.
   * <a href="https://github.com/threerings/getdown">Getdown</a> uses JRE's {@link URLConnection}
   * to issue a HEAD request for downloading files. If the HTTP response has a content, something
   * bad happens when issuing several HEAD requests. It looks like a statically-shared resource
   * exhaustion.
   * <p>
   * This test does not check content length, instead it copies some of Getdown code (which uses
   * JRE's HTTP classes in a fairly normal way) to ensure {@link UsualHttpCommands.JustFile}
   * causes no havoc.
   */
  @Test
  public void crappyHeadRequest() throws Exception {
    final FileFixture fileFixture = new FileFixture( methodSupport.getDirectory() ) ;
    final StaticFileContentProvider fileContentProvider = new StaticFileContentProvider(
        fileFixture.directory, fileFixture.mimeTypeMap() ) ;
    initialize( httpDispatcher -> httpDispatcher.file( fileContentProvider ) ) ;

    for( final FileFixture.FileElement fileElement : fileFixture.fileElements() ) {
      final URL url = httpRequestUrl( "/" + fileElement.relativePath ) ;
      final long size = CopyPastedFromGetdown16.HttpDownloader.checkSize( url ) ;
      assertThat( size ).isEqualTo( fileElement.content.length() ) ;
    }
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( StaticContentCacheTest.class ) ;

  private static final Charset CHARSET = Charsets.US_ASCII ;

  @Rule
  public final MethodSupport methodSupport = new MethodSupport() ;

  @Override
  protected int httpClientTimeoutMs() {
    return 10_000_000 ;
  }



  public StaticFileContentProviderTest() throws UnknownHostException { }

  @Override
  public void tearDown() throws Exception {
    super.tearDown() ;

  }

  private interface CopyPastedFromGetdown16 {

    interface ConnectionUtil {

      @SuppressWarnings( "DynamicRegexReplaceableByCompiledPattern" )
      static URLConnection open( final URL url ) throws IOException {
        final URLConnection urlConnection = url.openConnection() ;

        // If URL has a username:password@ before hostname, use HTTP basic auth
        String userInfo = url.getUserInfo() ;
        if( userInfo != null ) {
          // Remove any percent-encoding in the username/password
          userInfo = URLDecoder.decode( userInfo, "UTF-8" ) ;
          // Now base64 encode the auth info and make it a single line
          final byte[] encodedBytes = Base64.getEncoder().encode( userInfo.getBytes( "UTF-8" ) ) ;
          final String encoded = new String( encodedBytes, Charsets.US_ASCII )
              .replaceAll( "\\n", "" ).replaceAll( "\\r", "" ) ;
          urlConnection.setRequestProperty( "Authorization", "Basic " + encoded ) ;
        }
        return urlConnection ;
      }
    }

    interface HttpDownloader {
      static long checkSize ( final URL url ) throws IOException {
        final URLConnection urlConnection = ConnectionUtil.open( url ) ;
        try {
          // if we're accessing our data via HTTP, we only need a HEAD request.
          if( urlConnection instanceof HttpURLConnection ) {
            final HttpURLConnection httpURLConnection = ( HttpURLConnection ) urlConnection ;
            httpURLConnection.setRequestMethod( "HEAD" ) ;
            httpURLConnection.connect() ;
            // Make sure we got a satisfactory response code.
            // Trying to get the response code may throw an exception because the response
            // is not available yet.
            if( httpURLConnection.getResponseCode() != HttpURLConnection.HTTP_OK ) {
              throw new IOException( "Unable to check up-to-date for " +
                  url + ": " + httpURLConnection.getResponseCode() ) ;
            }
          }
          return urlConnection.getContentLength() ;

        } finally {
          // Let it be known that we're done with this connection.
          urlConnection.getInputStream().close() ;
        }
      }

    }

  }


}
