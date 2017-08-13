package com.otcdlink.chiron.toolbox.security;


import com.google.common.collect.ImmutableList;
import org.fest.reflect.core.Reflection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSslEngineFactoryTest {

  @Test
  public void onlyAliasAskedFor() throws Exception {
    final String alias = "caxx" ;
    final DefaultSslEngineFactory.ForServer sslEngineFactory =
        new DefaultSslEngineFactory.ForServer(
            KeystoreFixture.SELFISH_KEYSTORE.keystoreAccess.load(),
            alias,
            KeystoreFixture.SELFISH_KEYSTORE.keypass
        )
    ;
    final SSLEngine sslEngine = sslEngineFactory.newSslEngine() ;

    final SSLContextSpi sslContextSpi = Reflection.field( "sslContext" )
        .ofType( sun.security.ssl.SSLContextImpl.class ).in( sslEngine ).get() ;

    LOGGER.info( SSLContextSpi.class.getSimpleName() + ": " + sslContextSpi + "." ) ;

    final X509ExtendedKeyManager keyManager = Reflection.field( "keyManager" )
        .ofType( X509ExtendedKeyManager.class ).in( sslContextSpi ).get() ;

    LOGGER.info( X509ExtendedKeyManager.class.getSimpleName() + ": " + keyManager + "." ) ;

    final String[] serverAliases = keyManager.getServerAliases( "RSA", null ) ;
    LOGGER.info( "Server aliases: " +
        ( serverAliases == null ? null : ImmutableList.copyOf( serverAliases ) ) + "." ) ;

    assertThat( serverAliases ).hasSize( 1 ) ;
    assertThat( serverAliases ).containsExactly( alias ) ;

  }

// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( DefaultSslEngineFactoryTest.class ) ;

}