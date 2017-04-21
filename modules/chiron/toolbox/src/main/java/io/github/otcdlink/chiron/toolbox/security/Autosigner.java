package io.github.otcdlink.chiron.toolbox.security;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.toolbox.security.Autosigner.AlternativeName.Kind.IP_ADDRESS;

/**
 * Creates Keystore and Truststore for one self-signed certificate.
 * The {@link Autosigner} makes easy to switch between file-based and heap memory-based
 * Security Stores.
 */
public final class Autosigner {

  private static final Logger LOGGER = LoggerFactory.getLogger( Autosigner.class ) ;

  private Autosigner() { }

  private interface Defaults {
    Duration VALIDITY = Duration.standardDays( 365 * 3 ) ;
    String ALGORITHM = "RSA" ;

    /** 4096 takes a lot of time, and Firefox 47 whines about 512-bit key. */
    int KEYSIZE = 1024 ;

    String KEYPASS = "Keypass" ;

    /**
     * Using JRE default password so generated {@link FileGenerator#truststoreFile()} may be
     * used as substitute for JRE cacert file.
     */
    String STOREPASS = "changeit" ;

    String ALIAS = "automatic" ;
    String KEYSTORE_FILENAME = "self-generated-keystore.jks" ;
    String TRUSTSTORE_FILENAME = "self-generated-truststore.jks" ;
    String PRIVATEKEY_FILENAME = "self-generated-private-key.pem" ;
  }

  public enum Feature {
    SUBJECT_KEY_IDENTIFIER,
    ;
  }
  
  
  public static class AlternativeName {
    public final Kind kind ;
    public final String name ;

    public AlternativeName( final Kind kind, final String name ) {
      this.kind = checkNotNull( kind ) ;
      this.name = checkNotNull( name ) ;
    }
    
    private GeneralName asGeneralName() {
      return new GeneralName( kind.code, name ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + '{' + kind.name() + ';' + name + '}' ;
    }

    public enum Kind {
      IP_ADDRESS( GeneralName.iPAddress ),
      ;
      
      private final int code ;

      Kind( final int code ) {
        this.code = code ;
      }
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }

      final AlternativeName that = ( AlternativeName ) other ;

      if( kind != that.kind ) {
        return false ;
      }
      return name.equals( that.name ) ;

    }

    @Override
    public int hashCode() {
      int result = kind.hashCode() ;
      result = 31 * result + name.hashCode() ;
      return result ;
    }
  }

  public static DescriptionStep builder() {
    return new CertificateCreation() ;
  }


// =============
// Builder steps
// =============

  public interface DescriptionStep {
    CertificateStep commonNameAndDefaults( String commonName ) ;
    CertificateStep describedAs( CertificateDescription description ) ;
  }

  public interface CertificateStep {
    CertificateHolder createCertificateWithDefaults() throws CertificateCreationException ;
    CertificateHolder createCertificate( Feature... features ) throws CertificateCreationException ;
    CertificateHolder createCertificate( ImmutableSet< Feature > features )
        throws CertificateCreationException ;
  }



// ==========
// More roles
// ==========
  
  public static final class CertificateDescription {
    public final String commonName ;
    public final ImmutableSet< AlternativeName > alternativeNames ;
    public final DateTime validityStart ;
    public final Duration validityDuration ;
    public final int keySize ;
    public final String algorithm ;
    public final String alias ;
    public final String keypass ;
    public final String storepass ;

    private CertificateDescription( final String commonName ) {
      this(
          commonName,
          alternativeNames( commonName ),
          new DateTime().minusYears( 1 ),  // Windows Guest (IEVMS) may lag a bit.
          Defaults.VALIDITY,
          Defaults.KEYSIZE,
          Defaults.ALGORITHM,
          Defaults.ALIAS,
          Defaults.KEYPASS,
          Defaults.STOREPASS
      ) ;
    }
    
    private static ImmutableSet< AlternativeName > alternativeNames( 
        final String commonName 
    ) {
      if( isIpAddress( commonName ) ) {
        return ImmutableSet.of( new AlternativeName( IP_ADDRESS, commonName ) ) ;
      } else {
        return ImmutableSet.of() ;
      }
    } 
    
    private CertificateDescription(
        final String commonName,
        final ImmutableSet< AlternativeName > alternativeNames,
        final DateTime validityStart, 
        final Duration validityDuration, 
        final int keySize, 
        final String algorithm, 
        final String alias, 
        final String keypass, 
        final String storepass 
    ) {
      this.alternativeNames = checkNotNull( alternativeNames ) ;
      this.validityStart = checkNotNull( validityStart ) ;
      this.validityDuration = checkNotNull( validityDuration ) ;
      checkArgument( keySize >= 0 ) ;
      this.keySize = keySize ;
      checkArgument( ! Strings.isNullOrEmpty( algorithm ) ) ;
      this.algorithm = algorithm ;
      checkArgument( ! Strings.isNullOrEmpty( alias ) ) ;
      this.alias = alias ;
      checkArgument( ! Strings.isNullOrEmpty( storepass ) ) ;
      this.storepass = storepass ;
      checkArgument( ! Strings.isNullOrEmpty( keypass ) ) ;
      this.keypass = keypass ;
      checkArgument( ! Strings.isNullOrEmpty( commonName ) ) ;
      this.commonName = commonName ;
    }

    public String asShortString() {
      return "{alias='" + alias + "', cn='" + commonName + "'}" ;
    }
  }

  public interface CertificateDescriptionHolder {
    CertificateDescription certificateDescription() ;
  }

  public interface CertificateHolder extends CertificateDescriptionHolder {
    KeyStore createKeystore() throws KeystoreCreationException;
    KeyStore createTruststore() throws KeystoreCreationException;
    FileGenerator withDirectory( final File directory ) ;
    TruststoreFileStep withKeystoreFile( final File keystoreFile ) ;
    InterningGenerator interning() ;
  }
  
  public interface TruststoreFileStep {
    PrivateKeyFileStep withTruststoreFile( final File truststoreFile ) ;
  }

  public interface PrivateKeyFileStep {
    FileGenerator withPrivateKeyFile( final File privateKeyFile ) ;
  }

  public interface Generator extends CertificateDescriptionHolder {
    /**
     * The {@link KeyStore} obtained from returned object will always be a fresh instance.
     */
    KeystoreAccess keystoreAccess() throws KeystoreCreationException ;

    /**
     * The {@link KeyStore} obtained from returned object will always be a fresh instance.
     */
    URL truststoreUrl() throws KeystoreCreationException ;
  }

  public interface FileGenerator extends Generator {
    File keystoreFile() ;
    File truststoreFile() ;
    File privateKeyFile() ;
    void generateFiles() throws IOException, KeystoreCreationException ;
    boolean filesExist() throws IOException  ;
    void deleteExistingFiles() throws IOException  ;
  }

  public interface InterningGenerator extends Generator { }

  
  public static class CertificateCreationException extends Exception {
    public CertificateCreationException( final Throwable cause ) {
      super( cause ) ;
    }
  }
  
  public static class KeystoreCreationException extends Exception {
    public KeystoreCreationException( final Throwable cause ) {
      super( cause ) ;
    }
  }
  
// ==================  
// All steps together
// ==================  

  private static class CertificateCreation implements DescriptionStep, CertificateStep {

    private CertificateDescription certificateDescription = null ;
    
    @Override
    public CertificateStep commonNameAndDefaults( final String commonName ) {
      this.certificateDescription = new CertificateDescription( commonName ) ;
      return this ;
    }

    @Override
    public CertificateStep describedAs( final CertificateDescription description ) {
      this.certificateDescription = checkNotNull( description ) ;
      return this ;
    }

    @Override
    public CertificateHolder createCertificateWithDefaults() throws CertificateCreationException {
      return createCertificate( Feature.SUBJECT_KEY_IDENTIFIER ) ;
    }

    @Override
    public CertificateHolder createCertificate( final Feature... features )
        throws CertificateCreationException 
    {
      return createCertificate( ImmutableSet.copyOf( features ) ) ;
    }

    @Override
    public CertificateHolder createCertificate( final ImmutableSet< Feature > features )
        throws CertificateCreationException 
    {
      return new ImmutableCertificateHolder( certificateDescription, features ) ;
    }

  }
  
  private static class ImmutableCertificateHolder implements CertificateHolder {
    private final CertificateDescription certificateDescription ;
    private final X509Certificate certificate ;
    private final KeyPair keyPair ;

    private static final String CRYPTO_PROVIDER = "BC";
    private static final Provider PROVIDER = new BouncyCastleProvider() ;

    static {
      Security.addProvider( PROVIDER ) ;
      LOGGER.info( "Added Security Provider '" + PROVIDER + "'." ) ;
    }


    private ImmutableCertificateHolder(
        final CertificateDescription certificateDescription,
        final ImmutableSet< Feature > features
    ) throws CertificateCreationException {
      this.certificateDescription = certificateDescription ;

      try {
        keyPair = generateKeyPair() ;

        final SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(
            ASN1Sequence.getInstance( keyPair.getPublic().getEncoded() ) ) ;

        final X500Name x500Name = new X500Name( "CN=" + certificateDescription.commonName ) ;
        final BigInteger serialNumber = BigInteger.valueOf( System.currentTimeMillis() ) ;
        final Date notBefore = certificateDescription.validityStart.toDate() ;
        final Date notAfter = certificateDescription.validityStart.plus(
            certificateDescription.validityDuration ).toDate() ;

        final X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
            x500Name, serialNumber, notBefore, notAfter, x500Name, subjectPublicKeyInfo ) ;

        if( features.contains( Feature.SUBJECT_KEY_IDENTIFIER ) ) {
          final DigestCalculator digestCalculator = new BcDigestCalculatorProvider()
              .get( new AlgorithmIdentifier( OIWObjectIdentifiers.idSHA1 ) ) ;
          final X509ExtensionUtils x509ExtensionUtils = new X509ExtensionUtils( digestCalculator ) ;
          builder.addExtension( Extension.subjectKeyIdentifier, false,
              x509ExtensionUtils.createSubjectKeyIdentifier( subjectPublicKeyInfo ) ) ;
        }
        
        if( ! certificateDescription.alternativeNames.isEmpty() ) {
          final List< GeneralName > generalNameList = certificateDescription.alternativeNames
              .stream().map( AlternativeName::asGeneralName ).collect( Collectors.toList() ) ;
          final GeneralNames generalNames = new GeneralNames( 
              generalNameList.toArray( new GeneralName[ generalNameList.size() ] ) ) ;
          builder.addExtension( Extension.subjectAlternativeName, false, generalNames ) ;
          LOGGER.info( "Added Subject Alternative Names: " + 
              certificateDescription.alternativeNames + "." ) ;
        }
        
        final ContentSigner signer = new JcaContentSignerBuilder( "SHA256WithRSAEncryption" )
            .build( keyPair.getPrivate() ) ;
        final X509CertificateHolder certificateHolder = builder.build( signer ) ;
        certificate = new JcaX509CertificateConverter()
            .setProvider( PROVIDER ).getCertificate( certificateHolder ) ;
        certificate.verify( keyPair.getPublic() ) ;

      } catch( OperatorCreationException | CertIOException | NoSuchAlgorithmException | 
            InvalidKeyException | NoSuchProviderException | SignatureException | 
            CertificateException e 
        ) {
        throw new CertificateCreationException( e ) ;
      }

    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException {
      final KeyPairGenerator keyPairGenerator =
          KeyPairGenerator.getInstance( certificateDescription.algorithm, CRYPTO_PROVIDER ) ;
      keyPairGenerator.initialize( certificateDescription.keySize, new SecureRandom() ) ;
      return keyPairGenerator.generateKeyPair() ;
    }

    private static KeyStore createEmptyKeystore()
        throws KeystoreCreationException
    {
      final KeyStore keyStore ;
      try {
        keyStore = KeyStore.getInstance( "JKS" ) ;
        keyStore.load( null, null ) ;
      } catch( KeyStoreException | IOException | CertificateException | 
          NoSuchAlgorithmException e 
      ) {
        throw new KeystoreCreationException( e ) ;
      }
      return keyStore ;
    }
    

    @Override
    public CertificateDescription certificateDescription() {
      return certificateDescription ;
    }

    @Override
    public KeyStore createKeystore() throws KeystoreCreationException {
      final KeyStore keystore = createEmptyKeystore() ;
      try {
        keystore.setKeyEntry( 
            certificateDescription.alias, 
            keyPair.getPrivate(), 
            certificateDescription.keypass.toCharArray(),
            new Certificate[] { certificate } 
        ) ;
      } catch( final KeyStoreException e ) {
        throw new KeystoreCreationException( e ) ;
      }
      return keystore ;
    }

    @Override
    public KeyStore createTruststore() throws KeystoreCreationException {
      final KeyStore truststore = createEmptyKeystore() ;
      try {
        truststore.setCertificateEntry( certificateDescription.alias, certificate ) ;
      } catch( final KeyStoreException e ) {
        throw new KeystoreCreationException( e ) ;
      }
      return truststore ;
    }

    @Override
    public FileGenerator withDirectory( final File directory ) {
      return new DefaultFileGenerator(
          this,
          new File( directory, Defaults.KEYSTORE_FILENAME ),
          new File( directory, Defaults.TRUSTSTORE_FILENAME ),
          new File( directory, Defaults.PRIVATEKEY_FILENAME )
      ) ;
    }

    @Override
    public TruststoreFileStep withKeystoreFile( final File keystoreFile ) {
      return new TruststoreFileReceiver( this, keystoreFile ) ;
    }

    @Override
    public InterningGenerator interning() {
      return new DefaultInterningGenerator( this ) ;
    }
    
    
  }

  private static class TruststoreFileReceiver implements TruststoreFileStep {

    private final ImmutableCertificateHolder storeHolder ;
    private final File keystoreFile ;

    private TruststoreFileReceiver(
        final ImmutableCertificateHolder storeHolder,
        final File keystoreFile
    ) {
      this.storeHolder = checkNotNull( storeHolder ) ;
      this.keystoreFile = checkNotNull( keystoreFile ) ;
    }

    @Override
    public PrivateKeyFileStep withTruststoreFile( final File truststoreFile ) {
      return new PrivateKeyFileReceiver( storeHolder, keystoreFile, truststoreFile ) ;
    }
  }

  private static class PrivateKeyFileReceiver implements PrivateKeyFileStep {

    private final ImmutableCertificateHolder storeHolder ;
    private final File keystoreFile ;
    private final File truststoreFile ;

    private PrivateKeyFileReceiver(
        final ImmutableCertificateHolder storeHolder,
        final File keystoreFile,
        final File truststoreFile
    ) {
      this.storeHolder = checkNotNull( storeHolder ) ;
      this.keystoreFile = checkNotNull( keystoreFile ) ;
      this.truststoreFile = checkNotNull( truststoreFile ) ;
    }

    @Override
    public FileGenerator withPrivateKeyFile( final File privateKeyFile ) {
      return new DefaultFileGenerator( storeHolder, keystoreFile, truststoreFile, privateKeyFile ) ;
    }
  }

  private static abstract class AbstractGenerator implements Generator {
    
    protected final ImmutableCertificateHolder storeHolder ;

    private AbstractGenerator( final ImmutableCertificateHolder storeHolder ) {
      this.storeHolder = checkNotNull( storeHolder ) ;
    }

    @Override
    public CertificateDescription certificateDescription() {
      return storeHolder.certificateDescription() ;
    }

    protected abstract URL keystoreUrl() throws KeystoreCreationException ;
    
    @Override
    public final KeystoreAccess keystoreAccess() throws KeystoreCreationException {
      final URL url = keystoreUrl() ;
      try {
        final String keystoreAccessAsString = storeHolder.certificateDescription.alias + ":" +
            storeHolder.certificateDescription.keypass + "@" + url.toExternalForm() ;
        return KeystoreAccess.parse(
            keystoreAccessAsString,
            storeHolder.certificateDescription.alias,
            url
        ) ;
      } catch( final KeystoreAccessFormatException e ) {
        throw new RuntimeException( e ) ;
      }
    }

    protected final void save( final KeyStore keystore, final OutputStream stream )
        throws KeystoreCreationException, IOException
    {
      try {
        keystore.store( stream, storeHolder.certificateDescription.storepass.toCharArray() ) ;
      } catch( KeyStoreException | NoSuchAlgorithmException | CertificateException e ) {
        throw new KeystoreCreationException( e ) ;
      }
    }
  }
  
  private static class DefaultFileGenerator extends AbstractGenerator implements FileGenerator {

    private final File keystoreFile ;
    private final File truststoreFile ;
    private final File privateKeyFile ;

    private DefaultFileGenerator( 
        final ImmutableCertificateHolder storeHolder,
        final File keystoreFile,
        final File truststoreFile,
        final File privateKeyFile
    ) {
      super( storeHolder ) ;
      this.keystoreFile = checkNotNull( keystoreFile ) ;
      this.truststoreFile = checkNotNull( truststoreFile ) ;
      this.privateKeyFile = checkNotNull( privateKeyFile ) ;
    }

    @Override
    protected URL keystoreUrl() {
      return urlQuiet( keystoreFile ) ;
    }

    @Override
    public URL truststoreUrl() {
      return urlQuiet( truststoreFile ) ;
    }

    @Override
    public File keystoreFile() {
      return keystoreFile ;
    }

    @Override
    public File truststoreFile() {
      return truststoreFile ;
    }

    @Override
    public File privateKeyFile() {
      return privateKeyFile ;
    }

    @Override
    public boolean filesExist() {
      return fileExists( keystoreFile() ) && fileExists( truststoreFile() ) ;
    }

    private static boolean fileExists( final File file ) {
      return file.isFile() && file.length() > 0 ;
    }

    @Override
    public void deleteExistingFiles() throws IOException {
      keystoreFile().delete() ;
      truststoreFile().delete() ;
    }

    @Override
    public void generateFiles() throws IOException, KeystoreCreationException {
      save( storeHolder.createKeystore(), keystoreFile, "keystore" ) ;
      save( storeHolder.createTruststore(), truststoreFile, "truststore" ) ;

      final StringWriter stringWriter = new StringWriter() ;
      try( final JcaPEMWriter pemWriter = new JcaPEMWriter( stringWriter ) ) {
        pemWriter.writeObject( storeHolder.keyPair.getPrivate() ) ;
      }
      Files.write( stringWriter.toString(), privateKeyFile, Charsets.US_ASCII ) ;
    }

    private void save(
        final KeyStore keystore,
        final File storeFile,
        final String storekind
    ) throws IOException, KeystoreCreationException {
      final File parent = storeFile.getParentFile() ;
      if( parent != null && ! parent.exists() ) {
        parent.mkdirs() ;
      }
      try( final FileOutputStream stream = new FileOutputStream( storeFile ) ) {
        save( keystore, stream ) ;
        LOGGER.info( "Generated " + storekind + " with " +
            storeHolder.certificateDescription.asShortString() +
            " into '" + storeFile.getAbsolutePath() + "'."
        ) ;
      }
    }

  }

  private static class DefaultInterningGenerator extends AbstractGenerator implements InterningGenerator {

    private DefaultInterningGenerator( final ImmutableCertificateHolder storeHolder ) {
      super( storeHolder ) ;
    }

    @Override
    protected URL keystoreUrl() throws KeystoreCreationException {
      return createUrl( 
          storeHolder.createKeystore(), "keystore", storeHolder.certificateDescription ) ;
    }

    @Override
    public URL truststoreUrl() throws KeystoreCreationException {
      return createUrl(
          storeHolder.createKeystore(),
          "truststore",
          storeHolder.certificateDescription
      ) ;
    }

    private URL createUrl(
        final KeyStore securityStore,
        final String kind,
        final CertificateDescription certificateDescription
    ) throws RuntimeException {
      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream() ;
      try {
        save( securityStore, byteArrayOutputStream ) ;
      } catch( KeystoreCreationException | IOException e ) {
        throw new RuntimeException( e ) ;
      }

      try {
        final URLStreamHandler urlStreamHandler = new URLStreamHandler() {
          @Override
          protected URLConnection openConnection( final URL url ) throws IOException {
            return new URLConnection( url ) {
              @Override
              public void connect() throws IOException { }
              @Override
              public InputStream getInputStream() {
                return new ByteArrayInputStream( byteArrayOutputStream.toByteArray() ) ;
              }
            } ;
          }
        } ;
        return new URL( 
            null, 
            kind + "://" + certificateDescription.alias + '/' + certificateDescription.commonName,
            urlStreamHandler 
        ) ;
      } catch( final MalformedURLException e ) {
        throw new RuntimeException( e ) ;
      }

    }
  }

  
// =================  
// Various utilities
// =================

  private static final Pattern IPV4_PATTERN = Pattern.compile( "\\d+\\.\\d+\\.\\d+\\.\\d+" ) ;

  private static boolean isIpAddress( final String commonName ) {
    return IPV4_PATTERN.matcher( commonName ).matches() ;
  }

  private static URL urlQuiet( final File file ) {
    try {
      return file.toURI().toURL() ;
    } catch( final MalformedURLException e ) {
      throw new RuntimeException( e ) ;
    }
  }



// ============
// Usage sample
// ============

  @SuppressWarnings( "unused" )
  public static void main( final String... arguments ) throws Exception {
    final CertificateHolder certificateHolder = Autosigner.builder()
        .commonNameAndDefaults( "some.name.com" )
        .createCertificate( Feature.SUBJECT_KEY_IDENTIFIER )
    ;
    final KeyStore keystore = certificateHolder.createKeystore() ;
    final KeyStore trustStore = certificateHolder.createTruststore() ;
    final String keypass = certificateHolder.certificateDescription().keypass ;
    // etc.

    final FileGenerator fileGenerator1 = certificateHolder
        .withKeystoreFile( new File( "my-own-keystore.jks" ) )
        .withTruststoreFile( new File( "my-own-truststore.jks" ) )
        .withPrivateKeyFile( new File( "my-own-private-key.jks" ) )
    ;
    fileGenerator1.generateFiles() ;
    
    final FileGenerator fileGenerator2 = certificateHolder
        .withDirectory( new File( "securitystores" ) ) ;
    fileGenerator2.generateFiles() ; 
    
    final InterningGenerator interningGenerator = certificateHolder.interning() ;
    final KeystoreAccess keystoreAccess = interningGenerator.keystoreAccess() ;
    final URL truststoreAccess = interningGenerator.truststoreUrl() ;
    
  }

}
