package com.otcdlink.chiron.upend.session.implementation;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.middle.PhoneNumber;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.toolbox.StringWrapper;
import com.otcdlink.chiron.upend.session.SignableUser;
import org.joda.time.DateTime;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionDetailTest {
  
  @Test
  public void pending() throws Exception {
    LOGGER.info( "Created " + PENDING + "." ) ;
    assertThat( PENDING.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( PENDING.creationTime ).isEqualTo( INSTANT_0 ) ;
    assertThat( PENDING.channel ).isEqualTo( CHANNEL_0 ) ;
    assertThat( PENDING.user).isEqualTo( USER_0 ) ;
    assertThat( PENDING.remoteAddress ).isNull() ;
    assertThat( PENDING.inactiveSince ).isEqualTo( INSTANT_0 ) ;
  }

  @Test
  public void active() throws Exception {
    LOGGER.info( "Created " + ACTIVE_1 + "." ) ;
    assertThat( ACTIVE_1.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( ACTIVE_1.creationTime ).isEqualTo( INSTANT_0 ) ;
    assertThat( ACTIVE_1.channel ).isEqualTo( CHANNEL_0 ) ;
    assertThat( ACTIVE_1.user).isEqualTo( USER_0 ) ;
    assertThat( ACTIVE_1.remoteAddress ).isNull() ;
    assertThat( ACTIVE_1.inactiveSince ).isNull() ;
  }

  @Test
  public void orphaned() throws Exception {
    LOGGER.info( "Created " + ORPHANED + "." ) ;
    assertThat( ORPHANED.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( ORPHANED.creationTime ).isEqualTo( INSTANT_0 ) ;
    assertThat( ORPHANED.channel ).isNull(); ;
    assertThat( ORPHANED.user).isEqualTo( USER_0 ) ;
    assertThat( ORPHANED.remoteAddress ).isEqualTo( ADDRESS_0 ) ;
    assertThat( ORPHANED.inactiveSince ).isEqualTo( INSTANT_1 ) ;
  }

  @Test
  public void reusing() throws Exception {
    LOGGER.info( "Created " + REUSING + "." ) ;
    assertThat( REUSING.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( REUSING.creationTime ).isEqualTo( INSTANT_0 ) ;
    assertThat( REUSING.channel ).isEqualTo( CHANNEL_1 ) ;
    assertThat( REUSING.user).isEqualTo( USER_0 ) ;
    assertThat( REUSING.remoteAddress ).isNull() ;
    assertThat( REUSING.inactiveSince )
        .isEqualTo( ORPHANED.inactiveSince )
        .isEqualTo( INSTANT_1 )
    ;

    LOGGER.info( "Created " + ACTIVE_2 + "." ) ;
    assertThat( ACTIVE_2.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( ACTIVE_2.creationTime ).isEqualTo( INSTANT_0 ) ;
    assertThat( ACTIVE_2.channel ).isEqualTo( CHANNEL_1 ) ;
    assertThat( ACTIVE_2.user).isEqualTo( USER_0 ) ;
    assertThat( ACTIVE_2.remoteAddress ).isNull() ;
    assertThat( ACTIVE_2.inactiveSince ).isNull() ;

  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( SessionDetailTest.class ) ;

  private static final SessionIdentifier SESSION_IDENTIFIER = new SessionIdentifier( "53ss10N" ) ;

  private static final DateTime INSTANT_0 = new DateTime( Stamp.FLOOR ) ;
  private static final DateTime INSTANT_1 = new DateTime( Stamp.FLOOR.plusMillis( 1 ) ) ;

  private static final MyAddress ADDRESS_0 = new MyAddress( "Address-0" ) ;

  private static final MyChannel CHANNEL_0 = new MyChannel( "Channel-0" ) ;
  private static final MyChannel CHANNEL_1 = new MyChannel( "Channel-1" ) ;


  private static class MyAddress extends StringWrapper< MyAddress > {
    protected MyAddress( final String wrapped ) {
      super( wrapped ) ;
    }
  }

  private static class MyChannel extends StringWrapper< MyChannel > {
    protected MyChannel( final String wrapped ) {
      super( wrapped ) ;
    }
  }

  private static final SignableUser USER_0 = new SignableUser() {
    @Override
    public String login() {
      return "User-0" ;
    }

    @Override
    public PhoneNumber phoneNumber() {
      throw new UnsupportedOperationException( "Do not call" ) ;
    }

    @Override
    public String toString() {
      return SignableUser.class.getSimpleName() + '{' + login() + '}' ;
    }
  } ;

  private static final SessionDetail.Pending< SessionIdentifier, MyChannel, MyAddress > PENDING =
      SessionDetail.pending( SESSION_IDENTIFIER, INSTANT_0, CHANNEL_0, USER_0 ) ;

  private static final SessionDetail.Active< SessionIdentifier, MyChannel, MyAddress > ACTIVE_1 =
      PENDING.activate() ;

  private static final SessionDetail.Orphaned< SessionIdentifier, MyChannel, MyAddress > ORPHANED =
      ACTIVE_1.orphaned( INSTANT_1, ADDRESS_0 ) ;

  private static final SessionDetail.Reusing< SessionIdentifier, MyChannel, MyAddress > REUSING =
      ORPHANED.reusing( CHANNEL_1 ) ;

  private static final SessionDetail.Active< SessionIdentifier, MyChannel, MyAddress > ACTIVE_2 =
      REUSING.activate() ;

}