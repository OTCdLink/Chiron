package com.otcdlink.chiron.testing.junit5;

import com.otcdlink.chiron.testing.DirectoryFixture;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.io.IOException;

public class DirectoryExtension implements BeforeEachCallback {

  private DirectoryFixture directoryFixture = null ;

  @Override
  public void beforeEach( final ExtensionContext context ) {
    directoryFixture = DirectorySupplier.loadInScope( context ) ;
  }

  public File testDirectory() {
    try {
      return directoryFixture.getDirectory() ;
    } catch( IOException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public File newFile( final String relativePath ) {
    return new File( testDirectory(), relativePath ) ;
  }
}
