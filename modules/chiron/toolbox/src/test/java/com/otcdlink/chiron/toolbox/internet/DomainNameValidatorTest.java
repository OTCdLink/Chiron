package com.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static com.otcdlink.chiron.toolbox.internet.InternetAddressValidator.isDomainNameValid;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InternetAddressValidator}.
 */
public class DomainNameValidatorTest {

  @Test
  public void simple() {
    checkDomainNameValid( "foo.com" ) ;
  }

  @Test
  public void withSubdomain() {
    checkDomainNameValid( "foo.bar.com" ) ;
  }

  @Test
  public void missingPart() {
    checkDomainNameInvalid( "foo" ) ;
  }


// =======  
// Fixture
// =======
  
  private static void checkDomainNameValid( final String domainName ) {
    assertThat( isDomainNameValid( domainName ) ).isTrue() ;
  }
  
  private static void checkDomainNameInvalid( final String domainName ) {
    assertThat( isDomainNameValid( domainName ) ).isFalse() ;    
  }
}
