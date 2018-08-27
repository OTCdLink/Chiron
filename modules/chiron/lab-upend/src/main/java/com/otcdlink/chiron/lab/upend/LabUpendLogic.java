package com.otcdlink.chiron.lab.upend;

import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.lab.middle.LabDownwardDuty;
import com.otcdlink.chiron.lab.middle.LabUpwardDuty;
import com.otcdlink.chiron.middle.PhoneNumber;
import com.otcdlink.chiron.middle.TechnicalFailureNotice;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignableUser;
import com.otcdlink.chiron.middle.session.SignonDecision;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.toolbox.StringWrapper;
import com.otcdlink.chiron.upend.TimeKit;
import com.otcdlink.chiron.upend.session.FailedSignonAttempt;
import com.otcdlink.chiron.upend.session.SessionStore;
import com.otcdlink.chiron.upend.session.SignonAttempt;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents Application logic on the Upend.
 * Signon stuff is a very minimalistic implementation.
 */
public class LabUpendLogic
    implements
    SignonInwardDuty,
    LabUpwardDuty< Designator >
{
  private static final Logger LOGGER = LoggerFactory.getLogger( LabUpendLogic.class ) ;

  private final TimeKit timeKit = TimeKit.fromSystemClock() ;

  private final SessionStore< LabUserKey, LabUserSession > sessionStore =
      new SessionStore<>( timeKit.designatorFactory ) ;

  private final Map< LabUserKey, FailedSignonAttempt> failedSignonAttempts = new HashMap<>() ;

  /**
   * User name, login, and password are all the same here.
   */
  public static final ImmutableSet< LabUserKey > USERS = ImmutableSet.of(
      new LabUserKey( "Alice" ),
      new LabUserKey( "Bob" )
  ) ;

  private int counter = 0 ;

  private final SignonOutwardDuty signonOutwardDuty ;
  private final LabDownwardDuty< Designator > labDownwardDuty ;

  public LabUpendLogic(
      final SignonOutwardDuty signonOutwardDuty,
      final LabDownwardDuty<Designator> labDownwardDuty
  ) {
    this.signonOutwardDuty = checkNotNull( signonOutwardDuty ) ;
    this.labDownwardDuty = checkNotNull( labDownwardDuty ) ;
    for( final LabUserKey labUserKey : USERS ) {
      failedSignonAttempts.put( labUserKey, FailedSignonAttempt.create() ) ;
    }
  }


// ====
// Duty
// ====

  @Override
  public void increment( final Designator upward, final int delta ) {
    if( delta <= 0 ) {
      labDownwardDuty.failure(
          timeKit.designatorFactory.downward( upward ),
          new TechnicalFailureNotice( TechnicalFailureNotice.Kind.SERVER_ERROR, "Bad delta" )
      ) ;
    } else {
      counter += delta ;
      LOGGER.debug( "Counter is now " + counter + " given " + upward + "." ) ;
      sessionStore.visitSessions(
          ( Ø, ØØ, designatorSupplier, isOrigin ) ->
              labDownwardDuty.counter( designatorSupplier.get(), counter ),
          upward
      ) ;
    }
  }


// ======
// Signon
// ======

  @Override
  public void primarySignonAttempt(
      final Designator designatorInternal,
      final String login,
      final String password
  ) {
    final LabUserKey user = new LabUserKey( login ) ;

    if( USERS.contains( user ) ) {
      final FailedSignonAttempt failedSignonAttempt = failedSignonAttempts.get( user ) ;
      if( failedSignonAttempt.hasReachedLimit() ) {
        final SignonFailureNotice signonFailureNotice =
            new SignonFailureNotice( SignonFailure.TOO_MANY_ATTEMPTS ) ;
        LOGGER.debug(
            "Primary Signon attempt failed for " + user + " given " + designatorInternal + ", " +
            FailedSignonAttempt.class.getSimpleName() + " limit reached. Notifying with " +
            signonFailureNotice + "."
        ) ;
        signonOutwardDuty.primarySignonAttempted(
            designatorInternal,
            new SignonDecision<>( signonFailureNotice )
        ) ;
      } else if( password.equals( login ) ) {
        LOGGER.debug(
            "Primary Signon attempt successful for " + user + " given " + designatorInternal +
            ", notifying of successful attempt."
        ) ;
        signonOutwardDuty.primarySignonAttempted( designatorInternal, new SignonDecision<>( user ) ) ;
      }
      return ;
    }
    final SignonDecision<SignableUser> signonDecision = new SignonDecision<>(
        new SignonFailureNotice( SignonFailure.INVALID_CREDENTIAL ) ) ;
    LOGGER.debug(
        "Primary Signon attempt failed for " + user + " given " + designatorInternal + ", " +
        "notifying of " + signonDecision + "."
    ) ;
    signonOutwardDuty.primarySignonAttempted(
        designatorInternal,
        signonDecision
    ) ;
  }

  @Override
  public void secondarySignonAttempt(
      final Designator designatorInternal,
      final String login
  ) {
    final LabUserKey user = new LabUserKey( login ) ;

    if( USERS.contains( user ) ) {
      final FailedSignonAttempt failedSignonAttempt = failedSignonAttempts.get( user ) ;
      if( failedSignonAttempt.hasReachedLimit() ) {
        final SignonFailureNotice signonFailureNotice =
            new SignonFailureNotice( SignonFailure.TOO_MANY_ATTEMPTS ) ;
        LOGGER.debug(
            "Secondary Signon attempt failed for " + user + " given " + designatorInternal + ", " +
                FailedSignonAttempt.class.getSimpleName() + " limit reached. Notifying with " +
                signonFailureNotice + "."
        ) ;
        signonOutwardDuty.secondarySignonAttempted(
            designatorInternal,
            signonFailureNotice
        ) ;
      } else {
        LOGGER.debug(
            "Secondary Signon attempt successful for " + user + " given " + designatorInternal +
                ", notifying of successful attempt."
        ) ;
        signonOutwardDuty.secondarySignonAttempted( designatorInternal, null ) ;
      }
    } else {
      LOGGER.warn( "Could not find user with '" + user.login() + "' as login " +
          "while processing secondary signon attempt." ) ;
      signonOutwardDuty.secondarySignonAttempted(
          designatorInternal,
          new SignonFailureNotice( SignonFailure.UNEXPECTED )
      ) ;
    }
  }

  @Override
  public void failedSignonAttempt(
      final Designator designatorInternal,
      final String login,
      final SignonAttempt signonAttempt
  ) {
    final LabUserKey user = new LabUserKey( login ) ;
    if( USERS.contains( user ) ) {
      final FailedSignonAttempt incremented =
          failedSignonAttempts.get( user ).increment( signonAttempt ) ;
      failedSignonAttempts.put( user, incremented ) ;
      LOGGER.debug( "Registered failed Signon attempt for " + login + " given " +
          designatorInternal + ", updated to " + incremented + "." ) ;
    } else {
      LOGGER.error( "Unknown " + user + ", doing nothing." ) ;
    }
  }

  @Override
  public void registerSession(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier,
      final String login
  ) {
    final LabUserKey userKey = new LabUserKey( login ) ;
    checkArgument( USERS.contains( userKey ) ) ;
    try {
      sessionStore.putSession( sessionIdentifier, new LabUserSession( userKey ) ) ;
      LOGGER.info( "Registered " + sessionIdentifier + " for " + login + " given " +
          designatorInternal + "." ) ;
      signonOutwardDuty.sessionCreated( designatorInternal, sessionIdentifier, login, null ) ;
    } catch( final SessionStore.SessionAlreadyExistsException e ) {
      final SignonFailureNotice signonFailureNotice = new SignonFailureNotice(
          SignonFailure.SESSION_ALREADY_EXISTS ) ;
      LOGGER.info( "Did not register " + sessionIdentifier + " for " + login + " given " +
          designatorInternal + " because of " + signonFailureNotice + "." ) ;
      signonOutwardDuty.sessionCreated(
          designatorInternal,
          sessionIdentifier,
          login,
          null ) ;
    }
  }

  @Override
  public void signout( final Designator designator ) {
    signoutQuiet( designator, designator.sessionIdentifier ) ;
    signonOutwardDuty.terminateSession( designator, designator.sessionIdentifier ) ;
  }

  @Override
  public void signoutQuiet(
      final Designator designator,
      final SessionIdentifier sessionIdentifier
  ) {
    LOGGER.info( "Signing out quiet " + sessionIdentifier + " given" + designator + "." ) ;
    sessionStore.removeSession( sessionIdentifier ) ;
  }

  @Override
  public void signoutAll( final Designator designatorInternal ) {
    sessionStore.visitSessions(
        ( sessionIdentifier, userKey, designatorDownwardSupplier, isOrigin ) ->
            signout( timeKit.designatorFactory.internal( sessionIdentifier ) ),
        designatorInternal,
        false
    ) ;
    sessionStore.removeAll() ;
  }

  @Override
  public void resetSignonFailures(
      final Designator designatorInternal,
      final String login
  ) {
    for( final LabUserKey userKey : failedSignonAttempts.keySet() ) {
      failedSignonAttempts.put( userKey, FailedSignonAttempt.create() ) ;
    }
    LOGGER.info( "All " + FailedSignonAttempt.class.getSimpleName() + "s are reset." ) ;
  }


// ============
// Boring stuff
// ============

  public static final class LabUserKey
      extends StringWrapper< LabUserKey >
      implements SignableUser
  {
    public LabUserKey( final String wrapped ) {
      super( wrapped ) ;
    }

    @Override
    public String login() {
      return wrapped ;
    }

    @Override
    public PhoneNumber phoneNumber() {
      return new PhoneNumber( "+360000" + wrapped.hashCode() ) ;
    }
  }


  private static final class LabUserSession
      implements SessionStore.UserSession< LabUserKey >
  {

    private final LabUserKey labUserKey;

    private LabUserSession( final LabUserKey labUserKey ) {
      this.labUserKey = checkNotNull( labUserKey ) ;
    }

    @Override
    public LabUserKey userKey() {
      return labUserKey ;
    }
  }


}
