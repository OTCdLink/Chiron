package com.otcdlink.chiron.middle;

import org.junit.Test;

/**
 * Tests for {@link PhoneNumber}.
 */
public class PhoneNumberTest {

  @Test
  public void goodNumbers() throws Exception {
    new PhoneNumber( "123456" ) ;
    new PhoneNumber( "33 12 3456" ) ;
    new PhoneNumber( "+33 12  3456" ) ;
  }
}
