package com.otcdlink.chiron.toolbox.security;

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

  /**
   * We had to adjust some reflexion hack following to a Java update, so we check it still works.
   */
  @Test
  public void activateCryptographyExtensions() throws Exception {
    KeystoreTools.activateJavaCryptographyExtensions() ;
  }
}