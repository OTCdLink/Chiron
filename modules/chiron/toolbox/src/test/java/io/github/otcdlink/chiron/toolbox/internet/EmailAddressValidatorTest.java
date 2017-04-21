package io.github.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static io.github.otcdlink.chiron.toolbox.internet.InternetAddressValidator.isEmailAddressValid;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InternetAddressValidator}.
 */
public class EmailAddressValidatorTest {

  @Test( expected = NullPointerException.class )
  public void nullity() {
    checkEmailAddressInvalid( null ) ;
  }

  @Test
  public void simple() {
    checkEmailAddressValid( "me@foo.com" ) ;
  }

  @Test
  public void dashed() {
    checkEmailAddressValid( "this-is-me@f-o-o.com" ) ;
  }

  @Test
  public void missingLocalPart() {
    checkEmailAddressInvalid( "@foo.com" ) ;
  }

  @Test
  public void localPartMissingAgain() {
    checkEmailAddressInvalid( "foo.com" ) ;
  }


// =======  
// Fixture
// =======
  
  private static void checkEmailAddressValid( final String emailAdress ) {
    assertThat( isEmailAddressValid( emailAdress ) ).isTrue() ;
  }
  
  private static void checkEmailAddressInvalid( final String emailAdress ) {
    assertThat( isEmailAddressValid( emailAdress ) ).isFalse() ;
  }
  
}
