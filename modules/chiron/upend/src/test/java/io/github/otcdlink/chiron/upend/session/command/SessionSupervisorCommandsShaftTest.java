package io.github.otcdlink.chiron.upend.session.command;

import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.designator.DesignatorForger;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.session.SignonFailureNotice;
import io.github.otcdlink.chiron.middle.shaft.CrafterShaft;
import io.github.otcdlink.chiron.middle.shaft.MethodCallVerifier;
import io.github.otcdlink.chiron.middle.shaft.MethodCaller;
import io.github.otcdlink.chiron.toolbox.StringWrapper;
import io.github.otcdlink.chiron.upend.session.SessionSupervisor;
import io.github.otcdlink.chiron.upend.session.SignableUser;
import org.junit.Test;

public class SessionSupervisorCommandsShaftTest {

  @Test
  public void transientMethodsWithCrafterShaft() throws Exception {
    new CrafterShaft<
        Designator,
        SessionSupervisor< PrivateChannel, PrivateAddress >
    >( SessionSupervisorCrafter::new, DESIGNATOR )
        .submit( TRANSIENT_METHODS_CALLER, VERIFIER )
    ;
  }


// ==============
// Method callers
// ==============

  private static final MethodCaller< SessionSupervisor< PrivateChannel, PrivateAddress > >
      TRANSIENT_METHODS_CALLER =
          new MethodCaller.Default< SessionSupervisor< PrivateChannel, PrivateAddress > >() {
            @Override
            public void callMethods(
                final SessionSupervisor< PrivateChannel, PrivateAddress > sessionSupervisor
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

  private static final SessionSupervisor.PrimarySignonAttemptCallback
      PRIMARY_SIGNON_ATTEMPT_CALLBACK =
          new SessionSupervisor.PrimarySignonAttemptCallback() {
            @Override
            public void signonResult( final SignonFailureNotice Ø ) { }

            @Override
            public void needSecondarySignon(
                final SignableUser Ø,
                final SecondaryToken ØØ
            ) { }

            @Override
            public void sessionAttributed( final SessionIdentifier Ø ) { }
          }
  ;

  private static final SessionSupervisor.SecondarySignonAttemptCallback
      SECONDARY_SIGNON_ATTEMPT_CALLBACK =
          new SessionSupervisor.SecondarySignonAttemptCallback() {
            @Override
            public void sessionAttributed( final SessionIdentifier Ø ) { }

            @Override
            public void signonResult( final SignonFailureNotice Ø ) { }
          }
  ;
}