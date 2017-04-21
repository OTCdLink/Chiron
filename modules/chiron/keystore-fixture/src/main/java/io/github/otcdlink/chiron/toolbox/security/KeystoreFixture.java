package io.github.otcdlink.chiron.toolbox.security;

import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public enum KeystoreFixture {

  EMPTY( "empty.jks", "-" ),
  EMPTY_2( "empty2.jks", "-" ),

  SELFISH_KEYSTORE ( "my-keystore.jks", "e1" ),
  SELFISH_TRUSTSTORE ( "my-truststore.jks", "ca2" ),
  ;

  private static final Logger LOGGER = LoggerFactory.getLogger( KeystoreFixture.class ) ;


  public final URL url ;
  public final String alias ;
  public final KeystoreAccess keystoreAccess ;


  KeystoreFixture( final String resourceName, final String alias ) {
    this.alias = alias ;
    this.url = Resources.getResource( KeystoreFixture.class, resourceName ) ;
    try {
      this.keystoreAccess = KeystoreAccess.parse( keypass, alias, url ) ;
    } catch( final KeystoreAccessFormatException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public KeyStore load() {
    try( final InputStream inputStream = url.openStream() ) {
      final KeyStore keyStore = KeyStore.getInstance( "JKS" ) ;
      keyStore.load( inputStream, null ) ;
      LOGGER.info( "Loaded keystore from '" + url.toExternalForm() + "'" );
      return keyStore ;
    } catch( IOException | CertificateException | NoSuchAlgorithmException | KeyStoreException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public final String storepass = "Storepass" ;
  public final String keypass = "Keypass" ;




}
