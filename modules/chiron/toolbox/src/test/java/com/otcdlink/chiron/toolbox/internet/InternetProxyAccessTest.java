package com.otcdlink.chiron.toolbox.internet;

import com.otcdlink.chiron.toolbox.Credential;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternetProxyAccessTest {

  @Test
  public void noCredential() throws Exception {
    final InternetProxyAccess internetProxyAccess =
        new InternetProxyAccess( InternetProxyAccess.Kind.HTTP, HostPort.parse( "foo:999" ) ) ;
    assertThat( internetProxyAccess.credential ).isNull() ;
    assertThat( internetProxyAccess.hostPort.asString() ).isEqualTo( "foo:999" ) ;
    assertThat( internetProxyAccess.kind ).isEqualTo( InternetProxyAccess.Kind.HTTP ) ;
    assertThat( internetProxyAccess.toString() )
        .isEqualTo( "InternetProxyAccess{http://foo:999}" ) ;

  }
  @Test
  public void withCredential() throws Exception {
    final InternetProxyAccess internetProxyAccess = new InternetProxyAccess(
        InternetProxyAccess.Kind.SOCKS_V5,
        HostPort.parse( "foo:999" ),
        Credential.maybeCreate( "scott", "tiger" )
    ) ;
    assertThat( internetProxyAccess.credential.getLogin() ).isEqualTo( "scott" ) ;
    assertThat( internetProxyAccess.credential.getPassword() ).isEqualTo( "tiger" ) ;
    assertThat( internetProxyAccess.hostPort.asString() ).isEqualTo( "foo:999" ) ;
    assertThat( internetProxyAccess.kind ).isEqualTo( InternetProxyAccess.Kind.SOCKS_V5 ) ;
    assertThat( internetProxyAccess.toString() )
        .isEqualTo( "InternetProxyAccess{socks_v5://scott:******@foo:999}" ) ;

  }
}