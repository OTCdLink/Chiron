package com.otcdlink.chiron.integration.drill;

import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.toolbox.internet.HostPort;
import com.otcdlink.chiron.toolbox.internet.Hostname;
import com.otcdlink.chiron.toolbox.internet.InternetProxyAccess;
import com.otcdlink.chiron.toolbox.internet.SchemeHostPort;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public final class InternetAddressPack {

  private final SchemeHostPort upendListenSchemeHostPort ;
  private final URI upendListenFullUri ;
  private final URI upendListenRelativeUri ;
  private final URL upendListenUrlHttp ;
  private final URL upendListenUrlWs ;

  private final InternetProxyAccess internetProxyAccess ;

  private InternetAddressPack(
      final SchemeHostPort upendListenSchemeHostPort,
      final String path,
      final Integer proxyPort
  ) {
    this.upendListenSchemeHostPort = checkNotNull( upendListenSchemeHostPort ) ;
    this.upendListenFullUri = UrxTools.parseUriQuiet(
        upendListenSchemeHostPort.scheme.nameLowerCase() + "://" +
            upendListenSchemeHostPort.hostnameAsString() + ":" + upendListenSchemeHostPort.port() +
            path
    ) ;
    this.upendListenRelativeUri = UrxTools.parseUriQuiet( path ) ;
    upendListenUrlHttp = UrxTools.toUrlQuiet( upendListenFullUri ) ;
    upendListenUrlWs = UrxTools.websocketUrlQuiet(
        upendListenSchemeHostPort.scheme.secure, upendListenSchemeHostPort.hostPort, path ) ;

    if( proxyPort == null ) {
      internetProxyAccess = null ;
    } else {
      internetProxyAccess = new InternetProxyAccess(
          upendListenSchemeHostPort.scheme.secure ?
              InternetProxyAccess.Kind.HTTP :
              InternetProxyAccess.Kind.HTTPS
          ,
          HostPort.createForLocalhost( proxyPort )
      );
    }
  }

  public static final class CreationException extends RuntimeException {
    public CreationException( final String message ) {
      super( message ) ;
    }
  }

  public static InternetAddressPack newOnLocalhost(
      final boolean tls,
      final int port,
      final String path,
      final Integer proxyPort
  ) throws CreationException {
    return newFrom(
        SchemeHostPort.create(
            tls ? SchemeHostPort.Scheme.HTTPS : SchemeHostPort.Scheme.HTTP,
            Hostname.LOCALHOST,
            port
        ),
        path,
        proxyPort
    ) ;
  }
  public static InternetAddressPack newFrom(
      final SchemeHostPort schemeHostPort,
      final String path,
      final Integer proxyPort
  ) throws CreationException {
    try {
      return new InternetAddressPack( schemeHostPort, path, proxyPort ) ;
    } catch( Exception e ) {
      throw new CreationException(
          "Could not create from (" + schemeHostPort + ", '" + path + "'): " + e.getMessage() ) ;
    }
  }

// ==================
// Semantic accessors
// ==================

  public InetSocketAddress upendListeningSocketAddress() {
    return upendListenSchemeHostPort.hostPort.asInetSocketAddressQuiet() ;
  }

  public InetAddress upendListeningHostAddress() {
    return upendListenSchemeHostPort.hostPort.asInetAddressQuiet() ;
  }

  public int upendListeningPort() {
    return upendListenSchemeHostPort.hostPort.port ;
  }

  public URI upendWebSocketUri() {
    return upendListenRelativeUri ;
  }

  public String upendWebSocketUriPath() {
    return upendListenRelativeUri.toASCIIString() ;
  }

  public URL upendWebSocketUrlWithHttpScheme() {
    return upendListenUrlHttp ;
  }

  public URI upendWebSocketUriWithHttpScheme() {
    return upendListenFullUri ;
  }

  public URL upendWebSocketUrlWithWsScheme() {
    return upendListenUrlWs ;
  }

  public URL upendMalformedUrl() {
    return UrxTools.derive( upendWebSocketUrlWithHttpScheme(), "~/passwords.txt" ) ;
  }

  public InternetProxyAccess internetProxyAccess() {
    return internetProxyAccess ;
  }

  // ======
// Boring
// ======

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + upendListenUrlHttp + "}" ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }
    final InternetAddressPack that = ( InternetAddressPack ) other ;
    return Objects.equals( upendListenUrlHttp, that.upendListenUrlHttp ) ;
  }

  @Override
  public int hashCode() {
    return Objects.hash( upendListenUrlHttp ) ;
  }


}
