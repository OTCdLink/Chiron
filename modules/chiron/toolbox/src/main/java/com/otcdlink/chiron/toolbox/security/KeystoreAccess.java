package com.otcdlink.chiron.toolbox.security;

import com.google.common.base.Strings;
import com.google.common.io.ByteSource;
import com.otcdlink.chiron.toolbox.ComparatorTools;
import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.toolbox.internet.InternetAddressValidator;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Comparator;
import java.util.MissingResourceException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Bundles the parameters for accessing to a keystore: alias, password, URL.
 */
public class KeystoreAccess {

  public final String alias ;
  public final String password ;
  public final URL keystoreUrl ;
  public final boolean usingDefaultAlias ;
  public final boolean usingDefaultKeystoreUrl ;

  private KeystoreAccess(
      final String alias,
      final boolean usingDefaultAlias,
      final String password,
      final URL keystoreUrl,
      final boolean usingDefaultKeystoreUrl
  ) {
    this.alias = alias ;
    this.usingDefaultAlias = usingDefaultAlias ;
    this.password = password ;
    this.keystoreUrl = keystoreUrl ;
    this.usingDefaultKeystoreUrl = usingDefaultKeystoreUrl ;
  }

  public static KeystoreAccess create(
      final String alias,
      final String keypass,
      final String url
  ) throws KeystoreAccessFormatException {
    return parse( alias + ':' + keypass + '@' + url ) ;
  }

  public static KeystoreAccess parse( final String rawString )
      throws KeystoreAccessFormatException
  {
    final Matcher matcher = COMPLETE_PATTERN.matcher( rawString ) ;
    if( matcher.matches() ) {
      final String alias = matcher.group( 1 ) ;
      final String password = matcher.group( 2 ) ;
      final URL keystoreUrl = UrxTools.parseUrlQuiet( matcher.group( 3 ) ) ;
      return new KeystoreAccess( alias, false, password, keystoreUrl, false ) ;
    } else {
      throw new KeystoreAccessFormatException( rawString ) ;
    }
  }

  public static KeystoreAccess parse(
      final String rawString,
      final String defaultAlias,
      final URL defaultKeystoreUrl
  ) throws KeystoreAccessFormatException {
    checkArgument( ! Strings.isNullOrEmpty( defaultAlias ) ) ;
    checkNotNull( defaultKeystoreUrl ) ;
    final Matcher matcher = COMPLETE_PATTERN.matcher( rawString ) ;
    if( matcher.matches() ) {
      final String alias = matcher.group( 1 ) ;
      final String password = matcher.group( 2 ) ;
      final URL keystoreUrl ;
      final boolean usingDefaultAlias ;
      final boolean usingDefaultKeystoreUrl ;
      try {
        final String keystoreUrlAsString = matcher.group( 3 ) ;
        if( keystoreUrlAsString == null ) {
          keystoreUrl = defaultKeystoreUrl ;
          usingDefaultKeystoreUrl = true ;
        }
        else {
          if( keystoreUrlAsString.equals( defaultKeystoreUrl.toExternalForm() ) ) {
            // When using hacky URL with 'keystore://' or 'truststore://' protocol
            // re-parsing the URL fails because we don't provide an URLStreamHandler.
            // So we reuse given object which is known to work.
            keystoreUrl = defaultKeystoreUrl ;
          } else {
            keystoreUrl = new URL( keystoreUrlAsString ) ;
          }
          usingDefaultKeystoreUrl = false ;
        }
      } catch( final MalformedURLException e ) {
        throw new KeystoreAccessFormatException( rawString, e ) ;
      }
      final String resolvedAlias ;
      if( alias == null ) {
        resolvedAlias = defaultAlias ;
        usingDefaultAlias = true ;
      } else {
        resolvedAlias = alias ;
        usingDefaultAlias = false ;
      }
      return new KeystoreAccess(
          resolvedAlias,
          usingDefaultAlias,
          password,
          keystoreUrl,
          usingDefaultKeystoreUrl
      ) ;
    } else {
      throw new KeystoreAccessFormatException( rawString ) ;
    }
  }

  public KeyStore load() throws CertificateException, NoSuchAlgorithmException, KeyStoreException {
    final ByteSource byteSource = UrxTools.getByteSource( keystoreUrl ) ;

    //noinspection UnusedDeclaration,EmptyTryBlock
    try( final InputStream inputStream = byteSource.openStream() ) {
      return KeystoreTools.loadKeystore( keystoreUrl ) ;
    } catch( final IOException e ) {
      throw new MissingResourceException(
          "Can't open keystore '" + keystoreUrl.toExternalForm() + "': "
              + e.getClass() + ", " + e.getMessage()
          ,
          getClass().getName(),
          ""
      ) ;
    }

  }

  public String asString() {
    return asStringWithObfuscatedPassword( null ) ;
  }

  public String asStringWithObfuscatedPassword( final String obfuscator ) {
    final StringBuilder stringBuilder = new StringBuilder() ;
    if( ! usingDefaultAlias ) {
      stringBuilder.append( alias ) ;
      stringBuilder.append( ':' ) ;
    }
    if( obfuscator == null ) {
      stringBuilder.append( password ) ;
    } else {
      stringBuilder.append( obfuscator ) ;
    }
    if( ! usingDefaultKeystoreUrl ) {
      stringBuilder.append( '@' ) ;
      stringBuilder.append( keystoreUrl.toExternalForm() ) ;
    }
    return stringBuilder.toString() ;
  }

  private static final Pattern ALIAS_PATTERN = InternetAddressValidator.PASSWORD ;
  private static final Pattern PASSWORD_PATTERN = InternetAddressValidator.PASSWORD ;

  /**
   * {@code <[<alias>]:<password>[@<url>]>}
   */
  /*package*/ static final Pattern COMPLETE_PATTERN = Pattern.compile(
      "(?:(" + ALIAS_PATTERN.pattern() + "):)?"
      + "(" + PASSWORD_PATTERN.pattern() + ")(?:@(.+))?"
  ) ;



  @Override
  public String toString() {
    return getClass().getSimpleName() + "{"
        + alias + ":"
        + "******"
//        + password
        + "@" + keystoreUrl.toExternalForm()
        + "}"
        ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final KeystoreAccess that = ( KeystoreAccess ) other ;

    return COMPARATOR.compare( this, that ) == 0 ;
  }

  @Override
  public int hashCode() {
    int result = alias.hashCode() ;
    result = 31 * result + password.hashCode() ;
    result = 31 * result + keystoreUrl.hashCode() ;
    return result ;
  }

  public static final Comparator< KeystoreAccess > COMPARATOR
      = new ComparatorTools.WithNull< KeystoreAccess >() {
        @Override
        protected int compareNoNulls(
            final KeystoreAccess first,
            final KeystoreAccess second
        ) {
          final int aliasComparison = ComparatorTools.STRING_COMPARATOR.compare( first.alias, second.alias ) ;
          if( aliasComparison == 0 ) {
            final int passwordComparison
                = ComparatorTools.STRING_COMPARATOR.compare( first.password, second.password ) ;
            if( passwordComparison == 0 ) {
              final int urlComparison = ComparatorTools.STRING_COMPARATOR.compare(
                  first.keystoreUrl.toExternalForm(), second.keystoreUrl.toExternalForm() ) ;
              return urlComparison ;
            } else {
              return passwordComparison ;
            }
          } else {
            return aliasComparison ;
          }
        }
      }
  ;


}
