package com.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class LocalAddressToolsTest {
  @Test
  public void localhostAddressDetection() throws Exception {
    assertThat( LocalAddressTools.isLocalhost( "localhost" ) ).isTrue() ;
    assertThat( LocalAddressTools.isLocalhost( "127.0.0.1" ) ).isTrue() ;
    assertThat( LocalAddressTools.isLocalhost( "127.0.0.2" ) ).isTrue() ;

    assertThat( LocalAddressTools.isLocalhost( "" ) ).isFalse() ;
    assertThat( LocalAddressTools.isLocalhost( "128.0.0.1" ) ).isFalse() ;
    assertThat( LocalAddressTools.isLocalhost( "foo" ) ).isFalse() ;
    assertThat( LocalAddressTools.isLocalhost( "foo.bar" ) ).isFalse() ;
    assertThat( LocalAddressTools.isLocalhost( "local.host" ) ).isFalse() ;

  }
}