package com.otcdlink.chiron.toolbox.internet;

import com.google.common.base.Equivalence;
import com.otcdlink.chiron.toolbox.Credential;
import com.otcdlink.chiron.toolbox.DynamicProperty;
import com.otcdlink.chiron.toolbox.SafeSystemProperty;
import com.otcdlink.chiron.toolbox.ToStringTools;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Connection parameters for an Internet proxy.
 * An {@link InternetProxyAccess} instance defines how to establish a WebSocket connection
 * when using a proxy. It performs no dynamic resolution depending on the target address.
 *
 * @see java.net.Proxy doesn't support login and password, and Netty doesn't know about it.
 */
public final class InternetProxyAccess {

  public final Kind kind ;
  public final HostPort hostPort;
  public final Credential credential ;

  public InternetProxyAccess( final Kind kind, final HostPort hostPort ) {
    this( kind, hostPort, null ) ;
  }

  public InternetProxyAccess(
      final Kind kind,
      final HostPort hostPort,
      final Credential credential
  ) {
    this.kind = checkNotNull( kind ) ;
    this.hostPort = checkNotNull( hostPort ) ;
    this.credential = credential ;
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + '{' + asString() + '}' ;
  }

  public String asString() {
    return
        kind.name().toLowerCase() + "://" +
        ( credential == null ? "" : credential.getLogin() + ":******" + '@' ) +
        hostPort.asString()
    ;
  }

  /**
   * Helps to chose between System Properties.
   */
  public enum Family {
    HTTP, HTTPS, SOCKS, ;
  }

  public enum Kind {
    HTTP,
    HTTPS,
    SOCKS_V4,
    SOCKS_V5,
    ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final InternetProxyAccess that = ( InternetProxyAccess ) other ;

    return EQUIVALENCE.equivalent( this, that ) ;
  }

  @Override
  public int hashCode() {
    return EQUIVALENCE.hash( this ) ;
  }

  public static final Equivalence< InternetProxyAccess > EQUIVALENCE =
      new Equivalence< InternetProxyAccess >()
  {
    @Override
    @ParametersAreNonnullByDefault
    protected boolean doEquivalent(
        final InternetProxyAccess first,
        final InternetProxyAccess second
    ) {
      if( first.kind == second.kind ) {
        if( first.hostPort.equals( second.hostPort ) ) {
          if( first.credential.equals( second.credential ) ) {
            return true ;
          } else {
            return false ;
          }
        } else {
          return false ;
        }
      } else {
        return false ;
      }
    }

    @Override
    @ParametersAreNonnullByDefault
    protected int doHash( final InternetProxyAccess internetProxyAccess ) {
      int result = internetProxyAccess.kind.hashCode() ;
      result = 31 * result + internetProxyAccess.hostPort.hashCode() ;
      result = 31 * result + ( internetProxyAccess.credential != null ?
          internetProxyAccess.credential.hashCode() : 0 ) ;
      return result ;
    }
  } ;

  /**
   * http://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Proxies
   */
  private interface RawSystemProperties {
    SafeSystemProperty.HostnameType HTTP_PROXY_HOST =
        SafeSystemProperty.forHostname( "http.proxyHost" ) ;

    SafeSystemProperty.IntegerType HTTP_PROXY_PORT =
        SafeSystemProperty.forStrictlyPositiveInteger( "http.proxyPort" ) ;

    SafeSystemProperty.HostnameType HTTPS_PROXY_HOST =
        SafeSystemProperty.forHostname( "https.proxyHost" ) ;

    SafeSystemProperty.IntegerType HTTPS_PROXY_PORT =
        SafeSystemProperty.forStrictlyPositiveInteger( "https.proxyPort" ) ;

    SafeSystemProperty.HostnameType SOCKS_PROXY_HOST =
        SafeSystemProperty.forHostname( "socksProxyHost" ) ;

    SafeSystemProperty.IntegerType SOCKS_PROXY_PORT =
        SafeSystemProperty.forStrictlyPositiveInteger( "socksProxyPort" ) ;

    SafeSystemProperty.StringType SOCKS_PROXY_VERSION =
        SafeSystemProperty.forString( "socksProxyVersion" ) ;

    SafeSystemProperty.StringType SOCKS_USERNAME =
        SafeSystemProperty.forString( "java.net.socks.username" ) ;

    SafeSystemProperty.StringType SOCKS_PASSWORD =
        SafeSystemProperty.forString( "java.net.socks.password" ) ;
  }

  public interface SafeSystemProperties {

    /**
     * Aggregates a {@link SafeSystemProperty.HostnameType} and a
     * {@link SafeSystemProperty.StrictlyPositiveIntegerType} to obtain a {@link Hostname}.
     * This implementation sticks to Java's convention for defining proxies: host and port are
     * two different properties.
     */
    class HostPortType implements DynamicProperty< HostPortType,HostPort > {

      private final SafeSystemProperty.HostnameType hostnameProperty ;
      private final SafeSystemProperty.IntegerType portProperty ;

      private HostPortType(
          final SafeSystemProperty.HostnameType hostnameProperty,
          final SafeSystemProperty.IntegerType portProperty
      ) {
        this.hostnameProperty = checkNotNull( hostnameProperty ) ;
        this.portProperty = checkNotNull( portProperty ) ;
      }

      @Override
      public HostPort valueOrDefault( final HostPort defaultValue ) {
        final Hostname hostname = hostnameProperty.valueOrDefault( null ) ;
        // It's tempting to use Squid default port (3128) but this could get messy soon.
        final Integer port = portProperty.value ;
        if( hostname != null && port != null ) {
          return HostPort.create( hostname, port ) ;
        } else {
          return defaultValue ;
        }
      }

      @Override
      public HostPortType reload() {
        return new HostPortType( hostnameProperty.reload(), portProperty.reload() ) ;
      }
    }

    HostPortType HTTP_PROXY_HOST_PORT = new HostPortType(
        RawSystemProperties.HTTP_PROXY_HOST, RawSystemProperties.HTTP_PROXY_PORT ) ;

    HostPortType HTTPS_PROXY_HOST_PORT = new HostPortType(
        RawSystemProperties.HTTPS_PROXY_HOST, RawSystemProperties.HTTPS_PROXY_PORT ) ;

  }


  /**
   *
   * @return {@code null} if there are non consistent System Properties for given {@link Family}.
   */
  public static InternetProxyAccess fromSystemProperties( final Family family ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public static InternetProxyAccess httpMaybe(
      final boolean useTls,
      final HostPort hostPort
  ) {
    if( hostPort == null ) {
      return null ;
    } else {
      return new InternetProxyAccess(
          useTls ? Kind.HTTPS : Kind.HTTP,
          hostPort,
          null
      ) ;
    }
  }

}
