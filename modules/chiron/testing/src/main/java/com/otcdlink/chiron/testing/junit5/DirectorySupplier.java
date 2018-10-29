package com.otcdlink.chiron.testing.junit5;

import com.otcdlink.chiron.testing.DirectoryFixture;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.lang.reflect.Method;

public interface DirectorySupplier {

  /**
   * Any extension needing Test's directory should call this method, which guarantees the
   * {@link DirectoryFixture} to be instantiated only once for given {@link ExtensionContext}.
   */
  static DirectoryFixture loadInScope( final ExtensionContext extensionContext ) {
    final ExtensionContext.Store store = extensionContext.getStore(
        ExtensionContext.Namespace.create( extensionContext.getUniqueId() ) ) ;
    return store.getOrComputeIfAbsent(
        DirectoryFixture.class,
        c -> new DirectoryFixture( toFilePath( extensionContext ) ),
        DirectoryFixture.class
    ) ;
  }

  static String toFilePath( final ExtensionContext extensionContext ) {
    final Method requiredTestMethod = extensionContext.getRequiredTestMethod() ;
    return requiredTestMethod.getDeclaringClass().getName() + '#' + requiredTestMethod.getName() ;
  }

  File testDirectory() ;

}
