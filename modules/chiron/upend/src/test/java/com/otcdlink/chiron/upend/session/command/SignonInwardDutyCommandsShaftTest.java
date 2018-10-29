package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignonSetback;
import com.otcdlink.chiron.middle.shaft.CodecShaft;
import com.otcdlink.chiron.middle.shaft.CrafterShaft;
import com.otcdlink.chiron.middle.shaft.MethodCallVerifier;
import com.otcdlink.chiron.middle.shaft.MethodCaller;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;
import org.junit.Test;

public class SignonInwardDutyCommandsShaftTest {

  @Test
  public void transientMethodsWithCrafterShaft() throws Exception {
    new CrafterShaft<>( SignonInwardDutyCrafter::new, DESIGNATOR )
        .submit( TRANSIENT_METHODS_CALLER, VERIFIER ) ;
  }

  @Test
  public void persistableMethodsWithCodecShaft() throws Exception {
    new CodecShaft<>(
        commandConsumer -> new SignonInwardDutyCrafter( ( CommandConsumer ) commandConsumer ),
        SignonInwardDutyResolver.INSTANCE,
        DESIGNATOR
    ).submit( INTERNAL_METHODS_CALLER, VERIFIER ) ;
  }


// ==============
// Method callers
// ==============

  private static final MethodCaller< SignonInwardDuty > TRANSIENT_METHODS_CALLER =
      new MethodCaller.Default<SignonInwardDuty>() {
        @Override
        public void callMethods( final SignonInwardDuty signonInwardDuty ) {
          signonInwardDuty.primarySignonAttempt( DESIGNATOR, "TheLogin", "ThePassword" ) ;
          signonInwardDuty.secondarySignonAttempt( DESIGNATOR, "TheLogin" ) ;
        }
      }
  ;

  private static final MethodCaller< SignonInwardDuty > INTERNAL_METHODS_CALLER =
      new MethodCaller.Default< SignonInwardDuty >() {
        @Override
        public void callMethods( final SignonInwardDuty signonInwardDuty ) {
          signonInwardDuty.registerSession(
              DESIGNATOR, new SessionIdentifier( "The5e5510n" ), "TheLogin" ) ;
          signonInwardDuty.failedSignonAttempt(
              DESIGNATOR, "TheLogin", SignonSetback.Factor.PRIMARY ) ;
          signonInwardDuty.resetSignonFailures( DESIGNATOR, "TheLogin" ) ;
          signonInwardDuty.signout( DESIGNATOR ) ;
          signonInwardDuty.signoutQuiet( DESIGNATOR, SESSION_IDENTIFIER ) ;
          signonInwardDuty.signoutAll( DESIGNATOR ) ;
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

  private static final SessionIdentifier SESSION_IDENTIFIER = new SessionIdentifier( "535510n" ) ;

}