package com.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SmtpHostAccess}.
 */
public class UpendAccessTest {

  @Test
  public void everything() throws HostAccessFormatException {
    final UpendAccess upendAccess
        = new UpendAccess( "https://my.name:password@host:123" ) ;
    assertThat( upendAccess.login() ).isEqualTo( "my.name" ) ;
    assertThat( upendAccess.password() ).isEqualTo( "password" ) ;
    assertThat( upendAccess.schemeHostPort().scheme ).isEqualTo( SchemeHostPort.Scheme.HTTPS ) ;
    assertThat( upendAccess.schemeHostPort().hostnameAsString() ).isEqualTo( "host" ) ;
    assertThat( upendAccess.schemeHostPort().port() ).isEqualTo( 123 ) ;
  }

  @Test
  public void noCredential() throws HostAccessFormatException {
    final UpendAccess upendAccess
        = new UpendAccess( "http://myhost:123" ) ;
    assertThat( upendAccess.login() ).isEqualTo( null ) ;
    assertThat( upendAccess.password() ).isEqualTo( null ) ;
    assertThat( upendAccess.schemeHostPort().scheme ).isEqualTo( SchemeHostPort.Scheme.HTTP ) ;
    assertThat( upendAccess.schemeHostPort().hostnameAsString() ).isEqualTo( "myhost" ) ;
    assertThat( upendAccess.schemeHostPort().port() ).isEqualTo( 123 ) ;
  }

  @Test
  public void implicitHttpPort() throws HostAccessFormatException {
    final UpendAccess upendAccess
        = new UpendAccess( "http://myhost" ) ;
    assertThat( upendAccess.login() ).isEqualTo( null ) ;
    assertThat( upendAccess.password() ).isEqualTo( null ) ;
    assertThat( upendAccess.schemeHostPort().scheme ).isEqualTo( SchemeHostPort.Scheme.HTTP ) ;
    assertThat( upendAccess.schemeHostPort().hostnameAsString() ).isEqualTo( "myhost" ) ;
    assertThat( upendAccess.schemeHostPort().port() ).isEqualTo( 80 ) ;
  }

  @Test
  public void implicitHttpsPort() throws HostAccessFormatException {
    final UpendAccess upendAccess
        = new UpendAccess( "https://myhost" ) ;
    assertThat( upendAccess.login() ).isEqualTo( null ) ;
    assertThat( upendAccess.password() ).isEqualTo( null ) ;
    assertThat( upendAccess.schemeHostPort().scheme ).isEqualTo( SchemeHostPort.Scheme.HTTPS ) ;
    assertThat( upendAccess.schemeHostPort().hostnameAsString() ).isEqualTo( "myhost" ) ;
    assertThat( upendAccess.schemeHostPort().port() ).isEqualTo( 443 ) ;
  }

  @Test
  public void loginName() throws HostAccessFormatException {
    final UpendAccess upendAccess
        = new UpendAccess( "https://my.name@myhost:123" ) ;
    assertThat( upendAccess.login() ).isEqualTo( "my.name" ) ;
    assertThat( upendAccess.password() ).isEqualTo( null ) ;
    assertThat( upendAccess.schemeHostPort().scheme ).isEqualTo( SchemeHostPort.Scheme.HTTPS ) ;
    assertThat( upendAccess.schemeHostPort().hostnameAsString() ).isEqualTo( "myhost" ) ;
    assertThat( upendAccess.schemeHostPort().port() ).isEqualTo( 123 ) ;
  }

  @Test( expected = HostAccessFormatException.class )
  public void badHostname() throws HostAccessFormatException {
    SmtpHostAccess.parse( "http://name@host" ) ;
  }

}
