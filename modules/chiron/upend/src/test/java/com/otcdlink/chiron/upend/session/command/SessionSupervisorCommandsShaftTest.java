package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignableUser;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.middle.shaft.CrafterShaft;
import com.otcdlink.chiron.middle.shaft.MethodCallVerifier;
import com.otcdlink.chiron.middle.shaft.MethodCaller;
import com.otcdlink.chiron.toolbox.StringWrapper;
import com.otcdlink.chiron.upend.session.SessionSupervisor;
import org.junit.Test;

public class SessionSupervisorCommandsShaftTest {

  @Test
  public void transientMethodsWithCrafterShaft() throws Exception {
    new CrafterShaft<
        Designator,
        SessionSupervisor< PrivateChannel, PrivateAddress, Void >
    >( SessionSupervisorCrafter::new, DESIGNATOR )
        .submit( TRANSIENT_METHODS_CALLER, VERIFIER )
    ;
  }


// ==============
// Method callers
// ==============

  private static final MethodCaller< SessionSupervisor< PrivateChannel, PrivateAddress, Void > >
      TRANSIENT_METHODS_CALLER =
          new MethodCaller.Default< SessionSupervisor< PrivateChannel, PrivateAddress, Void > >() {
            @Override
            public void callMethods(
                final SessionSupervisor< PrivateChannel, PrivateAddress, Void > sessionSupervisor
            ) {
//              sessionSupervisor.attemptPrimarySignon(
//                  "TheLogin",
//                  "ThePassword",
//                  CHANNEL,
//                  ADDRESS,
//                  SessionSupervisor.ChannelRole.READ_ONLY,
//                  PRIMARY_SIGNON_ATTEMPT_CALLBACK
//              ) ;
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
  
  private static final class PrivateAddress extends StringWrapper< PrivateAddress > {

    protected PrivateAddress( final String wrapped ) {
      super( wrapped ) ;
    }
  }

  private static final class PrivateChannel extends StringWrapper< PrivateChannel > {

    protected PrivateChannel( final String wrapped ) {
      super( wrapped ) ;
    }
  }

  private static final PrivateChannel CHANNEL = new PrivateChannel( "Ch4nn3L" ) ;
  private static final PrivateAddress ADDRESS = new PrivateAddress( "4ddr355" ) ;

  private static final SessionSupervisor.PrimarySignonAttemptCallback< Void >
      PRIMARY_SIGNON_ATTEMPT_CALLBACK =
          new SessionSupervisor.PrimarySignonAttemptCallback< Void >() {
            @Override
            public void signonResult( final SignonFailureNotice Ø ) { }

            @Override
            public void needSecondarySignon(
                final SignableUser Ø,
                final SecondaryToken ØØ
            ) { }

            @Override
            public void sessionAttributed( final SessionIdentifier Ø, final Void ØØ ) { }
          }
  ;

  private static final SessionSupervisor.SecondarySignonAttemptCallback< Void >
      SECONDARY_SIGNON_ATTEMPT_CALLBACK =
          new SessionSupervisor.SecondarySignonAttemptCallback< Void >() {
            @Override
            public void sessionAttributed( final SessionIdentifier Ø, final Void ØØ ) { }

            @Override
            public void signonResult( final SignonFailureNotice Ø ) { }
          }
  ;
}