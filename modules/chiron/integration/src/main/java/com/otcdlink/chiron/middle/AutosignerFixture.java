package com.otcdlink.chiron.middle;

import com.otcdlink.chiron.toolbox.concurrent.Lazy;
import com.otcdlink.chiron.toolbox.internet.LocalAddressTools;
import com.otcdlink.chiron.toolbox.security.Autosigner;
import com.otcdlink.chiron.toolbox.security.DefaultSslEngineFactory;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;

import javax.net.ssl.SSLException;

public final class AutosignerFixture {

  private AutosignerFixture() { }

  /**
   * The value held by the {@link Lazy} is immutable, so it can be shared across tests.
   */
  private static final Lazy< Autosigner.CertificateHolder > AUTOSIGNER = new Lazy<>( () -> {
    try {
      return Autosigner.builder()
          .commonNameAndDefaults( LocalAddressTools.LOCALHOST_HOSTNAME.asString() )
          .createCertificateWithDefaults()
      ;
    } catch( Autosigner.CertificateCreationException e ) {
      throw new RuntimeException( e ) ;
    }
  } ) ;

  public static SslEngineFactory.ForClient sslEngineFactoryForClient() {
    try {
      return new DefaultSslEngineFactory.ForClient( AUTOSIGNER.get().createTruststore() ) ;
    } catch( final SSLException | Autosigner.KeystoreCreationException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public static SslEngineFactory.ForServer sslEngineFactoryForServer() {
    try {
      final Autosigner.CertificateHolder certificateHolder = AUTOSIGNER.get() ;
      return new DefaultSslEngineFactory.ForServer(
          certificateHolder.createKeystore(),
          certificateHolder.certificateDescription().alias,
          certificateHolder.certificateDescription().keypass
      ) ;
    } catch( final SSLException | Autosigner.KeystoreCreationException e ) {
      throw new RuntimeException( e ) ;
    }
  }
}
