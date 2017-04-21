package io.github.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EmailAddress}.
 */
public class EmailAddressTest {

  @Test
  public void goodEmailAddress() throws EmailAddressFormatException {
    assertThat( new EmailAddress( "me@me.me" ).getAsString() ).isEqualTo( "me@me.me" ) ;
  }

  @Test( expected = EmailAddressFormatException.class )
  public void badEmailAddress() throws EmailAddressFormatException {
    new EmailAddress( "bad" ) ;
  }

}
