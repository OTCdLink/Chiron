package com.otcdlink.chiron.testing.junit5.extension;

import org.junit.jupiter.api.extension.ExtensionContext;

class ReusableStuff {

  /**
   * Gets a {@link ReusableStuff} instance guaranteed to be always the same for one Test.
   */
  static ReusableStuff loadInTestScope( final ExtensionContext context ) {
    final ExtensionContext.Store store = context.getStore(
        ExtensionContext.Namespace.create( context.getUniqueId() ) ) ;
    return store.getOrComputeIfAbsent( ReusableStuff.class ) ;
  }
}
