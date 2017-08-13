package com.otcdlink.chiron.upend.session;

import com.otcdlink.chiron.command.Command.Tag;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.toolbox.StringWrapper;
import com.otcdlink.chiron.toolbox.clock.Clock;
import mockit.Mocked;
import mockit.Verifications;
import org.assertj.core.util.Preconditions;
import org.junit.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SessionStore}.
 */
public class SessionStoreTest {

  @Test
  public void userkey() throws Exception {
    store.putSession( SESSION_IDENTIFIER_A, USER_SESSION_1 ) ;
    store.putSession( SESSION_IDENTIFIER_B, USER_SESSION_2 ) ;
    assertThat( store.getUserKey( DESIGNATOR_UPWARD_A ) ).isEqualTo( USER_KEY_1 ) ;
  }

  @Test( expected = SessionStore.SessionAlreadyExistsException.class )
  public void sessionAlreadyExists() throws Exception {
    store.putSession( SESSION_IDENTIFIER_A, USER_SESSION_1 ) ;
    store.putSession( SESSION_IDENTIFIER_A, USER_SESSION_2 ) ;
  }

  @Test( expected = SessionStore.MoreThanOneSessionException.class )
  public void noMoreThanOneSessionPerUser() throws Exception {
    store.putSession( SESSION_IDENTIFIER_A, USER_SESSION_1 ) ;
    store.putSession( SESSION_IDENTIFIER_B, USER_SESSION_1 ) ;
  }

  @SuppressWarnings( "TestMethodWithIncorrectSignature" )
  @Test
  public void putAndVisit( @Mocked final SessionStore.Visitor< PrivateUserKey > visitor ) {
    store.putSession( SESSION_IDENTIFIER_A, USER_SESSION_1 ) ;
    store.putSession( SESSION_IDENTIFIER_B, USER_SESSION_2 ) ;

    store.visitSessions( visitor, DESIGNATOR_UPWARD_A ) ;

    new Verifications() { {
      Supplier< Designator > downward ;
      visitor.visit( SESSION_IDENTIFIER_A, USER_KEY_1, downward = withCapture(), true ) ;
      assertThat( downward.get() ).isEqualTo( DesignatorForger.newForger()
          .session( SESSION_IDENTIFIER_A )
          .cause( DESIGNATOR_UPWARD_A.stamp )
          .tag( DESIGNATOR_UPWARD_A.tag )
          .instant( CLOCK.getCurrentDateTime() )
          .counter( 1 )
          .downward()
      ) ;

      visitor.visit( SESSION_IDENTIFIER_B, USER_KEY_2, downward = withCapture(), false ) ;
      assertThat( downward.get() ).isEqualTo( DesignatorForger.newForger()
          .session( SESSION_IDENTIFIER_B )
          .cause( DESIGNATOR_UPWARD_A.stamp )
          .instant( CLOCK.getCurrentDateTime() )
          .counter( 2 )
          .downward()
      ) ;
    } } ;

    store.removeSession( SESSION_IDENTIFIER_A ) ;
    store.removeSession( SESSION_IDENTIFIER_B ) ;
  }

  @Test
  public void uniqueSession() throws Exception {
    store.putSession( SESSION_IDENTIFIER_A, USER_SESSION_1 ) ;
    assertThat( store.getUniqueSession( USER_KEY_1 ) ).isEqualTo( SESSION_IDENTIFIER_A ) ;
  }

  @Test( expected = SessionStore.MoreThanOneSessionException.class )
  public void enforeSessionUniquenessPerUser() throws Exception {
    store.putSession( SESSION_IDENTIFIER_A, USER_SESSION_1 ) ;
    store.putSession( SESSION_IDENTIFIER_B, USER_SESSION_1 ) ;
    store.getUniqueSession( USER_KEY_1 ) ;
  }

  @SuppressWarnings( "TestMethodWithIncorrectSignature" )
  @Test
  public void putAndRemove( @Mocked final SessionStore.Visitor visitor ) throws Exception {
    store.putSession( SESSION_IDENTIFIER_A, USER_SESSION_1 ) ;
    store.putSession( SESSION_IDENTIFIER_B, USER_SESSION_2 ) ;

    store.removeSession( SESSION_IDENTIFIER_A ) ;
    store.removeSession( SESSION_IDENTIFIER_B ) ;

    store.visitSessions( visitor, DESIGNATOR_UPWARD_A ) ;
    new Verifications() {  } ;
  }

  @SuppressWarnings( "TestMethodWithIncorrectSignature" )
  @Test
  public void removeAll( @Mocked final SessionStore.Visitor visitor ) throws Exception {
    store.putSession( SESSION_IDENTIFIER_A, USER_SESSION_1 ) ;
    store.removeAll() ;

    store.visitSessions( visitor, DESIGNATOR_UPWARD_A ) ;
    new Verifications() {  } ;
  }



// =======
// Fixture
// =======

  private final Clock CLOCK = () -> Stamp.FLOOR_MILLISECONDS ;
  private final Designator.Factory designatorFactory = new Designator.Factory(
      new Stamp.Generator( CLOCK ) ) ;
  private final SessionStore< PrivateUserKey, PrivateUserSession > store =
      new SessionStore<>( designatorFactory ) ;

  private static final class PrivateUserKey extends StringWrapper< PrivateUserKey > {
    public PrivateUserKey( final String wrapped ) {
      super( wrapped ) ;
    }
  }

  private static final class PrivateUserSession
      implements SessionStore.UserSession< PrivateUserKey >
  {

    private final PrivateUserKey privateUserKey ;

    private PrivateUserSession( final PrivateUserKey privateUserKey ) {
      this.privateUserKey = Preconditions.checkNotNull( privateUserKey ) ;
    }

    @Override
    public PrivateUserKey userKey() {
      return privateUserKey ;
    }
  }

  private static final SessionIdentifier SESSION_IDENTIFIER_A = new SessionIdentifier( "A" ) ;
  private static final SessionIdentifier SESSION_IDENTIFIER_B = new SessionIdentifier( "B" ) ;

  private static final PrivateUserKey USER_KEY_1 = new PrivateUserKey( "1" ) ;
  private static final PrivateUserKey USER_KEY_2 = new PrivateUserKey( "2" ) ;

  private static final PrivateUserSession USER_SESSION_1 = new PrivateUserSession( USER_KEY_1 ) ;
  private static final PrivateUserSession USER_SESSION_2 = new PrivateUserSession( USER_KEY_2 ) ;


  private final Designator DESIGNATOR_UPWARD_A = designatorFactory.upward(
      new Tag( "t" ), SESSION_IDENTIFIER_A ) ;


}
