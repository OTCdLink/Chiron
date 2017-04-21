package io.github.otcdlink.chiron.toolbox.internet;

import com.google.common.base.Equivalence;
import io.github.otcdlink.chiron.toolbox.UrxTools;

import javax.annotation.ParametersAreNonnullByDefault;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents the scheme-host-port part of a URL.
 * Instances of this class are immutable.
 * <p>
 * Heavily inspired by {@code org.apache.http.HttpHost} for which {@link SchemeHostPort} strives
 * to be a replacement as we are dropping Apache Commons.
 */
public final class SchemeHostPort {

  /**
   * Should we make this enumeration something more pluggable?
   * For now Apache Commons' {@code HttpHost} did a good job, restricting the scheme to HTTP(S)
   * but there is a tentation to extend to WS(S) and even more, including custom schemes like
   * {@code keystore:} (which is a truly bad example since we use it in a context where no port
   * is needed).
   */
  public enum Scheme {
    HTTP( false, 80 ),
    HTTPS( true, 443 ),
    ;
    public final boolean secure ;

    /**
     * Made nullable in case we add a scheme with no default port one day.
     */
    public final Integer defaultPort ;

    Scheme( final boolean secure, final Integer defaultPort ) {
      this.secure = secure ;
      this.defaultPort = defaultPort ;
    }


    public String nameLowerCase() {
      return name().toLowerCase() ;
    }

    public String ornated() {
      return nameLowerCase() + "://" ;
    }

    public static Scheme resolve( final String schemeAsString ) {
      for( final Scheme scheme : values() ) {
        if( scheme.nameLowerCase().equals( schemeAsString ) ) {
          return scheme ;
        }
      }
      throw new CreationException(
          "Unknown " + Scheme.class.getSimpleName() + ": '" + schemeAsString + "'" ) ;
    }
  }

  public final Scheme scheme ;

  public final HostPort hostPort ;

  private SchemeHostPort( final Scheme scheme, final HostPort hostPort ) {
    this.scheme = checkNotNull( scheme ) ;
    this.hostPort = checkNotNull( hostPort ) ;
  }

  public Hostname hostname() {
    return hostPort.hostname ;
  }

  public String hostnameAsString() {
    return hostname().asString() ;
  }

  public int port() {
    return hostPort.port ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' + uriString() + '}' ;
  }

  public String uriString() {
    return scheme.ornated() + hostPort.asString() ;
  }

  public URI uri() {
    return UrxTools.parseUriQuiet( uriString() ) ;
  }

  public URL url() {
    return UrxTools.parseUrlQuiet( uriString() ) ;
  }


// ========
// Creation
// ========

  public static class CreationException extends RuntimeException {
    private CreationException( final String message ) {
      super( message ) ;
    }

    public CreationException( final String message, final Exception e ) {
      super( message, e ) ;
    }
  }

  public static class ParseException extends Exception {
    private ParseException( final String message ) {
      super( message ) ;
    }
  }

  public static SchemeHostPort parse( final String string ) throws ParseException {
    final SchemeHostPort parsed = parseOrNull( string ) ;
    if( parsed == null ) {
      throw new ParseException( "Can't parse '" + string + "'" ) ;
    } else {
      return parsed ;
    }
  }

  public static SchemeHostPort parseOrNull( final String string ) {
    checkNotNull( string ) ;
    for( final Scheme aScheme : Scheme.values() ) {
      final String ornated = aScheme.ornated() ;
      if( string.startsWith( ornated ) ) {
        final String candidateHostPort = string.substring( ornated.length() ) ;
        final Matcher matcher =
            InternetAddressValidator.hostWithOptionalPortMatcher( candidateHostPort ) ;
        if( matcher.matches() ) {
          final int port ;
          final String portInString = matcher.group( 2 ) ;
          if( portInString == null ) {
            if( aScheme.defaultPort == null ) {
              return null ;
            } else {
              port = aScheme.defaultPort ;
            }
          } else {
            port = Integer.parseInt( portInString ) ;
          }
          final HostPort hostPort = HostPort.create(
              new Hostname( matcher.group( 1 ), false ), port ) ;
          return new SchemeHostPort( aScheme, hostPort ) ;
        }
      }
    }
    return null ;
  }

  public static SchemeHostPort create(
      final String scheme,
      final String hostnameAsString,
      final int port
  ) {
    return create( Scheme.resolve( scheme ), hostnameAsString, port ) ;
  }

  public static SchemeHostPort create(
      final Scheme scheme,
      final HostPort hostPort
  ) {
    return new SchemeHostPort( scheme, hostPort ) ;
  }

  public static SchemeHostPort create(
      final Scheme scheme,
      final Hostname hostname,
      final int port
  ) {
    final HostPort hostPort ;
    try {
      hostPort = HostPort.create( hostname, port ) ;
    } catch( final Exception e ) {
      throw new CreationException( "Could not create " + SchemeHostPort.class.getSimpleName() +
          " with " + hostname + " and port " + port ) ;
    }
    return new SchemeHostPort( scheme, hostPort ) ;
  }


  public static SchemeHostPort create( final URL url ) {
    return create(
        SchemeHostPort.Scheme.resolve( url.getProtocol() ),
        url.getHost(),
        url.getPort()
    ) ;
  }
      
  public static SchemeHostPort create(
      final Scheme scheme,
      final String hostnameAsString,
      final int port
  ) {
    try {
      return create(
          scheme,
          Hostname.parse( hostnameAsString ),
          port <= 0 ? scheme.defaultPort : port
      ) ;
    } catch( final CreationException e ) {
      throw e ;
    } catch( final Exception e ) {
      throw new CreationException( "Could not create " + SchemeHostPort.class.getSimpleName() +
          " with hostname '" + hostnameAsString + "' and port " + port ) ;
    }
  }


// ================
// Bridging methods
// ================

  public URL asCanonicalUrl() {
    return asCanonicalUrlQuiet( "" ) ;
  }

  public URL asCanonicalUrlQuiet( final String path ) throws CreationException {
    final StringBuilder builder = new StringBuilder() ;
    builder.append( scheme.ornated() ) ;
    builder.append( hostnameAsString() ) ;
    if( scheme.defaultPort != port() ) {
      builder.append( ':' ) ;
      builder.append( port() ) ;
    }
    builder.append( path ) ;
    final String urlString = builder.toString() ;
    try {
      return new URL( urlString ) ;
    } catch( final MalformedURLException e ) {
      throw new CreationException( "Could not create URL from '" + urlString + "'", e ) ;
    }
  }


// ===========
// Equivalence
// ===========

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }
    final SchemeHostPort that = ( SchemeHostPort ) other ;
    return EQUIVALENCE.equivalent( this, that ) ;
  }

  @Override
  public int hashCode() {
    return EQUIVALENCE.hash( this ) ;
  }

  public static final Equivalence< SchemeHostPort > EQUIVALENCE =
      new Equivalence< SchemeHostPort >() {
        @Override
        @ParametersAreNonnullByDefault
        protected boolean doEquivalent( final SchemeHostPort first, final SchemeHostPort second ) {
          final boolean portEquivalent = first.scheme == second.scheme ;
          if( portEquivalent ) {
            final boolean hostPortEquivalent = first.hostPort.equals( second.hostPort ) ;
            return hostPortEquivalent ;
          } else {
            return false ;
          }
        }

        @ParametersAreNonnullByDefault
        @Override
        protected int doHash( final SchemeHostPort schemeHostPort ) {
          int result = schemeHostPort.scheme.hashCode() ;
          result = 31 * result + schemeHostPort.hostPort.hashCode() ;
          return result ;
        }
      }
  ;

}
