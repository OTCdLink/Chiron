package io.github.otcdlink.chiron.upend.session;

import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.designator.RenderingAwareDesignator;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.session.SignonDecision;
import io.github.otcdlink.chiron.middle.session.SignonFailure;
import io.github.otcdlink.chiron.middle.session.SignonFailureNotice;
import io.github.otcdlink.chiron.upend.session.command.SignonInwardDutyPrimarySignonAttempt;
import io.github.otcdlink.chiron.upend.session.command.SignonInwardDutySecondarySignonAttempt;
import io.github.otcdlink.chiron.upend.session.implementation.DefaultSessionSupervisor;

/**
 * Contract for user authentication, to be supported by Upend Logic.
 * {@link SessionSupervisor} implementor will rely on {@link SignonInwardDuty}.
 * Methods here have no visible output, if there is some output it happens with a call to
 * {@link SignonOutwardDuty}.
 */
public interface SignonInwardDuty {

  /**
   * Verifies if a User has the right to log in.
   * Must have no side-effect because {@link SignonInwardDutyPrimarySignonAttempt} doesn't persist.
   * Not persisting Signon attempts permits to not saturate the persistence in case of a brute-force
   * attack. Only Session creation persists.
   *
   * @param designatorInternal a {@link Designator} to propagate as it is because implementor
   *     can use a subclass to put their own stuff into.
   *
   * @see SessionSupervisor#attemptSecondarySignon(Object, Object, SecondaryToken, SecondaryCode, SessionSupervisor.SecondarySignonAttemptCallback)
   * @see SignonOutwardDuty#primarySignonAttempted(Designator, SignonDecision)
   */
  void primarySignonAttempt(
      Designator designatorInternal,
      String login,
      String password
  ) ;

  /**
   * Verifies if a User has the right to run Secondary authentication.
   * Must have no side-effect because {@link SignonInwardDutySecondarySignonAttempt} doesn't persist.
   * Not persisting Signon attempts permits to not saturate the persistence in case of a brute-force
   * attack. Only Session creation persists.
   *
   * @param designatorInternal a {@link Designator} to propagate as it is because implementor
   *     can use a subclass to put their own stuff into.
   *
   * @see SessionSupervisor#attemptSecondarySignon(Object, Object, SecondaryToken, SecondaryCode, SessionSupervisor.SecondarySignonAttemptCallback)
   * @see SignonOutwardDuty#secondarySignonAttempted(Designator, SignonFailureNotice)
   */
  void secondarySignonAttempt(
      Designator designatorInternal,
      String login
  ) ;

  /**
   * Increments some counter about failed signon attempts, so
   * {@link #primarySignonAttempt(Designator, String, String)}
   * and {@link #secondarySignonAttempt(Designator, String)}
   * can do their job.
   * Since {@link #primarySignonAttempt(Designator, String, String)} and
   * {@link #secondarySignonAttempt(Designator, String)} have no side-effect,
   * it is the responsability of the calling code to call this method if Signon failed
   * in a manner that requires to increment Signon failure counters, namely
   * {@link SignonFailure#INVALID_CREDENTIAL} or {@link SignonFailure#INVALID_SECONDARY_CODE}.
   *
   */
  void failedSignonAttempt(
      Designator designatorInternal,
      String login,
      SignonAttempt signonAttempt
  ) ;

  /**
   * Persisted.
   * This command may fail at least because of {@link SignonFailure#SESSION_ALREADY_EXISTS}.
   *
   * @see SignonOutwardDuty#sessionCreated(Designator, SessionIdentifier, String)
   */
  void registerSession(
      Designator designatorInternal,
      SessionIdentifier sessionIdentifier,
      String login
  ) ;

  /**
   * @param designatorInternal must have a non-null {@link Designator#sessionIdentifier}.
   */
  void signout( Designator designatorInternal ) ;

  /**
   * Like {@link #signout(Designator)} but causes no call to
   * {@link SignonOutwardDuty#terminateSession(Designator, SessionIdentifier)}.
   * Only {@link DefaultSessionSupervisor}
   * should call this method, after it already terminated the session on its side.
   * @param designator hints about caller, may be a {@link RenderingAwareDesignator} (with a {@code null}
   *     {@link SessionIdentifier}.
   * @param sessionIdentifier a non-{@code null} value.
   */
  void signoutQuiet( Designator designator, SessionIdentifier sessionIdentifier ) ;

  void signoutAll( Designator designatorInternal ) ;

  void resetSignonFailures( Designator designatorInternal, String login ) ;


}
