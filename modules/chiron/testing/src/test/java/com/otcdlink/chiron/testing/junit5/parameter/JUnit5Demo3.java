package com.otcdlink.chiron.testing.junit5.parameter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

/**
 * This is a draft for some kind of extension similar to
 * {@link org.junit.jupiter.params.ParameterizedTest} which runs a test with different
 * arguments, but the arguments feed a helper object.
 */
public class JUnit5Demo3 {

  @Test  // Becomes: @TestWithParameterizedHelper
  void test1() {
    LOGGER.info( "It runs with " + someHelper.parameters() ) ;
  }


// =======
// Fixture
// =======

  static Stream< Arguments > arguments() {
    return Stream.of(
        Arguments.of( 1, "One" ),
        Arguments.of( 2, "Two" )
    ) ;
  }

  // @RegisterParameterizedHelper( methodSource = "arguments" )
  final SomeHelper someHelper = new SomeHelper() ;

  private static final Logger LOGGER = LoggerFactory.getLogger( JUnit5Demo3.class ) ;
}
