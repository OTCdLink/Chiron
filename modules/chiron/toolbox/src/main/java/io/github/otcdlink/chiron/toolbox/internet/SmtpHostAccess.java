package io.github.otcdlink.chiron.toolbox.internet;



import io.github.otcdlink.chiron.toolbox.Credential;

import java.util.regex.Matcher;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents user name, password, hostname and port.
 */
public class SmtpHostAccess {

  private final Credential credential;
  private final Hostname hostname ;
  private final int port ;

  public SmtpHostAccess( final Credential credential, final Hostname hostname, final int port ) {
    this.credential = checkNotNull( credential ) ;
    this.hostname = checkNotNull( hostname ) ;
    checkArgument( port > 0 ) ;
    this.port = port ;
  }

  public static SmtpHostAccess create(
      final String login,
      final String password,
      final String hostname,
      final int port
  ) throws HostAccessFormatException {
    return new SmtpHostAccess(
        new Credential( login, password ),
        Hostname.parse( hostname ),
        port
    ) ;
  }

  public static SmtpHostAccess parse( final String string ) throws HostAccessFormatException {
    final Matcher matcher = InternetAddressValidator.smtpHostAccessMatcher( string ) ;
    if( ! matcher.matches() ) {
      throw new HostAccessFormatException( string ) ;
    }
    final String loginName = matcher.group( 1 ) ;
    final String password = matcher.group( 2 ) ;
    final String rawHostname = matcher.group( 3 ) ;

    try {
      final Credential credential = new Credential( loginName, password ) ;
      final Hostname hostname = Hostname.parse( rawHostname ) ;
      final String rawPort = matcher.group( 4 ) ;
      final int port = Integer.parseInt( rawPort ) ;
      return new SmtpHostAccess( credential, hostname, port ) ;
    } catch( final Exception e ) {
      throw new HostAccessFormatException( string ) ;
    }
  }

  public Hostname getHostname() {
    return hostname ;
  }

  public Credential getCredential() {
    return credential;
  }

  public int getPort() {
    return port ;
  }

  public String asString() {
    return
        credential.getLogin() + ":" + credential.getPassword()
        + "@"
        + hostname.asString() + ":" + port
    ;
  }
}
