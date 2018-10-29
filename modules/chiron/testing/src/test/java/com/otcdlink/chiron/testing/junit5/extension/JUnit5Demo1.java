package com.otcdlink.chiron.testing.junit5.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JUnit5Demo1 {

  @Test
  void test1() {
    LOGGER.info( "It workz: " + fooExtensionRegistered.displayNameFromContext() + "." ) ;
    LOGGER.info( "It dont: " + privateFooExtensionRegistered.displayNameFromContext() + "." ) ;
    LOGGER.info( "It dont: " + fooExtensionNotegistered.displayNameFromContext() + "." ) ;
  }

  @Test
  void test2() {
  }

  @RegisterExtension
  static final FooExtension fooExtensionRegistered = new FooExtension() ;

  @RegisterExtension
  static final BarExtension barExtensionRegistered = new BarExtension( "Baaar" ) ;

  @RegisterExtension
  private final FooExtension privateFooExtensionRegistered = new FooExtension() ;

  final FooExtension fooExtensionNotegistered = new FooExtension() ;

  private static final Logger LOGGER = LoggerFactory.getLogger( JUnit5Demo1.class ) ;
}
