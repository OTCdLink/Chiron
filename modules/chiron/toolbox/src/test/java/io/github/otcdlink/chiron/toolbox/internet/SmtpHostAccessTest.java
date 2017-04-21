package io.github.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SmtpHostAccess}.
 */
public class SmtpHostAccessTest {

  @Test
  public void good1() throws HostAccessFormatException {
    final SmtpHostAccess smtpHostAccess = SmtpHostAccess.parse( "my.name:password@host:123" ) ;
    assertThat( smtpHostAccess.getCredential().getLogin() ).isEqualTo( "my.name" ) ;
    assertThat( smtpHostAccess.getCredential().getPassword() ).isEqualTo( "password" ) ;
    assertThat( smtpHostAccess.getHostname().asString() ).isEqualTo( "host" ) ;
    assertThat( smtpHostAccess.getPort() ).isEqualTo( 123 ) ;
  }

  @Test
  public void good2() throws HostAccessFormatException {
    final SmtpHostAccess smtpHostAccess
        = SmtpHostAccess.parse( "my.name@foo.bar:password@host:123" ) ;
    assertThat( smtpHostAccess.getCredential().getLogin() ).isEqualTo( "my.name@foo.bar" ) ;
    assertThat( smtpHostAccess.getCredential().getPassword() ).isEqualTo( "password" ) ;
    assertThat( smtpHostAccess.getHostname().asString() ).isEqualTo( "host" ) ;
    assertThat( smtpHostAccess.getPort() ).isEqualTo( 123 ) ;
  }

  @Test
  public void good3() throws HostAccessFormatException {
    final SmtpHostAccess smtpHostAccess
        = SmtpHostAccess.parse( "my.name@foo.bar:password@host:123" ) ;
    assertThat( smtpHostAccess.getCredential().getLogin() ).isEqualTo( "my.name@foo.bar" ) ;
    assertThat( smtpHostAccess.getCredential().getPassword() ).isEqualTo( "password" ) ;
    assertThat( smtpHostAccess.getHostname().asString() ).isEqualTo( "host" ) ;
    assertThat( smtpHostAccess.getPort() ).isEqualTo( 123 ) ;
  }

  @Test( expected = HostAccessFormatException.class )
  public void badHostname() throws HostAccessFormatException {
    SmtpHostAccess.parse( "name@host" ) ;
  }

}
