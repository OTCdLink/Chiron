package io.github.otcdlink.chiron.toolbox.internet;

import com.google.common.base.Preconditions;
import io.github.otcdlink.chiron.toolbox.Credential;

import java.util.regex.Matcher;

/**
 * Represents user name, password, hostname and port.
 *
 * TODO: use {@link HostPort} and {@link Credential} object.
 */
public class UpendAccess {

  private final String loginName ;
  private final String password ;
  private final SchemeHostPort schemeHostPort;

  public UpendAccess(
      final String loginName,
      final String password,
      final SchemeHostPort schemeHostPort
  ) {
    this.loginName = loginName ;
    this.password = password ;
    this.schemeHostPort = Preconditions.checkNotNull( schemeHostPort ) ;
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' +
        "login:" + loginName + ';' +
        "httpHost:" + schemeHostPort().uriString() +
    '}' ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final UpendAccess that = ( UpendAccess ) other ;

    if( loginName != null ? !loginName.equals( that.loginName ) : that.loginName != null ) {
      return false ;
    }
    if( password != null ? !password.equals( that.password ) : that.password != null ) {
      return false ;
    }
    return schemeHostPort.equals( that.schemeHostPort ) ;

  }

  @Override
  public int hashCode() {
    int result = loginName != null ? loginName.hashCode() : 0 ;
    result = 31 * result + ( password != null ? password.hashCode() : 0 ) ;
    result = 31 * result + schemeHostPort.hashCode() ;
    return result ;
  }

  public UpendAccess( final String loginOnHost ) throws HostAccessFormatException {
    final Matcher matcher = InternetAddressValidator.cometdHostAccessMatcher( loginOnHost ) ;
    if( ! matcher.matches() ) {
      throw new HostAccessFormatException( loginOnHost ) ;
    } ;
    final String scheme = matcher.group( 1 ) ;
    loginName = matcher.group( 2 ) ;
    password = matcher.group( 3 ) ;
    final String hostname = matcher.group( 4 ) ;
    final int port ;
    {
      final String portAsString = matcher.group( 5 ) ;
      if( portAsString == null ) {
        port = InternetHostNameTools.defaultPortForScheme( scheme );
      } else {
        try {
          port = Integer.parseInt( portAsString );
        } catch( NumberFormatException e ) {
          throw new HostAccessFormatException( loginName ) ;
        }
      }
    }

    this.schemeHostPort = SchemeHostPort.create( scheme, hostname, port ) ;
  }

  public SchemeHostPort schemeHostPort() {
    return schemeHostPort ;
  }

  public boolean hasSecureScheme() {
    return schemeHostPort.scheme.secure
    ;
  }

  /**
   * @return a possibly null object.
   */
  public String login() {
    return loginName ;
  }

  /**
   * @return a possibly null object.
   */
  public String password() {
    return password ;
  }

}
