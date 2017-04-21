package io.github.otcdlink.chiron.toolbox.internet;

import io.github.otcdlink.chiron.toolbox.Credential;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Credential}.
 */
public class CredentialTest {

  @Test
  public void justInstantiate() throws Exception {
    final Credential credential = new Credential( "n", "p" ) ;
    assertThat( credential.getLogin() ).isEqualTo( "n" ) ;
    assertThat( credential.getPassword() ).isEqualTo( "p" ) ;
  }

}
