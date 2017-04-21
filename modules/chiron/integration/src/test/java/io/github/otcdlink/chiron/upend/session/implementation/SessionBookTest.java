package io.github.otcdlink.chiron.upend.session.implementation;

import io.github.otcdlink.chiron.integration.ReactiveSessionFixture.MyAddress;
import io.github.otcdlink.chiron.integration.ReactiveSessionFixture.MyChannel;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.session.SignonFailure;
import io.github.otcdlink.chiron.middle.session.SignonFailureNotice;
import org.joda.time.Duration;
import org.junit.Test;

import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.CHANNEL_A1;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.CHANNEL_B1;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.CHANNEL_C3;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.SESSION_1;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.SESSION_2;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.SESSION_IDENTIFIER_2;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.T_0;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.T_1;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.T_2;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.T_3;
import static io.github.otcdlink.chiron.integration.ReactiveSessionFixture.USER_X;
import static org.assertj.core.api.Assertions.assertThat;

public class SessionBookTest {

  @Test
  public void createActivateRemove() throws Exception {

    assertThat( sessionBook.create( SESSION_1, CHANNEL_A1, USER_X, T_0 ) ).isNull() ;
    final SessionDetail.Pending pending = sessionDetail( SESSION_1 ) ;
    assertThat( pending.creationTime ).isEqualTo( T_0 ) ;
    assertThat( pending.channel ).isEqualTo( CHANNEL_A1 ) ;
    assertThat( pending.inactiveSince ).isEqualTo( T_0 ) ;

    assertThat( sessionBook.activate( SESSION_1, CHANNEL_A1, T_1, false ).userIdentity )
        .isEqualTo( USER_X ) ;
    final SessionDetail.Active active = sessionDetail( SESSION_1 ) ;
    assertThat( active.creationTime ).isEqualTo( T_0 ) ;
    assertThat( active.channel ).isEqualTo( CHANNEL_A1 ) ;
    assertThat( active.inactiveSince ).isNull() ;
    
    assertThat( sessionBook.removeChannel( CHANNEL_A1, null ) ).isTrue() ;
    assertThat( ( SessionDetail ) sessionDetail( SESSION_1 ) ).isNull() ;

    assertThat( sessionBook.removeChannel( CHANNEL_A1, T_2 ) )
        .describedAs( "Removal can occur only once, then it fails quietly" )
        .isFalse()
    ;
  }

  @Test
  public void twoSessionsForTheSameUser() throws Exception {
    assertThat( sessionBook.create( SESSION_1, CHANNEL_A1, USER_X, T_0 ) ).isNull() ;

    final SignonFailureNotice sessionCreationFailureNotice =
        sessionBook.create( SESSION_IDENTIFIER_2, CHANNEL_A1, USER_X, T_0 ) ;
    assertThat( sessionCreationFailureNotice.kind )
        .isEqualTo( SignonFailure.SESSION_ALREADY_ATTRIBUTED ) ;
  }

  @Test
  public void activateTooLate() throws Exception {

    sessionBook.create( SESSION_1, CHANNEL_A1, USER_X, T_0 ) ;

    assertThat( sessionBook.activate( SESSION_1, CHANNEL_A1, T_3, false ).signonFailureNotice.kind )
        .isEqualTo( SignonFailure.UNKNOWN_SESSION ) ;
    assertThat( ( SessionDetail ) sessionDetail( SESSION_1 ) ).isNull() ;
  }

  @Test
  public void onlyOnceChanceToActivate() throws Exception {

    sessionBook.create( SESSION_1, CHANNEL_A1, USER_X, T_0 ) ;

    assertThat( sessionBook.activate( SESSION_1, CHANNEL_C3, T_0, false ).signonFailureNotice.kind )
        .describedAs( "Activating with a Channel that has a different remote address" )
        .isEqualTo( SignonFailure.UNMATCHED_NETWORK_ADDRESS ) ;
    assertThat( ( SessionDetail ) sessionDetail( SESSION_1 ) ).isNull() ;
  }

  @Test
  public void reuseTooLate() throws Exception {

    sessionBook.create( SESSION_1, CHANNEL_A1, USER_X, T_0 ) ;

    sessionBook.activate( SESSION_1, CHANNEL_A1, T_0, false ) ;

    sessionBook.removeChannel( CHANNEL_A1, T_0 ) ;

    assertThat( sessionBook.reuse( SESSION_1, CHANNEL_B1, T_3 ).signonFailureNotice.kind )
        .isEqualTo( SignonFailure.UNKNOWN_SESSION) ;
    assertThat( ( SessionDetail ) sessionDetail( SESSION_1 ) ).isNull() ;
  }

  @Test
  public void onlyOneChanceToReuse() throws Exception {

    sessionBook.create( SESSION_1, CHANNEL_A1, USER_X, T_0 ) ;

    sessionBook.activate( SESSION_1, CHANNEL_A1, T_0, false ) ;

    sessionBook.removeChannel( CHANNEL_A1, T_0 ) ;

    assertThat( sessionBook.reuse( SESSION_1, CHANNEL_C3, T_1 ).signonFailureNotice.kind )
        .describedAs( "Reusing with a Channel that has a different remote address" )
        .isEqualTo( SignonFailure.UNMATCHED_NETWORK_ADDRESS )
    ;
    assertThat( ( SessionDetail ) sessionDetail( SESSION_1 ) ).isNull() ;
  }


    @Test
  public void orphanhoodAndReuse() throws Exception {

    sessionBook.create( SESSION_1, CHANNEL_A1, USER_X, T_0 ) ;

    sessionBook.activate( SESSION_1, CHANNEL_A1, T_0, false ) ;

    assertThat( sessionBook.removeChannel( CHANNEL_A1, T_1 ) ).isTrue() ;
    final SessionDetail.Orphaned orphaned = sessionDetail( SESSION_1 ) ;
    assertThat( orphaned.inactiveSince ).isEqualTo( T_1 ) ;
    assertThat( orphaned.remoteAddress ).isEqualTo( CHANNEL_A1.remoteAddress() ) ;

    assertThat( sessionBook.reuse( SESSION_1, CHANNEL_B1, T_2 ).signonFailureNotice ).isNull() ;
    final SessionDetail.Reusing reusing = sessionDetail( SESSION_1 ) ;
    assertThat( reusing.sessionIdentifier ).isEqualTo( SESSION_1 ) ;
    assertThat( reusing.channel ).isEqualTo( CHANNEL_B1 ) ;
    assertThat( reusing.inactiveSince ).isEqualTo( T_1 ) ;
    assertThat( reusing.remoteAddress ).isNull() ;
    assertThat( reusing.user ).isEqualTo( USER_X ) ;

    assertThat( sessionBook.activate( SESSION_1, CHANNEL_B1, T_3, true ).userIdentity )
        .isEqualTo( USER_X ) ;
    final SessionDetail.Active reactivated = sessionDetail( SESSION_1 ) ;
    assertThat( reactivated.sessionIdentifier ).isEqualTo( SESSION_1 ) ;
    assertThat( reactivated.channel ).isEqualTo( CHANNEL_B1 ) ;
    assertThat( reactivated.inactiveSince ).isNull() ;
    assertThat( reactivated.remoteAddress ).isNull() ;
    assertThat( reactivated.user ).isEqualTo( USER_X ) ;

  }
    @Test
  public void orphanhoodAndRecreate() throws Exception {

    sessionBook.create( SESSION_1, CHANNEL_A1, USER_X, T_0 ) ;
    sessionBook.activate( SESSION_1, CHANNEL_A1, T_0, false ) ;

    assertThat( sessionBook.removeChannel( CHANNEL_A1, T_1 ) ).isTrue() ;
    final SessionDetail.Orphaned orphaned = sessionDetail( SESSION_1 ) ;
    assertThat( orphaned.inactiveSince ).isEqualTo( T_1 ) ;
    assertThat( orphaned.remoteAddress ).isEqualTo( CHANNEL_A1.remoteAddress() ) ;

    sessionBook.create( SESSION_2, CHANNEL_B1, USER_X, T_2 ) ;
    assertThat( sessionBook.activate( SESSION_2, CHANNEL_B1, T_3, false ).userIdentity )
        .isEqualTo( USER_X ) ;

    final SessionDetail.Active activated2 = sessionDetail( SESSION_2 ) ;
    assertThat( activated2.sessionIdentifier ).isEqualTo( SESSION_2 ) ;
    assertThat( activated2.channel ).isEqualTo( CHANNEL_B1 ) ;
    assertThat( activated2.inactiveSince ).isNull() ;
    assertThat( activated2.remoteAddress ).isNull() ;
    assertThat( activated2.user ).isEqualTo( USER_X ) ;

  }

  @Test
  public void sessionRemoval() throws Exception {
    sessionBook.create( SESSION_1, CHANNEL_A1, USER_X, T_0 ) ;
    sessionBook.activate( SESSION_1, CHANNEL_A1, T_1, false ) ;
    final MyChannel removed1 = sessionBook.removeSession( SESSION_1 ) ;
    assertThat( removed1 ).isEqualTo( CHANNEL_A1 ) ;
    assertThat( sessionBook.removeChannel( CHANNEL_A1, T_2 ) ).isFalse() ;
    assertThat( sessionBook.removeSession( SESSION_1 ) ).isNull() ;
  }


// =======
// Fixture
// =======

  private final Duration MAXIMUM_ORPHANHOOD_DURATION = new Duration( 2 ) ;
  private final SessionBook< SessionIdentifier, MyChannel, MyAddress > sessionBook =
      new SessionBook<>( MyChannel::remoteAddress, MAXIMUM_ORPHANHOOD_DURATION ) ;

  private  < DETAIL extends SessionDetail > DETAIL sessionDetail(
      final SessionIdentifier sessionIdentifier
  ) {
    return ( DETAIL ) sessionBook.sessionDetail( sessionIdentifier ) ;
  }
}