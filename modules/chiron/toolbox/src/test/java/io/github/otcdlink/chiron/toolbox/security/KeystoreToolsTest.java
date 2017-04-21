package io.github.otcdlink.chiron.toolbox.security;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.net.URL;

public final class KeystoreToolsTest {

  /**
   * Not a very good test because things may be so different after a deployment on Windows.
   */
  @Test
  public void loadCacerts() throws Exception {
    final URL url = KeystoreTools.truststoreFromJreCacertsUrl() ;
    Assertions.assertThat( KeystoreTools.loadKeystore( url ) ).isNotNull() ;
  }

}