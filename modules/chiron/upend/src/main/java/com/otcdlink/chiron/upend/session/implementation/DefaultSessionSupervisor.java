package com.otcdlink.chiron.upend.session.implementation;

import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignableUser;
import com.otcdlink.chiron.middle.session.SignonDecision;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.middle.session.SignonSetback;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.clock.Clock;
import com.otcdlink.chiron.upend.TimeKit;
import com.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import com.otcdlink.chiron.upend.session.SecondaryAuthenticator;
import com.otcdlink.chiron.upend.session.SessionIdentifierGenerator;
import com.otcdlink.chiron.upend.session.SessionSupervisor;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;
import com.otcdlink.chiron.upend.session.twilio.AuthenticationFailure;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Wires up various Signon-related components to offer a simple behavior from a Netty Channel's
 * point of view.
 *
 * <h1>Threading</h1>
 * Instances of this class are single-threaded, they do not support concurrent access.
 * Callbacks are called in the same trade as the calling thread. It's callers' responsability
 * to provide a callback wrapped in an {@code Executor} for instance.
 *
 * <h1>Generics</h1>
 * Generic types are here to define the contract (or the absence of special contract) with used
 * classes.
 *
 * <h1>Designator</h1>
 * It could make sense to add a {@link Designator} as first argument to every public method
 * to support better traceability across threads but the need is unclear yet.
 *
 * <h1>Normal two-factor authentication sequence</h1>
 * <p>
 * The diagram shows how threading should look like.
 * <p>
 * The wiring between dependencies and thread assignation is done by the
 * <a href='https://projectreactor.io'>Reactor</a>. We cheat with an {@code Executor}
 * dedicated to {@link DefaultSessionSupervisor} to ease some wiring until we complete migration
 * to Chiron and Reactor 3.
 *
 * <pre>

  +--------------&gt; SessionEnforcerTier
  |                -------------------
  |                                |
  |                                | [Supervisor thread]
  |                                |
  |                                | 1: attemptPrimarySignon
  |                                | 7: attemptSecondarySignon
  |                                |
  | [Channel Executor]             |
  |                                |
  |  6: signonResult (2fa needed)  |
  | 14: signonResult (session OK)  |
  |                                |
  |                                v
  |               (I) SessionSupervisor         (I) SignonOutwardDuty
  |               ---------------------         ---------------------
  |               tryJoin                       primarySignonAttempted
  |               attemptPrimarySignon          secondarySignonAttempted
  |               attemptSecondarySignon        sessionCreated
  |               closed                        terminateSession
  |               kickoutAll                      O          ^
  |               kickout                         |          |
  |                 O                  +----------+          | [Supervisor thread]
  |                 |                  |                     |
  |                 |                  |                    ...
  |                 |                  |
  |                 |                  |
  +-------------- DefaultSessionSupervisor ----+----------------------------+
                  ------------------------     |                            |
                    |   |                      O                            O
                    |   |                     (I) SecondaryTokenCallback   (I) VerificationCallback
                    |   |                     --------------------------   ------------------------
                    |   |                     secondaryToken               secondaryAuthenticationResult
                    |   |                            ^                         ^
                    |   |                            |[Supervisor thread]      |[Supervisor thread]
                    |   |                            |                         |
                    |   |  4: requestAuthentication  |                         |
                    |   | 10: verifySecondaryCode    |                         |
                    |   |                            |  5: secondaryToken      |
                    |   |                            |  + request phone call   |
                    |   |                            |                         | 11: secondaryAuthResult
                    |   |                            |                         |     (success)
                    |   |                            |                         |
                    |   +------------------------&gt; SecondaryAuthenticator -----+
                    |      [Channel Executor]      ----------------------
                    |      [now: thread pool]      requestAuthentication
                    |                              verifySecondaryCode
                    |
                    |
                    | [Logic thread]
                    |
                    |  2: primarySignonAttempt
                    |  8: secondarySignonAttempt
                    | 12: registerSession
                    v
                  (I) SignonInwardDuty
                  --------------------
                  primarySignonAttempt
                  secondarySignonAttempt
                  failedSignonAttempt
                  registerSession
                  signout
                  signoutQuiet
                  signoutAll
                  resetSignonFailures
                   O
                   |                                        ...
                   |                                         | [Supervisor thread]
                   |                                         |
                   |                                         |  3: primarySignonAttempted (success)
                   |                                         |  9: secondarySignonAttempted (success)
                   |                                         | 13: sessionCreated (success)
                   |                                         |
                  UpendLogic --------------------------------+
                  ----------
</pre>
 */
public class DefaultSessionSupervisor< CHANNEL, ADDRESS, SESSION_PRIMER >
    implements
    OutwardSessionSupervisor< CHANNEL, ADDRESS, SESSION_PRIMER >
{
  private static final Logger LOGGER = LoggerFactory.getLogger( DefaultSessionSupervisor.class ) ;

  private final Clock clock ;
  private final Stamp.Generator stampGenerator ;
  private final Designator.Factory designatorFactory ;
  private final SessionIdentifierGenerator sessionIdentifierGenerator ;
  private final SignonInwardDuty signonInwardDuty;
  private final SecondaryAuthenticator secondaryAuthenticator;
  private final SessionBook<SessionIdentifier, CHANNEL, ADDRESS > sessionBook ;
  private final Map<SecondaryToken, PendingSecondaryAuthentication>
      pendingSecondaryAuthentications = new HashMap<>() ;
  private final Duration secondaryTokenValidity ;

  /**
   * Maximum lifetime for other than {@link SessionDetail.Active} based on
   * {@link SessionDetail#inactiveSince}.
   */
  private final Duration maximumInactivityDuration ;

  private final Function< CHANNEL, ADDRESS > addressExtractor ;
  private final ChannelCloser< CHANNEL > channelCloser ;


  public DefaultSessionSupervisor(
      final TimeKit timeKit,
      final SessionIdentifierGenerator sessionIdentifierGenerator,
      final SignonInwardDuty signonInwardDuty,
      final SecondaryAuthenticator secondaryAuthenticator,
      final Duration maximumInactivityDuration,
      final Function< CHANNEL, ADDRESS > addressExtractor,
      final ChannelCloser< CHANNEL > channelCloser
  ) {
    this(
        timeKit.clock,
        timeKit.stampGenerator,
        timeKit.designatorFactory,
        Duration.standardMinutes( 1 ),
        sessionIdentifierGenerator,
        signonInwardDuty,
        secondaryAuthenticator,
        maximumInactivityDuration, addressExtractor, channelCloser
    ) ;
  }

  DefaultSessionSupervisor(
      final Clock clock,
      final Stamp.Generator stampGenerator,
      final Designator.Factory designatorFactory,
      final Duration secondaryTokenValidity,
      final SessionIdentifierGenerator sessionIdentifierGenerator,
      final SignonInwardDuty signonInwardDuty,
      final SecondaryAuthenticator secondaryAuthenticator,
      final Duration maximumInactivityDuration,
      final Function< CHANNEL, ADDRESS > addressExtractor,
      final ChannelCloser< CHANNEL > channelCloser
  ) {
    this.clock = checkNotNull( clock ) ;
    this.stampGenerator = checkNotNull( stampGenerator ) ;
    this.designatorFactory = checkNotNull( designatorFactory ) ;
    this.sessionIdentifierGenerator = checkNotNull( sessionIdentifierGenerator ) ;
    this.signonInwardDuty = checkNotNull( signonInwardDuty ) ;
    this.secondaryAuthenticator = secondaryAuthenticator;
    this.secondaryTokenValidity = checkNotNull( secondaryTokenValidity ) ;
    this.maximumInactivityDuration = maximumInactivityDuration;
    this.addressExtractor = addressExtractor;
    this.channelCloser = checkNotNull( channelCloser ) ;
    sessionBook = new SessionBook<>( this.addressExtractor, maximumInactivityDuration ) ;
  }

// =====
// Reuse
// =====

  @Override
  public void tryReuse(
      final SessionIdentifier sessionIdentifier,
      final CHANNEL channel,
      final ReuseCallback callback
  ) {
    final DateTime now = clock.getCurrentDateTime() ;
    final SignonDecision< SignableUser > reuseDecision =
        sessionBook.reuse( sessionIdentifier, channel, now ) ;
    if( reuseDecision.signonFailureNotice == null ) {

      final Stamp sessionRegistrationStamp = stampGenerator.generate() ;
      signonInwardDuty.registerSession(
          new SessionCreationDesignator< CHANNEL, ADDRESS >(
              sessionRegistrationStamp,
              channel,
              new SignonAttemptCallback< SESSION_PRIMER >() {
                @Override
                public void sessionAttributed(
                    final SessionIdentifier sessionIdentifier,
                    final SESSION_PRIMER sessionPrimer
                ) {
                  callback.reuseOutcome( null ) ;
                }

                @Override
                public void signonResult( final SignonFailureNotice signonFailureNotice ) {
                  callback.reuseOutcome( signonFailureNotice ) ;
                }
              },
              true
          ),
          sessionIdentifier,
          reuseDecision.userIdentity.login()
      ) ;

    } else {
      callback.reuseOutcome( reuseDecision.signonFailureNotice ) ;
    }



  }


// ==============
// Primary signon
// ==============

  @Override
  public void attemptPrimarySignon(
      final String userLogin,
      final String password,
      final CHANNEL channel,
      final ADDRESS remoteAddress,
      final PrimarySignonAttemptCallback< SESSION_PRIMER > callback
  ) {
    final Designator designatorInternal =
        new PrimarySignonAttemptDesignator<>(
            stampGenerator.generate(),
            channel,
            remoteAddress,
            userLogin,
            callback
        )
    ;

    LOGGER.debug( "Attempting Primary Signon for '" + userLogin + "' using " +
        designatorInternal + "." ) ;

    signonInwardDuty.primarySignonAttempt(
        designatorInternal,
        userLogin,
        password
    ) ;

  }

  static class PrimarySignonAttemptDesignator< CHANNEL, ADDRESS >
      extends Designator
      implements Designator.Derivable< PrimarySignonAttemptDesignator< CHANNEL, ADDRESS > >
  {
    public final CHANNEL channel ;
    public final ADDRESS remoteAddress ;
    public final String login ;
    public final PrimarySignonAttemptCallback callback ;

    protected PrimarySignonAttemptDesignator(
        final Kind kind,
        final Stamp stamp,
        final Stamp cause,
        final CHANNEL channel,
        final ADDRESS remoteAddress,
        final String login,
        final PrimarySignonAttemptCallback callback
    ) {
      super( kind, stamp, cause, null, null ); ;
      this.channel = checkNotNull( channel ) ;
      this.remoteAddress = remoteAddress ;
      this.login = checkNotNull( login ) ;
      this.callback = checkNotNull( callback ) ;
    }

    public PrimarySignonAttemptDesignator(
        final Stamp stamp,
        final CHANNEL channel,
        final ADDRESS remoteAddress,
        final String login,
        final PrimarySignonAttemptCallback callback
    ) {
      this( Kind.INTERNAL, stamp, null, channel, remoteAddress, login, callback ) ;
    }

    @Override
    public PrimarySignonAttemptDesignator< CHANNEL, ADDRESS > derive(
        final Kind newKind,
        final Stamp newStamp
    ) {
      return new PrimarySignonAttemptDesignator<>(
          newKind,
          newStamp,
          stamp,
          channel,
          remoteAddress,
          login,
          callback
      ) ;
    }

    @Override
    public PrimarySignonAttemptDesignator< CHANNEL, ADDRESS > derive(
        final Kind newKind,
        final Stamp newStamp,
        final Command.Tag Ø,
        final SessionIdentifier ØØ
    ) {
      return derive( newKind, newStamp ) ;
    }
  }


  @Override
  public void primarySignonAttempted(
      final Designator designatorInternal,
      final SignonDecision<SignableUser> signonDecision
  ) {
    final PrimarySignonAttemptDesignator< CHANNEL, ADDRESS >
        designator = ( PrimarySignonAttemptDesignator ) designatorInternal ;
    if( signonDecision.userIdentity == null ) {
      /** {@link SignonDecision#userIdentity} nullity means there is a non-null
       * {@link SignonDecision#signonFailureNotice}. */
      LOGGER.debug( "Primary Signon failed given " + designatorInternal + " because of " +
          signonDecision.signonFailureNotice + ", no User identity given." ) ;
      designator.callback.signonResult( signonDecision.signonFailureNotice ) ;
    } else {
      if( signonDecision.signonFailureNotice == null ) {
        // Arrived here, user existence, password validity and signon attempts are OK.
        if( secondaryAuthenticator == null ) {
          // No need for Secondary Authentication.
          LOGGER.debug(
              "Primary Signon succeeded given " + designatorInternal + ", " +
              "initiating session creation for " + signonDecision.userIdentity + "."
          ) ;
          initiateSessionCreation(
              designator.channel,
              designator.remoteAddress,
              signonDecision.userIdentity,
              designator.callback
          ) ;
        } else {
          LOGGER.debug(
              "Initiating Secondary Signon given " + designatorInternal + " " +
              "after Primary Signon succeeded for " + signonDecision.userIdentity + "."
          ) ;
          secondaryAuthenticator.requestAuthentication(
              signonDecision.userIdentity.phoneNumber(),
              secondaryToken -> {
                pendingSecondaryAuthentications.put(
                    secondaryToken,
                    new PendingSecondaryAuthentication(
                        clock.getCurrentDateTime(),
                        signonDecision.userIdentity
                    )
                ) ;
                designator.callback.needSecondarySignon(
                    signonDecision.userIdentity, secondaryToken ) ;
              }
          ) ;
        }

      } else {
        LOGGER.debug(
            "Primary Signon failed given " + designatorInternal + " because of " +
                signonDecision.signonFailureNotice + ", User identity is  "+
                signonDecision.userIdentity + ", notifying of failed Signon attempt."
        ) ;
        signonInwardDuty.failedSignonAttempt(
            designatorFactory.internalZero( designator ),
            signonDecision.userIdentity.login(),
            SignonSetback.Factor.PRIMARY
        ) ;
        designator.callback.signonResult( signonDecision.signonFailureNotice ) ;
      }
    }
  }



// ================
// Secondary signon
// ================

  @Override
  public void attemptSecondarySignon(
      final CHANNEL channel,
      final ADDRESS remoteAddress,
      final SecondaryToken secondaryToken,
      final SecondaryCode secondaryCode,
      final SecondarySignonAttemptCallback callback
  ) {
    final PendingSecondaryAuthentication pendingSecondaryAuthentication =
        pendingSecondaryAuthentications.get( secondaryToken ) ;
    if( pendingSecondaryAuthentication == null ) {
      callback.signonResult(
          new SignonFailureNotice( SignonFailure.INVALID_SECONDARY_TOKEN ) ) ;
    } else {
      final SecondarySignonAttemptDesignator< CHANNEL, ADDRESS > secondarySignonAttemptDesignator =
          new SecondarySignonAttemptDesignator<>(
              stampGenerator.generate(),
              channel,
              remoteAddress,
              secondaryToken,
              secondaryCode,
              callback
          )
      ;
      signonInwardDuty.secondarySignonAttempt(
          secondarySignonAttemptDesignator,
          pendingSecondaryAuthentication.userIdentity.login()
      ) ;
    }
  }

  static class SecondarySignonAttemptDesignator< CHANNEL, ADDRESS >
      extends Designator
      implements Designator.Derivable< SecondarySignonAttemptDesignator< CHANNEL, ADDRESS > >
  {
    public final CHANNEL channel ;
    public final ADDRESS remoteAddress ;
    public final SecondaryToken secondaryToken ;
    public final SecondaryCode secondaryCode ;
    public final SecondarySignonAttemptCallback callback ;

    protected SecondarySignonAttemptDesignator(
        final Kind kind,
        final Stamp stamp,
        final Stamp cause,
        final CHANNEL channel,
        final ADDRESS remoteAddress,
        final SecondaryToken secondaryToken,
        final SecondaryCode secondaryCode,
        final SecondarySignonAttemptCallback callback
    ) {
      super( kind, stamp, cause, null, null ) ;
      this.callback = checkNotNull( callback ) ;
      this.channel = checkNotNull( channel ) ;
      this.remoteAddress = checkNotNull( remoteAddress ) ;
      this.secondaryToken = checkNotNull( secondaryToken ) ;
      this.secondaryCode = checkNotNull( secondaryCode ) ;
    }

    public SecondarySignonAttemptDesignator(
        final Stamp stamp,
        final CHANNEL channel,
        final ADDRESS remoteAddress,
        final SecondaryToken secondaryToken,
        final SecondaryCode secondaryCode,
        final SecondarySignonAttemptCallback callback
    ) {
      this(
          Kind.INTERNAL,
          stamp,
          null,
          channel,
          remoteAddress,
          secondaryToken,
          secondaryCode,
          callback
      ) ;
    }

    @Override
    public SecondarySignonAttemptDesignator< CHANNEL, ADDRESS > derive(
        final Kind newKind,
        final Stamp newStamp
    ) {
      return new SecondarySignonAttemptDesignator<>(
          newKind,
          newStamp,
          stamp,
          channel,
          remoteAddress,
          secondaryToken,
          secondaryCode,
          callback
      ) ;
    }

    @Override
    public SecondarySignonAttemptDesignator< CHANNEL, ADDRESS > derive(
        final Kind newKind,
        final Stamp newStamp,
        final Command.Tag Ø,
        final SessionIdentifier ØØ
    ) {
      return derive( newKind, newStamp ) ;
    }
  }

  @Override
  public void secondarySignonAttempted(
      final Designator designatorInternal,
      final SignonFailureNotice signonFailureNotice
  ) {
    final SecondarySignonAttemptDesignator< CHANNEL, ADDRESS > designator =
        ( SecondarySignonAttemptDesignator ) designatorInternal ;
    if( signonFailureNotice == null ) {
      final PendingSecondaryAuthentication pendingSecondaryAuthentication =
          pendingSecondaryAuthentications.get( designator.secondaryToken ) ;
      if( pendingSecondaryAuthentication == null ) {
        final SignonFailureNotice signonFailureNotice1 = new SignonFailureNotice(
            SignonFailure.INVALID_SECONDARY_TOKEN,
            SignonFailure.INVALID_SECONDARY_TOKEN.description() + ": " + designator.secondaryToken
        ) ;
        designator.callback.signonResult( signonFailureNotice1 ) ;
      } else {
        if( secondaryAuthenticator == null ) {
          throw new IllegalStateException( "This cannot happen: there should be some " +
              SecondaryAuthenticator.class.getSimpleName() + " to cause creation of a " +
              PendingSecondaryAuthentication.class.getSimpleName()
          ) ;
        } else {
          secondaryAuthenticator.verifySecondaryCode(
              designator.secondaryToken,
              designator.secondaryCode,
              secondaryCodeVerified( designator, pendingSecondaryAuthentication )
          ) ;
        }
      }
    } else {
      designator.callback.signonResult( signonFailureNotice ) ;
    }
  }

  private SecondaryAuthenticator.VerificationCallback secondaryCodeVerified(
      final SecondarySignonAttemptDesignator< CHANNEL, ADDRESS > designator,
      final PendingSecondaryAuthentication pendingSecondaryAuthentication
  ) {
    return authenticationFailureNotice -> {
      if( authenticationFailureNotice == null ) {
        initiateSessionCreation(
            designator.channel,
            designator.remoteAddress,
            pendingSecondaryAuthentication.userIdentity,
            designator.callback
        ) ;
      } else {
        if( authenticationFailureNotice.kind == AuthenticationFailure.INCORRECT_CODE ) {
          signonInwardDuty.failedSignonAttempt(
              designatorFactory.internal(),
              pendingSecondaryAuthentication.userIdentity.login(),
              SignonSetback.Factor.SECONDARY
          ) ;
          designator.callback.signonResult( new SignonFailureNotice(
              SignonFailure.INVALID_SECONDARY_CODE, authenticationFailureNotice.message ) ) ;
        } else if( authenticationFailureNotice.kind ==
            AuthenticationFailure.UNKNOWN_SECONDARY_TOKEN
        ) {
          final SignonFailureNotice signonFailureNotice = new SignonFailureNotice(
              SignonFailure.INVALID_SECONDARY_TOKEN,
              authenticationFailureNotice.message
          ) ;
          designator.callback.signonResult( signonFailureNotice ) ;
        } else {
          designator.callback.signonResult( new SignonFailureNotice(
              SignonFailure.SECONDARY_AUTHENTICATION_GENERIC_FAILURE,
              authenticationFailureNotice.message
          ) ) ;
        }
      }
    } ;
  }

  private void initiateSessionCreation(
      final CHANNEL channel,
      final ADDRESS remoteAddress,
      final SignableUser userIdentity,
      final SignonAttemptCallback callback
  ) {
    scavenge() ;
    final SessionIdentifier sessionIdentifier = sessionIdentifierGenerator.generate() ;
    final SignonFailureNotice sessionCreationResult =
        sessionBook.create(
            sessionIdentifier,
            channel,
            userIdentity,
            clock.getCurrentDateTime()
        )
    ;

    if( sessionCreationResult == null ) {
      final SessionCreationDesignator< CHANNEL, ADDRESS > sessionCreationDesignator =
          new SessionCreationDesignator<>(
              stampGenerator.generate(),
              channel,
              callback,
              false
          )
      ;
      signonInwardDuty.registerSession(
          sessionCreationDesignator,
          sessionIdentifier,
          userIdentity.login()
      ) ;
    } else {
      LOGGER.error(
          "This looks like an internal error. " + sessionIdentifier +
          " was considered by " + signonInwardDuty + " as valid, but the " + channel +
          " calling this method did not know about it, and triggered a full Signon" +
          " using it again. Detail: " + sessionCreationResult + "."
      ) ;
      callback.signonResult( new SignonFailureNotice( SignonFailure.SESSION_ALREADY_EXISTS ) ) ;
    }
  }

  static class SessionCreationDesignator< CHANNEL, ADDRESS >
      extends Designator
      implements Designator.Derivable< SessionCreationDesignator< CHANNEL, ADDRESS > >
  {
    public final CHANNEL channel ;
    public final SessionSupervisor.SignonAttemptCallback signonAttemptCallback ;
    public final boolean sessionReuse ;

    protected SessionCreationDesignator(
        final Kind kind,
        final Stamp stamp,
        final Stamp cause,
        final CHANNEL channel,
        final SignonAttemptCallback signonAttemptCallback,
        final boolean sessionReuse
    ) {
      super( kind, stamp, cause, null, null ) ;
      this.channel = checkNotNull( channel ) ;
      this.signonAttemptCallback = checkNotNull( signonAttemptCallback ) ;
      this.sessionReuse = sessionReuse ;
    }

    public SessionCreationDesignator(
        final Stamp stamp,
        final CHANNEL channel,
        final SignonAttemptCallback signonAttemptCallback,
        final boolean sessionReuse
    ) {
      this(
          Kind.INTERNAL,
          stamp,
          null,
          channel,
          signonAttemptCallback,
          sessionReuse
      ) ;
    }

    @Override
    public SessionCreationDesignator< CHANNEL, ADDRESS > derive(
        final Kind newKind,
        final Stamp newStamp
    ) {
      return new SessionCreationDesignator<>(
          newKind,
          newStamp,
          stamp,
          channel,
          signonAttemptCallback,
          sessionReuse
      ) ;
    }

    @Override
    public SessionCreationDesignator< CHANNEL, ADDRESS > derive(
        final Kind newKind,
        final Stamp newStamp,
        final Command.Tag newTag,
        final SessionIdentifier newSessionIdentifier
    ) {
      return derive( newKind, newStamp ) ;
    }
  }


// ================
// Session creation
// ================

  @Override
  public void sessionCreated(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier,
      final String login,
      final SESSION_PRIMER sessionPrimer
  ) {
    /** Checking we have a {@link SessionCreationDesignator} because during a Replay
     * we have plain {@link Designator}s. This is very crappy.
     * TODO: fix this, use {@link OutwardSessionSupervisor} as a normal Reactor {@link Stage}.
     */
    if( designatorInternal instanceof SessionCreationDesignator ) {
      final SessionCreationDesignator< CHANNEL, ADDRESS > designator =
          ( SessionCreationDesignator<CHANNEL, ADDRESS> ) designatorInternal ;
      sessionCreated(
          designator.channel,
          sessionIdentifier,
          designator.signonAttemptCallback,
          designator.sessionReuse,
          sessionPrimer
      ) ;
    }
  }

  private void sessionCreated(
      final CHANNEL channel,
      final SessionIdentifier sessionIdentifier,
      final SignonAttemptCallback< SESSION_PRIMER > signonAttemptCallback,
      final boolean reuse,
      final SESSION_PRIMER sessionPrimer
  ) {
    final DateTime now = clock.getCurrentDateTime() ;
    final SignonFailureNotice signonFailureNotice = sessionBook.activate(
        sessionIdentifier,
        channel,
        now,
        reuse
    ).signonFailureNotice ;
    if( signonFailureNotice == null ) {
      signonAttemptCallback.sessionAttributed( sessionIdentifier, sessionPrimer ) ;
    } else {
      LOGGER.info( "Session attribution failed for " + sessionIdentifier + ", got " +
          signonFailureNotice + "." +
          ( reuse && signonFailureNotice.kind == SignonFailure.UNKNOWN_SESSION ?
              " This might have been caused by an outdated session (session reuse delay is " +
                  sessionBook.maximumInactivityDuration + ")." :
              ""
          )
      ) ;
      signonAttemptCallback.signonResult( signonFailureNotice ) ;
    }
  }

  @Override
  public void sessionCreationFailed(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier,
      final SignonFailureNotice signonFailureNotice
  ) {
    final SessionCreationDesignator< CHANNEL, ADDRESS > designator =
        ( SessionCreationDesignator<CHANNEL, ADDRESS> ) designatorInternal ;
    sessionCreationFailed(
        designator.signonAttemptCallback,
        sessionIdentifier,
        signonFailureNotice
    ) ;
  }

  private void sessionCreationFailed(
      final SignonAttemptCallback signonAttemptCallback,
      final SessionIdentifier sessionIdentifier,
      final SignonFailureNotice signonFailureNotice
  ) {
    if( sessionIdentifier != null ) {
      sessionBook.removeSession( sessionIdentifier ) ;
    }
    signonAttemptCallback.signonResult( signonFailureNotice ) ;
  }

// ===================
// Session termination
// ===================

  @Override
  public void terminateSession(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier
  ) {
    checkNotNull( sessionIdentifier ) ;
    final CHANNEL removedChannel = sessionBook.removeSession( sessionIdentifier ) ;
    if( removedChannel != null ) {
      channelCloser.close( removedChannel ) ;
    }
  }



// ===============
// Other contracts
// ===============

  @Override
  public void closed(
      final CHANNEL channel,
      final SessionIdentifier sessionIdentifier,
      final boolean terminateSession
  ) {
    final DateTime now = clock.getCurrentDateTime() ;
    LOGGER.info( "Closed (with " + ( terminateSession ? "" : "no " ) + "session termination) " +
        "for " + sessionIdentifier + ": " + channel + "." ) ;
    signonInwardDuty.signoutQuiet( designatorFactory.internal(), sessionIdentifier ) ;
    final boolean removed = sessionBook.removeChannel(
        channel, terminateSession ? null : now ) ;
    if( terminateSession && ! removed ) {
      /** If removal did not happen, this means we already removed the {@link CHANNEL} */
      sessionBook.removeSession( sessionIdentifier ) ;
    }
  }

  @Override
  public void kickoutAll() {
    final ImmutableSet< CHANNEL > removedChannels = sessionBook.removeAllChannels() ;
    removedChannels.forEach( channelCloser::close ) ;
  }

  @Override
  public void kickout( final Designator designator, final SessionIdentifier sessionIdentifier ) {
    final CHANNEL removedChannel = sessionBook.removeSession( sessionIdentifier ) ;
    if( removedChannel != null ) {
      channelCloser.close( removedChannel ) ;
    }
    signonInwardDuty.signoutQuiet(
        designator == null ? designatorFactory.internal() : designator, sessionIdentifier ) ;
    LOGGER.info( "Asked to kick out session " + sessionIdentifier + "." ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + "{}" ;
  }

// ========
// Internal
// ========

  private static class PendingSecondaryAuthentication {
    private final DateTime creationTime ;
    private final SignableUser userIdentity ;

    public PendingSecondaryAuthentication(
        final DateTime creationTime,
        final SignableUser userIdentity
    ) {
      this.creationTime = checkNotNull( creationTime ) ;
      this.userIdentity = checkNotNull( userIdentity ) ;
    }
  }

  /**
   * Remove outdated objects.
   */
  void scavenge() {
    final DateTime now = clock.getCurrentDateTime() ;
    final DateTime earliestSurvivor = now.minus( secondaryTokenValidity ) ;
    final Iterator< Map.Entry< SecondaryToken, PendingSecondaryAuthentication> >
        iterator = pendingSecondaryAuthentications.entrySet().iterator() ;
    while( iterator.hasNext() ) {
      final Map.Entry< SecondaryToken, PendingSecondaryAuthentication> next =
          iterator.next() ;
      if( next.getValue().creationTime.isBefore( earliestSurvivor ) ) {
        iterator.remove() ;
      }
    }
  }


}
