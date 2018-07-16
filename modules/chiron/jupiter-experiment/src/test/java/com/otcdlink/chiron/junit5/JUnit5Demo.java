package com.otcdlink.chiron.junit5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JUnit5Demo {

  @Test
  void name() {
    LOGGER.info( "It workz: " + fooExtensionRegistered.displayNameFromContext() + "." ) ;
    LOGGER.info( "It dont: " + privateFooExtensionRegistered.displayNameFromContext() + "." ) ;
    LOGGER.info( "It dont: " + fooExtensionUnegistered.displayNameFromContext() + "." ) ;
  }

  @RegisterExtension
  final FooExtension fooExtensionRegistered = new FooExtension() ;

  @RegisterExtension
  private final FooExtension privateFooExtensionRegistered = new FooExtension() ;

  final FooExtension fooExtensionUnegistered = new FooExtension() ;

  private static final Logger LOGGER = LoggerFactory.getLogger( JUnit5Demo.class ) ;
}
