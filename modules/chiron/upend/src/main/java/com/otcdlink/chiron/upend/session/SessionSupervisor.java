package com.otcdlink.chiron.upend.session;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;


/**
 * Keeps sessions and {@link CHANNEL}s together.
 * This class is meant to bridge {@link ChannelPipeline}s with {@link SignonInwardDuty} and
 * {@link SecondaryAuthenticator}.
 *
 * <h2>Threading</h2>
 * This class contains only non-blocking methods.
 * It delegates much of its work to {@link SignonInwardDuty} which has only non-blocking methods
 * (the Reactor should run it in one single thread).
 * We need to convert method calls into {@link Command} objects with {@link Designator}.
 * Secondary Authentication happens through an {@link SecondaryAuthenticator}
 * which shares an {@link io.netty.channel.EventLoop} common to the whole Upend stuff.
 *
 * <h2>Session timeout</h2>
 * Pinging feature should trigger {@link CHANNEL} closing in case of a timeout.
 *
 *
 */
public interface SessionSupervisor<CHANNEL, ADDRESS > {

  /**
   * Asks to associate given {@link CHANNEL} to an already-existing session.
   * When the Upend receives an existing {@link SessionIdentifier} from Downend during
   * a reconnection, it should call this method.
   *
   * @see SignonInwardDuty#registerSession(Designator, SessionIdentifier, String)
   */
  void tryReuse(
      SessionIdentifier sessionIdentifier,
      CHANNEL channel,
      ReuseCallback callback
  ) ;

  interface ReuseCallback {
    /**
     * @param signonFailureNotice {@code null} if success.
     */
    void reuseOutcome( SignonFailureNotice signonFailureNotice ) ;

  }

  /**
   * @see SignonInwardDuty#primarySignonAttempt(Designator, String, String)
   */
  void attemptPrimarySignon(
      String userLogin,
      String password,
      CHANNEL channel,
      ADDRESS remoteAddress,
      PrimarySignonAttemptCallback callback
  ) ;

  interface SignonAttemptCallback {

    void sessionAttributed( SessionIdentifier sessionIdentifier ) ;

    /**
     * @param signonFailureNotice a non-{@code null} value meaning the Signon failed.
     *     (This is the behavior common to subinterfaces. Subinterfaces refine this contract.)
     */
    void signonResult(
        SignonFailureNotice signonFailureNotice
    ) ;

  }

  interface PrimarySignonAttemptCallback extends SignonAttemptCallback {

    /**
     * @param signonFailureNotice can't be {@code null}; in case of success, the method to call
     *     should be {@link SignonAttemptCallback#sessionAttributed(SessionIdentifier)}).
     *     Can't be {@link SignonFailure#MISSING_SECONDARY_CODE}; in this case the method to call
     *     should be
     *     {@link PrimarySignonAttemptCallback#needSecondarySignon(SignableUser, SecondaryToken)}).
     */
    @Override
    void signonResult(
        SignonFailureNotice signonFailureNotice
    ) ;

    void needSecondarySignon(
        SignableUser userIdentity,
        SecondaryToken secondaryToken
    ) ;

  }

  /**
   * @see SignonOutwardDuty#secondarySignonAttempted(Designator, SignonFailureNotice)
   */
  void attemptSecondarySignon(
      CHANNEL channel,
      ADDRESS remoteAddress,
      SecondaryToken secondaryToken,
      SecondaryCode secondaryCode,
      SecondarySignonAttemptCallback callback
  ) ;

  interface SecondarySignonAttemptCallback extends SignonAttemptCallback { }

  /**
   * {@link CHANNEL} must call this method when detecting a disconnection or a timeout
   * (then not asking to terminate the Session) or when it receives a {@link CloseWebSocketFrame}
   * or a POST request asking for signout.
   * Implementer should not re-close the {@link CHANNEL} thereafter.
   *
   * @param terminateSession {@code false} means keeping the {@link SessionIdentifier} valid
   *     for a period of time. This makes sense only in the case of a disconnection.
   *
   */
  void closed( CHANNEL channel, SessionIdentifier sessionIdentifier, boolean terminateSession ) ;

  void kickoutAll() ;

  /**
   *
   * @param designator {@code null}, or a {@link Designator} that will be attached to
   *     created {@link Command}.
   */
  void kickout( Designator designator, final SessionIdentifier sessionIdentifier ) ;

  interface ChannelCloser< CHANNEL > {

    /**
     * Should throw no exception.
     * TODO: add some code telling about the reason (Daemon stopping or kickout).
     */
    void close( CHANNEL channel ) ;
  }

}
