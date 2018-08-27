package com.otcdlink.chiron.upend.session;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignonDecision;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;

/**
 * Contract for passing results of {@link SignonInwardDuty} method calls.
 * This interface is typically implemented by a {@link SessionSupervisor} through
 * {@link OutwardSessionSupervisor}.
 */
public interface SignonOutwardDuty< SESSION_PRIMER > extends ElementarySignonOutwardDuty {


  /**
   * @see SignonInwardDuty#registerSession(Designator, SessionIdentifier, String)
   */
  void sessionCreated(
      Designator designatorInternal,
      SessionIdentifier sessionIdentifier,
      String login,
      SESSION_PRIMER sessionPrimer
  ) ;

  /**
   * Tells that something went wrong. This must not be called instead of
   * {@link #primarySignonAttempted(Designator, SignonDecision)} or
   * {@link #secondarySignonAttempted(Designator, SignonFailureNotice)}.
   *
   * @param designatorInternal a non-null object.
   * @param sessionIdentifier a non-null object.
   * @param signonFailureNotice a non-null object.
   */
  void sessionCreationFailed(
      Designator designatorInternal,
      SessionIdentifier sessionIdentifier,
      SignonFailureNotice signonFailureNotice
  ) ;

  void terminateSession(
      Designator designatorInternal,
      SessionIdentifier sessionIdentifier
  ) ;

}
