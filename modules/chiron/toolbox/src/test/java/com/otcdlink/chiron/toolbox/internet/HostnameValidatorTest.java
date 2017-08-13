package com.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static com.otcdlink.chiron.toolbox.internet.InternetAddressValidator.isHostnameValid;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InternetAddressValidator}.
 */
public class HostnameValidatorTest {

  @Test
  public void simple() {
    checkHostnameValid( "foo" ) ;
  }

  @Test
  public void badCharacter() {
    checkHostnameInvalid( "foo)" ); ;
  }


// =======  
// Fixture
// =======
  
  private static void checkHostnameValid( final String hostname ) {
    assertThat( isHostnameValid( hostname ) ).isTrue() ;
  }
  
  private static void checkHostnameInvalid( final String hostname ) {
    assertThat( isHostnameValid( hostname ) ).isFalse() ;
  }

}
