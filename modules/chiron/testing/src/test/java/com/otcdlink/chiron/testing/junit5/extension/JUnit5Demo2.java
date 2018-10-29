package com.otcdlink.chiron.testing.junit5.extension;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JUnit5Demo2 {

  @Test
  @Bar( message = "Message_from_annotation" )
  void test1() {
    LOGGER.info( "It runs." ) ;
  }


  private static final Logger LOGGER = LoggerFactory.getLogger( JUnit5Demo2.class ) ;
}
