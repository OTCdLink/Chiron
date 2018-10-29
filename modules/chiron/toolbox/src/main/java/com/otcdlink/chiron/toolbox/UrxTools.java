package com.otcdlink.chiron.toolbox;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.otcdlink.chiron.toolbox.internet.HostPort;
import com.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.URLStreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * URL- and URI-related tools.
 */
public final class UrxTools {
  private UrxTools() { }

  /**
   * Hacks a {@code file:///./xxx} path so underlying resource is relative to current directory,
   * or {@code file:///~/xxx} so resource is relative to user home.
   * Otherwise it works as {@link com.google.common.io.Resources#asByteSource(java.net.URL)} does.
   *
   * @param url a non-null object.
   * @return a non-null object.
   */
  public static ByteSource getByteSource( final URL url ) {
    final ByteSource byteSource ;
    if( "file".equals( url.getProtocol() ) ) {
      final File file = resolveFileFromUrl( url ) ;
      byteSource = Files.asByteSource( file ) ;
    } else {
      byteSource = Resources.asByteSource( url ) ;
    }
    return byteSource ;
  }


  /**
   * Correctly interprets {@code file:///./xxx} so underlying resource is relative
   * to current directory, and {@code file:///~/xxx} so resource is relative to user home.
   *
   * @param url a non-null object.
   * @return a non-null object.
   */
  public static URL absolutize( final URL url ) {
    if( "file".equals( url.getProtocol() ) ) {
      return toUrlQuiet( resolveFileFromUrl( url ) ) ;
    }
    return url ;
  }

  private static File resolveFileFromUrl( final URL url ) {
    checkArgument( "file".equals( url.getProtocol() ) ) ;
    final File file ;
    if( url.getPath().startsWith( "/./" ) ) {
      file = new File( decodeQuiet( url.getPath().substring( 1 ) ) ) ;
    } else if( url.getPath().startsWith( "/~/" ) ) {
      file = new File(
          SafeSystemProperty.Standard.USER_HOME.value,
          decodeQuiet( url.getPath().substring( 3 ) )
      ) ;
    } else {
      file = new File( decodeQuiet( url.getPath() ) ) ;
    }
    return file ;

  }

  private static String decodeQuiet( final String string ) {
    try {
      return URLDecoder.decode( string, Charsets.UTF_8.name() );
    } catch( final UnsupportedEncodingException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  private static URL toUrlQuiet( final File file ) {
    final URL absolute ;
    try {
      absolute = file.getCanonicalFile().getAbsoluteFile().toURL() ;
    } catch( final IOException e ) {
      throw new RuntimeException( e ) ;
    }
    return absolute ;
  }

  public static URL parseUrlQuiet( final String string ) {
    try {
      return new URL( string ) ;
    } catch( final MalformedURLException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public static URI parseUriQuiet( final String string ) {
    try {
      return new URI( string ) ;
    } catch( final URISyntaxException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  /**
   * Appends a segment to some URL's path. The path is everything between the {@code //} after
   * the scheme, and the first character denoting the start of the query part or the start of
   * the fragment. The fragment is not a part of official URL grammar, but Java supports it
   * through {@link URL#getRef()}.
   * <p>
   * Specification:
   * <a href="http://www.w3.org/Addressing/URL/5_BNF.html">URL BNF</a>
   * <a href="http://www.w3.org/Addressing/URL/5_URI_BNF.html">URI BNF</a>
   */
  public static URL derive( final URL resourcesBaseUrl, final String segment ) {
    final String baseToExternalForm = resourcesBaseUrl.toExternalForm() ;
    final String postPath ;
    final String base ;
    final String pathAndFragment = baseToExternalForm.substring(
        resourcesBaseUrl.getProtocol().length() + "://".length() ) ;
    final int endOfPath ;
    {
      final int startOfQuery = pathAndFragment.indexOf( '?' ) ;
      final int startOfFragment = pathAndFragment.indexOf( '#' ) ;
      int smallest = -1 ;
      if( startOfFragment >= 0 ) {
        smallest = startOfFragment ;
      }
      if( startOfQuery >= 0 ) {
        smallest = smallest < 0 ? startOfQuery : Math.min( smallest, startOfQuery ) ;
      }
      endOfPath = smallest ;
    }
    if( endOfPath > 0 ) {
      postPath = pathAndFragment.substring( endOfPath ) ;
      base = baseToExternalForm.substring( 0, baseToExternalForm.length() - postPath.length() ) ;
    } else {
      base = baseToExternalForm ;
      postPath = "" ;
    }
    final String safeConcatenation = appendSegment( base, segment ) + postPath ;
    return parseUrlQuiet( safeConcatenation ) ;
  }

  public static URL deriveUrl( final URI baseUri, final String segment ) {
    final String base = baseUri.toASCIIString() ;
    final String safeConcatenation = appendSegment( base, segment ) ;
    return parseUrlQuiet( safeConcatenation ) ;
  }

  /**
   * Concatenates two URL segments taking care of a leading/trailing {@code /} character.
   */
  public static String appendSegment( final String base, final String segment ) {
    final String safeBase ;
    if( base.endsWith( "/" ) ) {
      safeBase = base.substring( 0, base.length() - 1 ) ;
    } else {
      safeBase = base ;
    }
    final String safeSegment ;
    if( segment.startsWith( "/" ) ) {
      safeSegment = segment.substring( 1 ) ;
    } else {
      safeSegment = segment ;
    }
    if( "/".equals( segment ) ) {
      return safeBase + segment ;
    }
    if( safeBase.isEmpty() || safeSegment.isEmpty() ) {
      return safeBase + safeSegment ;

    } else {
      return safeBase + '/' + safeSegment ;
    }
  }

  public static URL removePath( final URL url ) {
    try {
      if( url.getProtocol().startsWith( "ws" ) ) {
        return websocketUrl( hasSecureScheme( url ), url.getHost(), url.getPort(), "" ) ;
      } else {
        return new URL( url.getProtocol(), url.getHost(), url.getPort(), "" ) ;
      }
    } catch( final MalformedURLException e ) {
      throw new RuntimeException( "Should not happen basing on '" + url.toExternalForm() +
          "'", e ) ;
    }
  }

  public static String urlEncodeUtf8( final String string ) {
    try {
      return URLEncoder.encode( string, Charsets.UTF_8.name() ) ;
    } catch( final UnsupportedEncodingException e ) {
      throw Throwables.propagate( e ) ;
    }
  }

  public static String urlDecodeUtf8( final String string ) {
    try {
      return URLDecoder.decode( string, Charsets.UTF_8.name() ) ;
    } catch( final UnsupportedEncodingException e ) {
      throw Throwables.propagate( e ) ;
    }
  }

  public static URL fromFileQuiet( final File file ) {
    try {
      return file.toURI().toURL() ;
    } catch( final MalformedURLException e ) {
      throw Throwables.propagate( e ) ;
    }
  }

  public static URL toUrlQuiet( final URI uri ) {
    try {
      return uri.toURL() ;
    } catch( final MalformedURLException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public static URI toUriQuiet( final URL url ) {
    try {
      return url.toURI() ;
    } catch( final URISyntaxException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  private static final URLStreamHandler WEBSOCKET_URL_STREAM_HANDLER = new URLStreamHandler() {
    @Override
    protected URLConnection openConnection( final URL doNotUse ) throws IOException {
      throw new UnsupportedOperationException( "Do not open directly" ) ;
    }

    @Override
    public String toString() {
      return URLStreamHandler.class.getSimpleName() + "#WEBSOCKET_ONLY_DO_NOT_USE{}" ;
    }
  } ;

  public static boolean hasSecureScheme( final URL url ) {
    return "https".equals( url.getProtocol() ) || "wss".equals( url.getProtocol() ) ;
  }

  public static URL websocketUrl(
      final boolean useTls,
      final HostPort hostPort,
      final String path
  ) throws MalformedURLException {
    return websocketUrl( useTls, hostPort.hostname.asString(), hostPort.port, path ) ;
  }

  public static URL websocketUrlQuiet(
      final boolean useTls,
      final HostPort hostPort,
      final String path
  ) {
    try {
      return websocketUrl( useTls, hostPort.hostname.asString(), hostPort.port, path ) ;
    } catch( MalformedURLException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public static URL websocketUrlQuiet(
      final boolean useTls,
      final String host,
      final int port,
      final String path
  ) {
    try {
      return websocketUrl( useTls, host, port, path ) ;
    } catch( final MalformedURLException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public static URL websocketUrl(
      final boolean useTls,
      final String host,
      final int port,
      final String path
  ) throws MalformedURLException {
    return new URL(
        useTls ? "wss" : "ws",
        host,
        port,
        path,
        WEBSOCKET_URL_STREAM_HANDLER
    ) ;
  }

  public static URL checkValidWebsocketUrl( final URL url ) {
    if( ! "ws".equals( url.getProtocol() ) && ! "wss".equals( url.getProtocol() ) ) {
      throw new MalformedWebsocketUrlException( "Unsupported protocol " + url.getProtocol(), url ) ;
    }
    if( Strings.isNullOrEmpty( url.getHost() ) ) {
      throw new MalformedWebsocketUrlException( "Missing hostname", url ) ;
    }
    if( url.getUserInfo() != null ) {
      throw new MalformedWebsocketUrlException( "Should have no UserInfo", url ) ;
    }
    if( url.getQuery() != null ) {
      throw new MalformedWebsocketUrlException( "Should have no Query", url ) ;
    }
    if( url.getRef() != null ) {
      throw new MalformedWebsocketUrlException( "Should have no Ref", url ) ;
    }
    return url ;
  }

  /**
   * Replaces {@code URI}'s scheme, host and port by the given one.
   * This also works if there is no scheme or authority in given URI.
   */
  public static URI derive( final URI uri, final SchemeHostPort expectedHttpHost ) {
    final StringBuilder builder = new StringBuilder( expectedHttpHost.uriString() ) ;
    if( uri.getPath() != null ) {
      builder.append( uri.getPath() ) ;
    }
    if( ! Strings.isNullOrEmpty( uri.getQuery() ) ) {
      builder.append( '?' ).append( uri.getQuery() ) ;
    }
    if( ! Strings.isNullOrEmpty( uri.getFragment() ) ) {
      builder.append( '#' ).append( uri.getFragment() ) ;
    }

    return parseUriQuiet( builder.toString() ) ;
  }

  public static String derive( final String uri, final SchemeHostPort expectedHttpHost ) {
    return derive( parseUriQuiet( uri ), expectedHttpHost ).toASCIIString() ;
  }

  public static int safePort( final URI uri ) {
    final int port ;
    {
      final int rawPort = uri.getPort() ;
      if( rawPort > 0 ) {
        port = rawPort ;
      } else {
        if( "http".equals( uri.getScheme() ) ) {
          port = 80 ;
        } else if( "https".equals( uri.getScheme() ) ) {
          port = 443 ;
        } else {
          throw new IllegalArgumentException( "Unsupported scheme for " + uri.toASCIIString() ) ;
        }
      }
    }
    return port ;
  }

  public static class MalformedWebsocketUrlException extends RuntimeException {
    public MalformedWebsocketUrlException( final String message, final URL url ) {
      super( message + " in " + ( url == null ? null : url.toExternalForm() ) ) ;
    }
  }


  public static final class Parsing {

    private Parsing() { }

    public static final Pattern PATH_PATTERN = Pattern.compile(
        "((?:/[a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9_-]+)*)*/?)" ) ;
    /**
     * Quick and dirty implementation that omits lots of features.
     * This should be a valid URI: 'foo.bar' but it is not because path expects a leading '/'.
     */
    public static final Pattern URI_PATTERN = Pattern.compile(
        "(?:([a-z]+)://([a-zA-Z0-9]+(?:\\.[a-zA-Z0-9]+)*)(?::([0-9]+))?)?" +
            PATH_PATTERN.pattern() +
            "(?:\\?([a-zA-Z0-9=&%;_+@.\\-]*))?"
    ) ;

    static {
      LoggerFactory.getLogger( UrxTools.class ).debug( "Crafted regex " + URI_PATTERN.pattern() ) ;
    }

    public static Matcher safeMatcher( final String candidateUrl ) {
      final Matcher matcher = URI_PATTERN.matcher( candidateUrl ) ;
      checkArgument( matcher.matches(), "Given URL '" + candidateUrl + "' " +
          "does not match pattern '" + URI_PATTERN.pattern() + "'" ) ;
      return matcher ;
    }

    public enum Part {
      SCHEME,
      HOST,
      PORT,
      PATH,
      QUERY,
      ;

      public String extract( final Matcher matcher ) {
        return matcher.group( ordinal() + 1 ) ;
      }
    }


  }

}
