package com.otcdlink.chiron.downend.babyupend;

import com.otcdlink.chiron.conductor.Conductor;
import com.otcdlink.chiron.conductor.Responder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeResponderTest {

  @Test
  public void firstResponder() throws Exception {
    assertThat( new Conductor.CompositeResponder<>( RESPONDER_STRINGIFIER, RESPONDER_STAR )
        .respond( 1 ) ).isEqualTo( "1" ) ;
    assertThat( new Conductor.CompositeResponder<>( RESPONDER_STAR, RESPONDER_STRINGIFIER )
        .respond( 1 ) ).isEqualTo( "*1" ) ;
  }

  @Test
  public void nextResponderIfNull() throws Exception {
    assertThat( new Conductor.CompositeResponder<>( RESPONDER_NULL )
        .respond( 1 ) ).isNull() ;
    assertThat( new Conductor.CompositeResponder<>( RESPONDER_NULL, RESPONDER_STRINGIFIER )
        .respond( 1 ) ).isEqualTo( "1" ) ;
  }

// =======
// Fixture
// =======

  private static final Responder< Integer, String > RESPONDER_STRINGIFIER =
      integer -> Integer.toString( integer ) ;
  private static final Responder< Integer, String > RESPONDER_STAR =
      integer -> "*" + Integer.toString( integer ) ;
  private static final Responder< Integer, String > RESPONDER_NULL = integer -> null ;

}