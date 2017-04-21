package io.github.otcdlink.chiron.toolbox.security;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public abstract class DefaultSslEngineFactory implements SslEngineFactory {

  private static final String PROTOCOL = "TLS" ;
  private static final String KEY_ALGORITHM = "SunX509" ;
  private static final char[] EMPTY_CHAR_ARRAY = new char[ 0 ] ;
  protected final SSLContext sslContext ;

  private DefaultSslEngineFactory() throws SSLException {
    try {
      sslContext = SSLContext.getInstance( PROTOCOL ) ;
    } catch( final NoSuchAlgorithmException e ) {
      throw new SSLException( e ) ;
    }
  }

  protected abstract boolean isClient() ;

  @Override
  public SSLEngine newSslEngine() {
    final SSLEngine sslEngine = sslContext.createSSLEngine() ;
    sslEngine.setUseClientMode( isClient() ) ;
    sslEngine.setEnabledCipherSuites( KeystoreTools.getIncludedCipherSuites() ) ;
    return sslEngine ;
  }



// =========
// ForServer
// =========

  public static final class ForServer extends DefaultSslEngineFactory
      implements SslEngineFactory.ForServer
  {

    public ForServer( final KeystoreAccess keystoreAccess ) throws SSLException {
      this( loadQuiet( keystoreAccess ), keystoreAccess.alias, keystoreAccess.password ) ;
    }

    public ForServer( final KeyStore keyStore, final String alias, final String keypass )
        throws SSLException
    {
      try {
        final KeyManagerFactory keyManagerFactory =
            createAndFixKeyManagers( keyStore, alias, keypass ) ;
        sslContext.init(
            keyManagerFactory.getKeyManagers(),
            null,
            null
        ) ;
      } catch( final NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException |
          KeyManagementException e )
      {
        throw new SSLException( e ) ;
      } catch( final GeneralSecurityException e ) {
        throw new RuntimeException( e ) ;
      }
    }

    @Override
    protected boolean isClient() {
      return false ;
    }
  }


  /**
   * Inspired by
   * http://www.programcreek.com/java-api-examples/index.php?source_dir=teiid-designer-master/plugins/teiid/org.teiid.runtime.client/client/org/teiid/net/socket/SocketUtil.java
   */
  private static KeyManagerFactory createAndFixKeyManagers(
      final KeyStore keyStore,
      final String alias,
      final String keystorePassword
  ) throws GeneralSecurityException {
    final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance( KEY_ALGORITHM ) ;
    final char[] passwordChars = keystorePassword == null ?
        EMPTY_CHAR_ARRAY : keystorePassword.toCharArray() ;
    keyManagerFactory.init( keyStore, passwordChars ) ;

    final KeyManager[] keyManagers = keyManagerFactory.getKeyManagers() ;
    final ImmutableList.Builder< KeyManager > builder = ImmutableList.builder() ;
    if( alias != null ) {
      if( ! keyStore.isKeyEntry( alias ) ) {
        throw new GeneralSecurityException( "No alias '" + alias + "'" ) ;
      }
      for( final KeyManager keyManager : keyManagers ) {
        final X509ExtendedKeyManager x509ExtendedKeyManager =
            ( X509ExtendedKeyManager ) keyManager;
        if( x509ExtendedKeyManager.getCertificateChain( alias ) != null ) {
          if( keyManager instanceof X509ExtendedKeyManager ) {
            if( keyManager instanceof AliasAwareKeyManager ) {
              builder.add( x509ExtendedKeyManager );
            } else {
              builder.add( new AliasAwareKeyManager(
                  x509ExtendedKeyManager, alias ) );
            }
          }
        }
      }
    }

    // Need a new instance, original implementation returned a defensive copy of the array.
    return new DelegatingKeyManagerFactory( keyManagerFactory, keyStore, builder.build() ) ;
  }


  /**
   * Tweak behavior of {@link #getKeyManagers()} to return a given array.
   */
  private static class DelegatingKeyManagerFactory extends KeyManagerFactory {
    public DelegatingKeyManagerFactory(
        final KeyManagerFactory keyManagerFactory,
        final KeyStore keyStore,
        final ImmutableList< KeyManager > keyManagers
    ) {
      super(
          new KeyManagerFactorySpi() {
            @Override
            protected void engineInit( final KeyStore keystore, final char[] password )
                throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException
            {
              keyManagerFactory.init( keyStore, password ) ;
            }

            @Override
            protected void engineInit(
                final ManagerFactoryParameters managerFactoryParameters
            ) throws InvalidAlgorithmParameterException {
              keyManagerFactory.init( managerFactoryParameters ) ;
            }

            @Override
            protected KeyManager[] engineGetKeyManagers() {
              return keyManagers.toArray( new KeyManager[ keyManagers.size() ]) ;
            }
          },
          keyManagerFactory.getProvider(),
          DefaultSslEngineFactory.KEY_ALGORITHM
      ) ;
    }
  }


  /**
   * Shortcut heuristics for choosing best alias, just return the one we set explicitely.
   * Made package-protected for tests.
   * Inspired by:
   * http://www.programcreek.com/java-api-examples/index.php?source_dir=teiid-designer-master/plugins/teiid/org.teiid.runtime.client/client/org/teiid/net/socket/SocketUtil.java
   */
   static class AliasAwareKeyManager extends X509ExtendedKeyManager {
    private final X509ExtendedKeyManager delegate ;
    private final String keyAlias ;

    public AliasAwareKeyManager( final X509ExtendedKeyManager delegate, final String alias ) {
      this.delegate = delegate ;
      this.keyAlias = alias ;
    }

    @Override
    public String chooseClientAlias(
        final String[] keyType,
        final Principal[] issuers,
        final Socket socket
    ) {
      return keyAlias ;
    }

    @Override
    public String chooseServerAlias(
        final String keyType,
        final Principal[] issuers,
        final Socket socket
    ) {
      return keyAlias ;
    }

    @Override
    public X509Certificate[] getCertificateChain( final String alias ) {
      return keyAlias.endsWith( alias ) ? delegate.getCertificateChain( alias ) : null ;
    }

    @Override
    public String[] getClientAliases( final String keyType, final Principal[] issuers ) {
      return new String[] { keyAlias } ;
    }

    @Override
    public PrivateKey getPrivateKey( final String alias ) {
      return keyAlias.equals( alias ) ? delegate.getPrivateKey( alias ) : null ;
    }

    @Override
    public String[] getServerAliases( final String keyType, final Principal[] issuers ) {
      return new String[] { keyAlias } ;
    }


    @Override
    public String chooseEngineClientAlias(
        final String[] keyType,
        final Principal[] issuers,
        final SSLEngine engine
    ) {
      return keyAlias ;
    }

    @Override
    public String chooseEngineServerAlias(
        final String keyType,
        final Principal[] issuers,
        final SSLEngine engine
    ) {
      return keyAlias ;
    }
  }


// =========
// ForClient
// =========

  public static final class ForClient extends DefaultSslEngineFactory
      implements SslEngineFactory.ForClient
  {


    public ForClient( final File truststoreFile )
        throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException
    {
      this( KeystoreTools.loadKeystore( truststoreFile ) ) ;
    }

    public ForClient( final KeyStore truststore ) throws SSLException {
      try {
        final TrustManagerFactory trustManagerFactory =
            TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() ) ;
        trustManagerFactory.init( truststore ) ;
        sslContext.init( null , trustManagerFactory.getTrustManagers(), null ) ;
      } catch( final NoSuchAlgorithmException | KeyStoreException | KeyManagementException e ) {
        throw new SSLException( e ) ;
      }
    }

    public static SslEngineFactory.ForClient createQuiet( final KeystoreAccess truststoreAccess ) {
      if( truststoreAccess == null ) {
        return null ;
      } else {
        try {
          return createQuiet( truststoreAccess.load() ) ;
        } catch( CertificateException | NoSuchAlgorithmException | KeyStoreException e ) {
          throw Throwables.propagate( e ) ;
        }
      }
    }

    public static SslEngineFactory.ForClient createQuiet( final KeyStore trustStore ) {
      if( trustStore == null ) {
        return null ;
      } else {
        try {
          return new DefaultSslEngineFactory.ForClient( trustStore ) ;
        } catch( final SSLException e ) {
          throw Throwables.propagate( e ) ;
        }
      }
    }

    @Override
    protected boolean isClient() {
      return true ;
    }
  }

// =================
// Utilities for all
// =================

  private static KeyStore loadQuiet( final KeystoreAccess keystoreAccess ) throws SSLException {
    try {
      return keystoreAccess.load();
    } catch( CertificateException | NoSuchAlgorithmException | KeyStoreException e ) {
      throw new SSLException( e ) ;
    }
  }

}
