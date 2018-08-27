package com.otcdlink.chiron.upend.session.implementation;

import com.google.common.base.Joiner;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignableUser;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.collection.KeyHolder;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents the state associated to a {@link SessionIdentifier}.
 *
 * <pre>
 *        (o)
 *         |
 * +-----------------------+
 * | {@link Pending} |
 * +-----------------------+
 *         |
 *         |-{@link SessionBook#activate(KeyHolder.Key, Object, DateTime, boolean)}
 *         | [false]
 *         |
 *         v
 * +----------------------+
 * | {@link Active} |<--------------+
 * +----------------------+               |
 *         |                              |
 *         |-{@link SessionBook#removeChannel(Object, DateTime)}
 *         |                              |
 *         |                              |
 *         |                              |-{@link SessionBook#activate(Key, Object, DateTime, boolean)}
 *         |                              | [true]
 *         |                              |
 *         |                  +-----------------------+
 *         |                  | {@link Reusing} |
 *         |                  +-----------------------+
 *         v                              ^
 * +------------------------+             |
 * | {@link Orphaned} |-------------+
 * +------------------------+     |
 *                                {@link SessionBook#reuse(Key, Object, DateTime)}
 * </pre>
 */
abstract class SessionDetail<
    SESSION_IDENTIFIER extends KeyHolder.Key< SESSION_IDENTIFIER >,
    CHANNEL,
    ADDRESS
>
    implements KeyHolder< SESSION_IDENTIFIER >
{

  public final SESSION_IDENTIFIER sessionIdentifier ;

  @Override
  public final SESSION_IDENTIFIER key() {
    return sessionIdentifier ;
  }

  /**
   * When was created associated {@link SessionIdentifier}.
   */
  public final DateTime creationTime ;

  /**
   * The remote {@link ADDRESS} of previous {@link CHANNEL}, so we can check that {@link Reusing}
   * does not happen from another {@link ADDRESS}.
   */
  public final ADDRESS remoteAddress ;

  /**
   * Time at which this {@link SessionDetail} was no longer {@link Active}
   * (also applies for {@link Pending}.
   */
  public DateTime inactiveSince ;

  /**
   * Associated {@link SignableUser}
   */
  public final SignableUser user ;
  public CHANNEL channel ;

  protected SessionDetail(
      final SESSION_IDENTIFIER sessionIdentifier,
      final DateTime creationTime,
      final CHANNEL channel,
      final SignableUser user
  ) {
    this( sessionIdentifier, creationTime, null, null, channel, user ) ;
  }

  protected SessionDetail(
      final SESSION_IDENTIFIER sessionIdentifier,
      final DateTime creationTime,
      final ADDRESS remoteAddress,
      final DateTime inactiveSince,
      final SignableUser user
  ) {
    this( sessionIdentifier, creationTime, remoteAddress, inactiveSince, null, user ) ;
  }

  /**
   * Enforces invariants common to all concrete classes.
   */
  private SessionDetail(
      final SESSION_IDENTIFIER sessionIdentifier,
      final DateTime creationTime,
      final ADDRESS remoteAddress,
      final DateTime inactiveSince,
      final CHANNEL channel,
      final SignableUser user
  ) {
    this.sessionIdentifier = checkNotNull( sessionIdentifier ) ;
    this.creationTime = checkNotNull( creationTime ) ;
    if( channel != null ) {
      checkArgument( remoteAddress == null ) ;
    }
    this.remoteAddress = remoteAddress ;
    this.inactiveSince = inactiveSince;
    this.user = checkNotNull( user ) ;
    this.channel = channel ;
  }

  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + '{' + Joiner.on( ';' ).skipNulls().join(
       "sessionIdentifier=" + sessionIdentifier.toString(),
       "creationTime=" + creationTime.toString(),
       "user.login=" + user.login(),
       remoteAddress == null ? null : "remoteAddress=" + remoteAddress,
       channel == null ? null : "channel=" + channel,
       inactiveSince == null ? null : "inactiveSince=" + inactiveSince
    ) + '}' ;
  }

  public static abstract class Connected<
      SESSION_IDENTIFIER extends KeyHolder.Key< SESSION_IDENTIFIER >,
      CHANNEL,
      ADDRESS
      >
      extends SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS >
  {
    public Connected(
        final SESSION_IDENTIFIER sessionIdentifier,
        final DateTime creationTime,
        final ADDRESS remoteAddress,
        final DateTime inactiveSince,
        final CHANNEL channel,
        final SignableUser user
    ) {
      super(
          sessionIdentifier,
          creationTime,
          remoteAddress,
          inactiveSince,
          checkNotNull( channel ),
          user
      ) ;
    }

    protected Connected(
        final SESSION_IDENTIFIER sessionIdentifier,
        final DateTime creationTime,
        final CHANNEL channel,
        final SignableUser user
    ) {
      super( sessionIdentifier, creationTime, checkNotNull( channel ), user ) ;
    }

    /**
     * @param remoteAddress caller must ensure that address is the same than {@link CHANNEL}'s
     *     remote address.
     */
    public Orphaned< SESSION_IDENTIFIER, CHANNEL, ADDRESS > orphaned(
        final DateTime orphanoodStart,
        final ADDRESS remoteAddress
    ) {
      return new Orphaned<>(
          sessionIdentifier, creationTime, remoteAddress, orphanoodStart, user ) ;
    }

  }

  public interface Activable<
      SESSION_IDENTIFIER extends Key< SESSION_IDENTIFIER >,
      CHANNEL,
      ADDRESS
      > {
    Active< SESSION_IDENTIFIER, CHANNEL, ADDRESS > activate() ;
  }


  public static < SESSION_IDENTIFIER extends KeyHolder.Key< SESSION_IDENTIFIER >, CHANNEL, ADDRESS >
  Pending< SESSION_IDENTIFIER, CHANNEL, ADDRESS > pending(
      final SESSION_IDENTIFIER sessionIdentifier,
      final DateTime creationTime,
      final CHANNEL channel,
      final SignableUser user
  ) {
    return new Pending<>( sessionIdentifier, creationTime, channel, user ) ;
  }

  public static class Pending<
      SESSION_IDENTIFIER extends KeyHolder.Key< SESSION_IDENTIFIER >,
      CHANNEL,
      ADDRESS
  >
      extends SessionDetail.Connected< SESSION_IDENTIFIER, CHANNEL, ADDRESS >
      implements Activable< SESSION_IDENTIFIER, CHANNEL, ADDRESS >
  {
    private Pending(
        final SESSION_IDENTIFIER sessionIdentifier,
        final DateTime creationTime,
        final CHANNEL channel,
        final SignableUser user
    ) {
      super( sessionIdentifier, creationTime, null, creationTime, channel, user ) ;
    }

    @Override
    public Active< SESSION_IDENTIFIER, CHANNEL, ADDRESS > activate() {
      return new Active<>( sessionIdentifier, creationTime, channel, user ) ;
    }
  }

  public static class Active<
      SESSION_IDENTIFIER extends KeyHolder.Key< SESSION_IDENTIFIER >,
      CHANNEL,
      ADDRESS
  > extends SessionDetail.Connected< SESSION_IDENTIFIER, CHANNEL, ADDRESS >
  {
    private Active(
        final SESSION_IDENTIFIER sessionIdentifier,
        final DateTime creationTime,
        final CHANNEL channel,
        final SignableUser user
    ) {
      super( sessionIdentifier, creationTime, channel, user ) ;
    }

  }

  public static class Orphaned<
      SESSION_IDENTIFIER extends KeyHolder.Key< SESSION_IDENTIFIER >,
      CHANNEL,
      ADDRESS
  > extends SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > {
    private Orphaned(
        final SESSION_IDENTIFIER sessionIdentifier,
        final DateTime creationTime,
        final ADDRESS remoteAddress,
        final DateTime inactiveSince,
        final SignableUser user
    ) {
      super(
          sessionIdentifier,
          creationTime,
          checkNotNull( remoteAddress ),
          checkNotNull( inactiveSince ),
          user
      ) ;
    }

    public Reusing< SESSION_IDENTIFIER, CHANNEL, ADDRESS > reusing( final CHANNEL channel ) {
      return new Reusing<>( sessionIdentifier, creationTime, channel, inactiveSince, user ) ;
    }
  }

  public static class Reusing<
      SESSION_IDENTIFIER extends KeyHolder.Key< SESSION_IDENTIFIER >,
      CHANNEL,
      ADDRESS
  >   extends Connected< SESSION_IDENTIFIER, CHANNEL, ADDRESS >
      implements Activable< SESSION_IDENTIFIER, CHANNEL, ADDRESS >
  {
    private Reusing(
        final SESSION_IDENTIFIER sessionIdentifier,
        final DateTime creationTime,
        final CHANNEL channel,
        final DateTime inactiveSince,
        final SignableUser user
    ) {
      super( sessionIdentifier, creationTime, null, inactiveSince, channel, user ) ;
    }

    @Override
    public Active< SESSION_IDENTIFIER, CHANNEL, ADDRESS > activate() {
      return new Active<>( sessionIdentifier, creationTime, channel, user ) ;
    }

  }


}
