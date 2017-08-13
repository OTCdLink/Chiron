package com.otcdlink.chiron.integration;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.middle.PhoneNumber;
import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.toolbox.StringWrapper;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.upend.session.SignableUser;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.util.Preconditions.checkNotNull;

public final class ReactiveSessionFixture {
  private ReactiveSessionFixture() { }

  public static final SessionIdentifier SESSION_1 = new SessionIdentifier( "One" ) ;
  public static final SessionIdentifier SESSION_2 = new SessionIdentifier( "Two" ) ;
  public static final SessionIdentifier SESSION_IDENTIFIER_2 = new SessionIdentifier( "Two" ) ;
  public static final SessionIdentifier SESSION_IDENTIFIER_3 = new SessionIdentifier( "Three" ) ;
  public static final SecondaryToken SECONDARY_TOKEN_1 = new SecondaryToken( "Token1" ) ;
  public static final SecondaryCode SECONDARY_CODE_1 = new SecondaryCode( "2code1" ) ;
  public static final MyUser USER_X = new MyUser( "Xxx", "+360000000" ) ;
  public static final MyChannel CHANNEL_A1 = new MyChannel( "Aaa/1.1.1.1" ) ;
  public static final MyChannel CHANNEL_B1 = new MyChannel( "Bbb/1.1.1.1" ) ;
  public static final MyChannel CHANNEL_C3 = new MyChannel( "Ccc/3.3.3.3" ) ;
  public static final MyAddress ADDRESS_1 = newUnresolvedIpv4Address( "1.1.1.1" ) ;
  public static final MyAddress ADDRESS_2 = newUnresolvedIpv4Address( "2.2.2.2" ) ;
  public static final DateTime T_0 = new DateTime( Stamp.FLOOR_MILLISECONDS + 0 ) ;
  public static final DateTime T_1 = new DateTime( Stamp.FLOOR_MILLISECONDS + 1 ) ;
  public static final DateTime T_2 = new DateTime( Stamp.FLOOR_MILLISECONDS + 2 ) ;
  public static final DateTime T_3 = new DateTime( Stamp.FLOOR_MILLISECONDS + 3 ) ;
  public static final DateTime T_4 = new DateTime( Stamp.FLOOR_MILLISECONDS + 4 ) ;

  public static final DateTime T_5 = new DateTime( Stamp.FLOOR_MILLISECONDS + 5 ) ;


  public static final SessionLifecycle.PrimarySignon USER_X_PRIMARY_SIGNON_GOOD =
      SessionLifecycle.PrimarySignon.create(
          ReactiveSessionFixture.USER_X.login(),
          ReactiveSessionFixture.USER_X.password()
      )
  ;
  public static final SessionLifecycle.SecondarySignon USER_X_SECONDARY_SIGNON_GOOD =
      SessionLifecycle.SecondarySignon.create(
          SECONDARY_TOKEN_1,
          SECONDARY_CODE_1
      )
  ;

  public static final SessionLifecycle.PrimarySignon
      USER_X_PRIMARY_SIGNON_GOOD_WITH_MODIFY_CHANNEL_ROLE = USER_X_PRIMARY_SIGNON_GOOD ;

  public static final SessionLifecycle.SecondarySignon
      USER_X_SECONDARY_SIGNON_GOOD_WITH_MODIFY_CHANNEL_ROLE = USER_X_SECONDARY_SIGNON_GOOD ;


  public static final SessionLifecycle.Resignon USER_X_RESIGNON_GOOD =
      SessionLifecycle.Resignon.create( SESSION_1 ) ;

  public static final SessionLifecycle.Resignon
      USER_X_RESIGNON_GOOD_WITH_MODIFY_CHANNEL_ROLE = USER_X_RESIGNON_GOOD ;

  private static MyAddress newUnresolvedIpv4Address( final String text ) {
    return new MyAddress( text ) ;
  }

  public static class MyChannel extends StringWrapper< MyChannel > {
    protected MyChannel( final String wrapped ) {
      super( wrapped ) ;
      checkArgument(
          wrapped.indexOf( '/' ) > 0,
          "Should contain address under the form '<whatever>/<remoteAddress>', " +
              "got '" + wrapped + "'"
      ) ;
    }

    public MyAddress remoteAddress() {
      return new MyAddress( wrapped.substring( wrapped.indexOf( '/' ) + 1 ) ) ;
    }
  }

  public static class MyUser implements SignableUser {
    private final String login ;
    private final PhoneNumber phoneNumber ;

    protected MyUser( final String login, final String phoneNumber ) {
      this( login, new PhoneNumber( phoneNumber ) ) ;
    }

    protected MyUser( final String login, final PhoneNumber phoneNumber ) {
      this.login = checkNotNull( login ) ;
      this.phoneNumber = checkNotNull( phoneNumber ) ;
    }

    @Override
    public String login() {
      return login ;
    }

    public String password() {
      return "Password" ;
    }

    @Override
    public PhoneNumber phoneNumber() {
      return phoneNumber ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }

      final MyUser that = ( MyUser ) other ;

      if( ! login.equals( that.login ) ) return false ;
      return phoneNumber.equals( that.phoneNumber ) ;

    }

    @Override
    public int hashCode() {
      int result = login.hashCode() ;
      result = 31 * result + phoneNumber.hashCode() ;
      return result ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + '{' +
          login + ';' +
          phoneNumber.getAsString() +
          '}'
      ;
    }
  }

  public static class MyAddress extends StringWrapper< MyAddress > {
    protected MyAddress( final String wrapped ) {
      super( wrapped ) ;
    }
  }


  public static Stamp newStamp( final long counter ) {
    checkArgument( counter >= 0 ) ;
    return Stamp.raw( Stamp.FLOOR_MILLISECONDS, counter ) ;
  }


  public static class HasKind extends TypeSafeMatcher<SignonFailureNotice> {

    private final SignonFailure signonFailure ;

    private HasKind( final SignonFailure signonFailure ) {
      this.signonFailure = checkNotNull( signonFailure ) ;
    }

    @Override
    public void describeTo( final Description description) {
      description.appendText( "kind is " + ToStringTools.enumToString( signonFailure ) ) ; }

    @Factory
    public static Matcher< SignonFailureNotice > hasKind( final SignonFailure signonFailure ) {
      return new HasKind( signonFailure ) ;
    }

    @Override
    protected boolean matchesSafely( final SignonFailureNotice item ) {
      return item.kind == signonFailure ;
    }
  }
}
