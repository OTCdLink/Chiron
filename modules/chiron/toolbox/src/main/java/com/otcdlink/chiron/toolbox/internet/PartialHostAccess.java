package com.otcdlink.chiron.toolbox.internet;

import java.util.regex.Matcher;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents user name, hostname and port, no password.
 */
public class PartialHostAccess {

  private final String login;
  private final Hostname hostname ;
  private final int port ;

  public PartialHostAccess( final String login, final Hostname hostname, final int port ) {
    this.login = checkNotNull( login ) ;
    this.hostname = checkNotNull( hostname ) ;
    checkArgument( port > 0 ) ;
    this.port = port ;
  }

  public static PartialHostAccess create(
      final String login,
      final String hostname,
      final int port
  ) throws HostAccessFormatException {
    return new PartialHostAccess( login, Hostname.parse( hostname ), port ) ;
  }

  public static PartialHostAccess parse( final String string, final int defaultPort )
      throws HostAccessFormatException
  {
    checkArgument( defaultPort > 0 ) ;
    final Matcher matcher = InternetAddressValidator.partialHostAccessMatcher( string ) ;
    if( ! matcher.matches() ) {
      throw new HostAccessFormatException( string ) ;
    }
    final String loginName = matcher.group( 1 ) ;
    final String rawHostname = matcher.group( 2 ) ;

    try {
      final String rawPort = matcher.group( 3 ) ;
      final int port ;
      if( rawPort == null ) {
        port = defaultPort ;
      } else {
        port = Integer.parseInt( rawPort ) ;
      }
      return new PartialHostAccess( loginName, Hostname.parse( rawHostname ), port ) ;
    } catch( final Exception e ) {
      throw new HostAccessFormatException( string ) ;
    }
  }

  public Hostname getHostname() {
    return hostname ;
  }

  public String getLogin() {
    return login ;
  }

  public int getPort() {
    return port ;
  }

  public String asString() {
    return
        getLogin() +
        "@" +
        hostname.asString() + ":" + port
    ;
  }
}
