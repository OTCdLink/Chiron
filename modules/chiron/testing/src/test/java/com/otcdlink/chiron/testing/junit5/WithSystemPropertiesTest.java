package com.otcdlink.chiron.testing.junit5;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class WithSystemPropertiesTest {

  @Test
  @WithSystemProperty( name = FOO, value = BAR )
  @WithSystemProperty( name = USER_HOME, value = WRECKED )
  void name() {
    LOGGER.info( "Test running." ) ;
    assertThat( System.getProperty( USER_HOME ) ).isEqualTo( WRECKED ) ;
    assertThat( System.getProperty( FOO ) ).isEqualTo( BAR ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( WithSystemPropertiesTest.class ) ;

  @AfterEach
  void tearDown() {
    LOGGER.info( "System properties restored to: " + System.getProperties() ) ;
    assertThat( System.getProperty( FOO ) ).isNotEqualTo( BAR ) ;
    assertThat( System.getProperty( USER_HOME ) ).isNotEqualTo( WRECKED ) ;
  }

  private static final String USER_HOME = "user.home" ;
  private static final String WRECKED = "wrecked" ;
  private static final String FOO = "foo" ;
  private static final String BAR = "bar" ;

}
