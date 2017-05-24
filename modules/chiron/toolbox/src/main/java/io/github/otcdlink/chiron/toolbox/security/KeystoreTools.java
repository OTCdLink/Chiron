package io.github.otcdlink.chiron.toolbox.security;

import com.google.common.base.Preconditions;
import io.github.otcdlink.chiron.toolbox.SafeSystemProperty;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public enum KeystoreTools { ;

  private static final Logger LOGGER = LoggerFactory.getLogger( KeystoreTools.class ) ;

  private static final boolean WEAKENED_SECURITY = false ;

  public static final String CLIENT_SSL_PROTOCOL = WEAKENED_SECURITY ? "SSLv3" : "TLSv1.2" ;

  public static final String CACERTS_PATH = File.separator + "lib" + File.separator +
      "security" + File.separator + "cacerts" ;

  public static KeyStore loadWithFallback( final URL url, final URL fallback )
      throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException
  {
    Preconditions.checkNotNull( fallback ) ;

    if( url == null ) {
      LOGGER.info( "Loading keystore/trustore from fallback " + fallback + " ..." ) ;
      return loadKeystore( fallback ) ;
    }
    else {
      return loadKeystore( url ) ;
    }
  }


  public static KeyStore loadKeystore( final File file )
      throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException
  {
    return loadKeystore( UrxTools.fromFileQuiet( file ) ) ;
  }

  public static KeyStore loadKeystore( final URL url )
      throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException
  {
    final KeyStore keyStore = KeyStore.getInstance( "JKS" ) ;
    try( final InputStream inputStream = UrxTools.getByteSource( url ).openStream() ) {
      keyStore.load( inputStream, null ) ;
      LOGGER.info( "Loaded keystore/truststore from '" + url + "'." ) ;
    }
    return keyStore ;
  }

  public static KeyStore truststoreFromJreCacerts()
      throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException
  {
    return loadKeystore( truststoreFromJreCacertsUrl() ) ;
  }

  /**
   * Useful when using a self-signed certificate, and we did set our own Keystore instead
   * of JRE's {@code cacerts} file.
   */
  public static URL truststoreFromJreCacertsUrl()
      throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException
  {
    final File cacertsFile = new File( SafeSystemProperty.Standard.JAVA_HOME.value, CACERTS_PATH ) ;
    return cacertsFile.toURI().toURL() ;
  }

  public static String keystoreToString( final KeyStore keyStore ) {
    return ToStringTools.getNiceClassName( keyStore )
        + "{" + keyStore.getType() + "}@" + System.identityHashCode( keyStore ) ;
  }

  /**
   * Returns a fresh array of {@code sun.security.ssl.CipherSuite} names, considered as secure
   * by https://www.ssllabs.com .
   */
  public static String[] getIncludedCipherSuites() {
    return ChironSecuritySystemProperties.WEAKENED_TLS.isSet() ?
        new String[]{
            // "SSL_RSA_WITH_NULL_MD5",     // Web browsers don't like it.
            "TLS_RSA_WITH_AES_128_CBC_SHA", // Only to makes Wireshark's life easier
        } :
        new String[] {
            // Recommended
            // https://community.qualys.com/blogs/securitylabs/2013/06/25/ssl-labs-deploying-forward-secrecy
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", // Requires JCE Unlimited Strength.

            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",

            // Others:
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
        }
    ;
  }

  /**
   * Prevent POODLE attack.
   */
  public static String[] getExcludedProtocols() {
    return new String[] { "SSLv3" } ;
  }


  /**
   * Returns which {@code CipherSuite} the client should use.
   *
   * @return a valid {@code CipherSuite} name.
   */
  public static String getClientCipherSuite() {
    return getIncludedCipherSuites()[ 0 ];
  }

  private static final AtomicBoolean JAVA_CRYPTOGRAPHY_EXTENSIONS_INSTALLED =
      new AtomicBoolean( false ) ;

  /**
   * Legal restrictions for cryptographic tools still applies. Ask your lawyer if you can call
   * this method that enables Java Cryptography Extensions.
   * Java 1.8.0_112 added some checks but nothing that couldn't be bypassed using this
   * <a href="http://stackoverflow.com/a/22492582/1923328" >hack</a>.
   */
  public static void activateJavaCryptographyExtensions() {
    if( JAVA_CRYPTOGRAPHY_EXTENSIONS_INSTALLED.compareAndSet( false, true ) ) {
      try {
        final Class< ? > jceSecurity = Class.forName( "javax.crypto.JceSecurity" ) ;
        final Class< ? > cryptoPermissions = Class.forName( "javax.crypto.CryptoPermissions" ) ;
        final Class< ? > cryptoAllPermission = Class.forName( "javax.crypto.CryptoAllPermission" ) ;

        final Field isRestrictedField = jceSecurity.getDeclaredField( "isRestricted" ) ;
        isRestrictedField.setAccessible( true ) ;
        final Field modifiersField = Field.class.getDeclaredField( "modifiers" ) ;
        modifiersField.setAccessible( true ) ;
        modifiersField.setInt( isRestrictedField,
            isRestrictedField.getModifiers() & ~Modifier.FINAL ) ;
        isRestrictedField.set( null, false ) ;

        final Field defaultPolicyField = jceSecurity.getDeclaredField( "defaultPolicy" ) ;
        defaultPolicyField.setAccessible( true ) ;
        final PermissionCollection defaultPolicy =
            ( PermissionCollection ) defaultPolicyField.get( null ) ;

        final Field perms = cryptoPermissions.getDeclaredField( "perms" ) ;
        perms.setAccessible( true ) ;
        ( ( Map< ?, ? > ) perms.get( defaultPolicy ) ).clear() ;

        final Field instance = cryptoAllPermission.getDeclaredField( "INSTANCE" ) ;
        instance.setAccessible( true ) ;
        defaultPolicy.add( ( Permission ) instance.get( null ) ) ;
        LOGGER.info( "Installed Java Cryptography Extension programmatically." ) ;
      } catch( final Exception ex ) {
        LOGGER.error( "Could not force Java Cryptography Extension", ex ) ;
        throw new RuntimeException( ex ) ;
      }
    }
  }
}
