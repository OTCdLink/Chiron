package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.middle.PhoneNumber;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignableUser;
import com.otcdlink.chiron.middle.session.SignonDecision;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.middle.shaft.CrafterShaft;
import com.otcdlink.chiron.middle.shaft.MethodCallVerifier;
import com.otcdlink.chiron.middle.shaft.MethodCaller;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;
import org.junit.Test;

public class SignonOutwardDutyCommandsShaftTest {

  @Test
  public void transientMethodsWithCrafterShaft() throws Exception {
    new CrafterShaft< Designator, SignonOutwardDuty< Dummy > >(
        SignonOutwardDutyCrafter::new,
        DESIGNATOR
    ).submit( TRANSIENT_METHODS_CALLER, VERIFIER ) ;
  }


// ==============
// Method callers
// ==============

  private static final MethodCaller< SignonOutwardDuty< Dummy > > TRANSIENT_METHODS_CALLER =
      new MethodCaller.Default< SignonOutwardDuty< Dummy > >() {
        @Override
        public void callMethods( final SignonOutwardDuty< Dummy > signonOutwardDuty ) {
          signonOutwardDuty.primarySignonAttempted(
              DESIGNATOR,
              new SignonDecision<>( SIGNABLE_USER )
          ) ;
          signonOutwardDuty.secondarySignonAttempted(
              DESIGNATOR,
              new SignonFailureNotice( SignonFailure.SESSION_ALREADY_EXISTS )
          ) ;
          signonOutwardDuty.sessionCreated(
              DESIGNATOR,
              new SessionIdentifier( "535510n" ),
              "TheLogin",
              Dummy.INSTANCE
          ) ;
          signonOutwardDuty.terminateSession(
              DESIGNATOR,
              new SessionIdentifier( "535510n" )
          ) ;
        }
      }
  ;

// =====
// Other
// =====

  /**
   * Skips the first parameter, which is a {@link Designator} on Upend and something else
   * on Downend.
   */
  private static final MethodCallVerifier VERIFIER = new MethodCallVerifier.Skipping( 0 ) ;

  private static final Designator DESIGNATOR = DesignatorForger
      .newForger().instant( Stamp.FLOOR ).internal() ;


  private static final SignableUser SIGNABLE_USER = new SignableUser() {
    @Override
    public String login() {
      return "TheLogin";
    }

    @Override
    public PhoneNumber phoneNumber() {
      return new PhoneNumber( "+36987654323" ) ;
    }
  } ;


  private static class Dummy {
    private Dummy() { }
    public static final Dummy INSTANCE = new Dummy() ;
  }

}