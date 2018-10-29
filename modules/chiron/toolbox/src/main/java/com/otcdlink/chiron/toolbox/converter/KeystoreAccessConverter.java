package com.otcdlink.chiron.toolbox.converter;

import com.google.common.base.Converter;
import com.google.common.io.ByteSource;
import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.toolbox.security.KeystoreAccess;
import com.otcdlink.chiron.toolbox.security.KeystoreAccessFormatException;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.MissingResourceException;

public class KeystoreAccessConverter extends Converter< String, KeystoreAccess > {

  private final URL productionKeystoreUrl ;
  private final String certificateAliasDefault ;

  public KeystoreAccessConverter(
      final URL productionKeystoreUrl,
      final String certificateAliasDefault
  ) {
    this.productionKeystoreUrl = productionKeystoreUrl ;
    this.certificateAliasDefault = certificateAliasDefault ;
  }

  @Override
  public KeystoreAccess doForward( @Nonnull final String keystoreAccessAsString ) {
    final KeystoreAccess keystoreAccess ;
    try {
      keystoreAccess = KeystoreAccess.parse(
          keystoreAccessAsString,
          certificateAliasDefault,
          productionKeystoreUrl
      ) ;
    } catch( KeystoreAccessFormatException e ) {
      throw new ConverterException( e ) ;
    }
    final ByteSource inputSupplier = UrxTools.getByteSource( keystoreAccess.keystoreUrl ) ;

    //noinspection UnusedDeclaration,EmptyTryBlock
    try( final InputStream inputStream = inputSupplier.openStream() ) {
    } catch( final IOException e ) {
      throw new MissingResourceException(
          "Can't open keystore '" + keystoreAccess.keystoreUrl.toExternalForm() + "': "
              + e.getClass() + ", " + e.getMessage()
          ,
          getClass().getName(),
          ""
      ) ;
    }
    return keystoreAccess ;
  }

  @Override
  protected String doBackward( @Nonnull final KeystoreAccess keystoreAccess ) {
    return keystoreAccess.asStringWithObfuscatedPassword( "******" ) ;
  }
}
