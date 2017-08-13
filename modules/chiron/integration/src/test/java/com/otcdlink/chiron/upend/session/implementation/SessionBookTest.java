package com.otcdlink.chiron.upend.session.implementation;

import com.otcdlink.chiron.integration.ReactiveSessionFixture;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import org.assertj.core.api.Assertions;
import org.joda.time.Duration;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionBookTest {

  @Test
  public void createActivateRemove() throws Exception {

    assertThat( sessionBook.create( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ).isNull() ;
    final SessionDetail.Pending pending = sessionDetail( ReactiveSessionFixture.SESSION_1 ) ;
    assertThat( pending.creationTime ).isEqualTo( ReactiveSessionFixture.T_0 ) ;
    assertThat( pending.channel ).isEqualTo( ReactiveSessionFixture.CHANNEL_A1 ) ;
    assertThat( pending.inactiveSince ).isEqualTo( ReactiveSessionFixture.T_0 ) ;

    Assertions.assertThat( sessionBook.activate( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_1, false ).userIdentity )
        .isEqualTo( ReactiveSessionFixture.USER_X ) ;
    final SessionDetail.Active active = sessionDetail( ReactiveSessionFixture.SESSION_1 ) ;
    assertThat( active.creationTime ).isEqualTo( ReactiveSessionFixture.T_0 ) ;
    assertThat( active.channel ).isEqualTo( ReactiveSessionFixture.CHANNEL_A1 ) ;
    assertThat( active.inactiveSince ).isNull() ;
    
    assertThat( sessionBook.removeChannel( ReactiveSessionFixture.CHANNEL_A1, null ) ).isTrue() ;
    assertThat( ( SessionDetail ) sessionDetail( ReactiveSessionFixture.SESSION_1 ) ).isNull() ;

    assertThat( sessionBook.removeChannel( ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_2 ) )
        .describedAs( "Removal can occur only once, then it fails quietly" )
        .isFalse()
    ;
  }

  @Test
  public void twoSessionsForTheSameUser() throws Exception {
    assertThat( sessionBook.create( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ).isNull() ;

    final SignonFailureNotice sessionCreationFailureNotice =
        sessionBook.create( ReactiveSessionFixture.SESSION_IDENTIFIER_2, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ;
    Assertions.assertThat( sessionCreationFailureNotice.kind )
        .isEqualTo( SignonFailure.SESSION_ALREADY_ATTRIBUTED ) ;
  }

  @Test
  public void activateTooLate() throws Exception {

    sessionBook.create( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ;

    Assertions.assertThat( sessionBook.activate( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_3, false ).signonFailureNotice.kind )
        .isEqualTo( SignonFailure.UNKNOWN_SESSION ) ;
    assertThat( ( SessionDetail ) sessionDetail( ReactiveSessionFixture.SESSION_1 ) ).isNull() ;
  }

  @Test
  public void onlyOnceChanceToActivate() throws Exception {

    sessionBook.create( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ;

    Assertions.assertThat( sessionBook.activate( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_C3, ReactiveSessionFixture.T_0, false ).signonFailureNotice.kind )
        .describedAs( "Activating with a Channel that has a different remote address" )
        .isEqualTo( SignonFailure.UNMATCHED_NETWORK_ADDRESS ) ;
    assertThat( ( SessionDetail ) sessionDetail( ReactiveSessionFixture.SESSION_1 ) ).isNull() ;
  }

  @Test
  public void reuseTooLate() throws Exception {

    sessionBook.create( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ;

    sessionBook.activate( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_0, false ) ;

    sessionBook.removeChannel( ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_0 ) ;

    Assertions.assertThat( sessionBook.reuse( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_B1, ReactiveSessionFixture.T_3 ).signonFailureNotice.kind )
        .isEqualTo( SignonFailure.UNKNOWN_SESSION) ;
    assertThat( ( SessionDetail ) sessionDetail( ReactiveSessionFixture.SESSION_1 ) ).isNull() ;
  }

  @Test
  public void onlyOneChanceToReuse() throws Exception {

    sessionBook.create( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ;

    sessionBook.activate( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_0, false ) ;

    sessionBook.removeChannel( ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_0 ) ;

    Assertions.assertThat( sessionBook.reuse( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_C3, ReactiveSessionFixture.T_1 ).signonFailureNotice.kind )
        .describedAs( "Reusing with a Channel that has a different remote address" )
        .isEqualTo( SignonFailure.UNMATCHED_NETWORK_ADDRESS )
    ;
    assertThat( ( SessionDetail ) sessionDetail( ReactiveSessionFixture.SESSION_1 ) ).isNull() ;
  }


    @Test
  public void orphanhoodAndReuse() throws Exception {

    sessionBook.create( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ;

    sessionBook.activate( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_0, false ) ;

    assertThat( sessionBook.removeChannel( ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_1 ) ).isTrue() ;
    final SessionDetail.Orphaned orphaned = sessionDetail( ReactiveSessionFixture.SESSION_1 ) ;
    assertThat( orphaned.inactiveSince ).isEqualTo( ReactiveSessionFixture.T_1 ) ;
    assertThat( orphaned.remoteAddress ).isEqualTo( ReactiveSessionFixture.CHANNEL_A1.remoteAddress() ) ;

    assertThat( sessionBook.reuse( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_B1, ReactiveSessionFixture.T_2 ).signonFailureNotice ).isNull() ;
    final SessionDetail.Reusing reusing = sessionDetail( ReactiveSessionFixture.SESSION_1 ) ;
    assertThat( reusing.sessionIdentifier ).isEqualTo( ReactiveSessionFixture.SESSION_1 ) ;
    assertThat( reusing.channel ).isEqualTo( ReactiveSessionFixture.CHANNEL_B1 ) ;
    assertThat( reusing.inactiveSince ).isEqualTo( ReactiveSessionFixture.T_1 ) ;
    assertThat( reusing.remoteAddress ).isNull() ;
    assertThat( reusing.user ).isEqualTo( ReactiveSessionFixture.USER_X ) ;

    Assertions.assertThat( sessionBook.activate( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_B1, ReactiveSessionFixture.T_3, true ).userIdentity )
        .isEqualTo( ReactiveSessionFixture.USER_X ) ;
    final SessionDetail.Active reactivated = sessionDetail( ReactiveSessionFixture.SESSION_1 ) ;
    assertThat( reactivated.sessionIdentifier ).isEqualTo( ReactiveSessionFixture.SESSION_1 ) ;
    assertThat( reactivated.channel ).isEqualTo( ReactiveSessionFixture.CHANNEL_B1 ) ;
    assertThat( reactivated.inactiveSince ).isNull() ;
    assertThat( reactivated.remoteAddress ).isNull() ;
    assertThat( reactivated.user ).isEqualTo( ReactiveSessionFixture.USER_X ) ;

  }
    @Test
  public void orphanhoodAndRecreate() throws Exception {

    sessionBook.create( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ;
    sessionBook.activate( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_0, false ) ;

    assertThat( sessionBook.removeChannel( ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_1 ) ).isTrue() ;
    final SessionDetail.Orphaned orphaned = sessionDetail( ReactiveSessionFixture.SESSION_1 ) ;
    assertThat( orphaned.inactiveSince ).isEqualTo( ReactiveSessionFixture.T_1 ) ;
    assertThat( orphaned.remoteAddress ).isEqualTo( ReactiveSessionFixture.CHANNEL_A1.remoteAddress() ) ;

    sessionBook.create( ReactiveSessionFixture.SESSION_2, ReactiveSessionFixture.CHANNEL_B1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_2 ) ;
    Assertions.assertThat( sessionBook.activate( ReactiveSessionFixture.SESSION_2, ReactiveSessionFixture.CHANNEL_B1, ReactiveSessionFixture.T_3, false ).userIdentity )
        .isEqualTo( ReactiveSessionFixture.USER_X ) ;

    final SessionDetail.Active activated2 = sessionDetail( ReactiveSessionFixture.SESSION_2 ) ;
    assertThat( activated2.sessionIdentifier ).isEqualTo( ReactiveSessionFixture.SESSION_2 ) ;
    assertThat( activated2.channel ).isEqualTo( ReactiveSessionFixture.CHANNEL_B1 ) ;
    assertThat( activated2.inactiveSince ).isNull() ;
    assertThat( activated2.remoteAddress ).isNull() ;
    assertThat( activated2.user ).isEqualTo( ReactiveSessionFixture.USER_X ) ;

  }

  @Test
  public void sessionRemoval() throws Exception {
    sessionBook.create( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.USER_X, ReactiveSessionFixture.T_0 ) ;
    sessionBook.activate( ReactiveSessionFixture.SESSION_1, ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_1, false ) ;
    final ReactiveSessionFixture.MyChannel removed1 = sessionBook.removeSession( ReactiveSessionFixture.SESSION_1 ) ;
    Assertions.assertThat( removed1 ).isEqualTo( ReactiveSessionFixture.CHANNEL_A1 ) ;
    assertThat( sessionBook.removeChannel( ReactiveSessionFixture.CHANNEL_A1, ReactiveSessionFixture.T_2 ) ).isFalse() ;
    Assertions.assertThat( sessionBook.removeSession( ReactiveSessionFixture.SESSION_1 ) ).isNull() ;
  }


// =======
// Fixture
// =======

  private final Duration MAXIMUM_ORPHANHOOD_DURATION = new Duration( 2 ) ;
  private final SessionBook<SessionIdentifier, ReactiveSessionFixture.MyChannel, ReactiveSessionFixture.MyAddress> sessionBook =
      new SessionBook<>( ReactiveSessionFixture.MyChannel::remoteAddress, MAXIMUM_ORPHANHOOD_DURATION ) ;

  private  < DETAIL extends SessionDetail > DETAIL sessionDetail(
      final SessionIdentifier sessionIdentifier
  ) {
    return ( DETAIL ) sessionBook.sessionDetail( sessionIdentifier ) ;
  }
}