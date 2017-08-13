package com.otcdlink.chiron.middle;


import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import org.joda.time.DateTime;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DesignatorForgerTest {

  @Test
  public void internalWithCauseNoCounter() throws Exception {
    final Designator internal = DesignatorForger
        .newForger()
        .cause( STAMP_1_0 )
        .flooredInstant( 2 )
        .internal()
    ;
    assertThat( internal.sessionIdentifier ).isNull() ;
    assertThat( internal.tag ).isNull() ;
    assertThat( internal.cause ).isEqualTo( STAMP_1_0 ) ;
    assertThat( internal.stamp ).isEqualTo( STAMP_2_0 ) ;
    assertThat( internal ).isInstanceOf( Designator.class ) ;
  }

  @Test
  public void internalWithCounterNoCause() throws Exception {
    final Designator internal = DesignatorForger
        .newForger()
        .flooredInstant( 2 )
        .counter( 1 )
        .internal()
    ;
    assertThat( internal.sessionIdentifier ).isNull() ;
    assertThat( internal.tag ).isNull() ;
    assertThat( internal.cause ).isNull() ;
    assertThat( internal.stamp ).isEqualTo( STAMP_2_1 ) ;
    assertThat( internal ).isInstanceOf( Designator.class ) ;
  }

  @Test
  public void internalWithNextStamp() throws Exception {
    final Stamp base = Stamp.raw( INSTANT_1.getMillis(), 3 ) ;
    final Designator internal = DesignatorForger
        .newForger()
        .nextStamp( base )
        .internal()
    ;
    assertThat( internal.sessionIdentifier ).isNull() ;
    assertThat( internal.tag ).isNull() ;
    assertThat( internal.cause ).isNull() ;
    assertThat( internal.stamp ).isEqualTo( Stamp.raw( INSTANT_1.getMillis(), 4 ) ) ;
  }

  @Test
  public void upwardWithCounter() throws Exception {
    final Designator upward = DesignatorForger
        .newForger()
        .session( SESSION_IDENTIFIER )
        .tag( COMMAND_TAG )
        .instant( INSTANT_2 )
        .upward()
    ;
    assertThat( upward.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( upward.tag ).isEqualTo( COMMAND_TAG ) ;
    assertThat( upward.cause ).isNull() ;
    assertThat( upward.stamp ).isEqualTo( STAMP_2_0 ) ;
    assertThat( upward ).isInstanceOf( Designator.class ) ;
  }

  @Test
  public void downward() throws Exception {
    final Designator internal = DesignatorForger
        .newForger()
        .session( SESSION_IDENTIFIER )
        .cause( STAMP_1_0 )
        .instant( INSTANT_2 )
        .downward()
        ;
    assertThat( internal.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( internal.tag ).isNull() ;
    assertThat( internal.cause ).isEqualTo( STAMP_1_0 ) ;
    assertThat( internal.stamp ).isEqualTo( STAMP_2_0 ) ;
    assertThat( internal ).isInstanceOf( Designator.class ) ;
  }

  @Test
  public void downwardWithCauseAndTag() throws Exception {
    final Designator internal = DesignatorForger
        .newForger()
        .session( SESSION_IDENTIFIER )
        .cause( STAMP_1_0 )
        .tag( COMMAND_TAG )
        .instant( INSTANT_2 )
        .downward()
        ;
    assertThat( internal.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( internal.tag ).isEqualTo( COMMAND_TAG ) ;
    assertThat( internal.cause ).isEqualTo( STAMP_1_0 ) ;
    assertThat( internal.stamp ).isEqualTo( STAMP_2_0 ) ;
    assertThat( internal ).isInstanceOf( Designator.class ) ;
  }

  @Test( expected = NullPointerException.class )
  public void tagWithoutSession() throws Exception {
    DesignatorForger
        .newForger()
        .tag( COMMAND_TAG )
        .instant( INSTANT_2 )
        .downward()
    ;
  }


// =======
// Fixture
// =======

  private static final DateTime INSTANT_1 = new DateTime( Stamp.FLOOR_MILLISECONDS + 1 ) ;
  private static final DateTime INSTANT_2 = new DateTime( Stamp.FLOOR_MILLISECONDS + 2 ) ;

  private static final SessionIdentifier SESSION_IDENTIFIER = new SessionIdentifier( "session" ) ;
  private static final Command.Tag COMMAND_TAG = new Command.Tag( "tag" ) ;

  private static final Stamp STAMP_1_0 = Stamp.raw( INSTANT_1.getMillis(), 0 ) ;
  private static final Stamp STAMP_2_0 = Stamp.raw( INSTANT_2.getMillis(), 0 ) ;
  private static final Stamp STAMP_2_1 = Stamp.raw( INSTANT_2.getMillis(), 1 ) ;

}