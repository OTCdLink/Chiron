package io.github.otcdlink.chiron.middle.session;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionIdentifierTest {

  @Test
  public void equality() throws Exception {
    final SessionIdentifier sessionIdentifier1 = new SessionIdentifier( "111" ) ;
    final SessionIdentifier sessionIdentifier1bis = new SessionIdentifier( "111" ) ;
    final SessionIdentifier sessionIdentifier2 = new SessionIdentifier( "222" ) ;

    assertThat( sessionIdentifier1.equals( sessionIdentifier1 ) ).isTrue() ;
    assertThat( sessionIdentifier1.equals( sessionIdentifier1bis ) ).isTrue() ;
    assertThat( sessionIdentifier1.equals( sessionIdentifier2 ) ).isFalse() ;

  }
}
