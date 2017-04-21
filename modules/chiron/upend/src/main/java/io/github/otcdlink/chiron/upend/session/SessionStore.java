package io.github.otcdlink.chiron.upend.session;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;

import java.util.Map;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Opaque container associating a {@link SessionIdentifier} with a {@link SESSION}.
 * No duplicates allowed (this could become an option). Not thread-safe.
 */
public class SessionStore< KEY, SESSION extends SessionStore.UserSession< KEY > > {

  public interface UserSession< KEY > {
    KEY userKey() ;
  }

  public interface Visitor< KEY > {
    /**
     * @param designatorDownwardSupplier creates a fresh {@link Designator} only when needed.
     * @param isOrigin {@code true} if {@code sessionIdentifier} matches the
     *     {@link Designator} passed to
     *     {@link SessionStore#visitSessions(Visitor, Designator)}
     */
    void visit(
        SessionIdentifier sessionIdentifier,
        KEY userKey,
        Supplier< Designator > designatorDownwardSupplier,
        boolean isOrigin
    ) ;
  }

  /**
   * Thrown if there is more than one {@link SessionIdentifier}
   * for each {@link UserSession#userKey()}.
   */
  public static class MoreThanOneSessionException extends RuntimeException {
    public MoreThanOneSessionException( final Object userKey ) {
      super( "More than one session for user key " + userKey ) ;
    }
  }

  public static class UnknownUserKeyException extends RuntimeException {
    public UnknownUserKeyException( final Object userKey ) {
      super( "Found no " + UserSession.class.getSimpleName() + " with key " + userKey ) ;
    }
  }

  public static class SessionAlreadyExistsException extends RuntimeException {
    public SessionAlreadyExistsException( final SessionIdentifier sessionIdentifier ) {
      super( "Already exists: " + sessionIdentifier ) ;
    }
  }

  public static class UnknownSessionException extends RuntimeException {
    public UnknownSessionException( final SessionIdentifier sessionIdentifier ) {
      super( "Unknown: " + sessionIdentifier ) ;
    }
  }



  private final Map< SessionIdentifier, SESSION > sessions = Maps.newHashMap() ;
  private final Designator.Factory designatorFactory ;

  public SessionStore( final Designator.Factory designatorFactory ) {
    this.designatorFactory = checkNotNull( designatorFactory ) ;
  }

  public void visitSessions(
      final SessionStore.Visitor< KEY > visitor,
      final Designator origin
  ) {
    visitSessions( visitor, origin, true ) ;
  }

  /**
   * @param propagateTag if {@code true}, the
   *     {@link Visitor#visit(SessionIdentifier, Object, Supplier, boolean)} will receive a
   *     {@code Supplier} giving a {@link Designator} that contains the same
   *     {@link Command.Tag} as given {@link Designator}.
   */
  public void visitSessions(
      final SessionStore.Visitor< KEY > visitor,
      final Designator origin,
      final boolean propagateTag
  ) {
    checkNotNull( visitor ) ;
    for( final Map.Entry< SessionIdentifier, SESSION > entry : sessions.entrySet() ) {
      final boolean isOrigin =
          origin != null &&
          origin.sessionIdentifier != null &&
          origin.sessionIdentifier.equals( entry.getKey() )
      ;
      final Supplier< Designator > destination = isOrigin && propagateTag
          ? () -> designatorFactory.downward( origin )
          : () -> designatorFactory.downward( entry.getKey(), origin.stamp )
      ;
      visitor.visit( entry.getKey(), entry.getValue().userKey(), destination, isOrigin ) ;
    }
  }

  public ImmutableMap< SessionIdentifier, SESSION > getAllSessions() {
    return ImmutableMap.copyOf( sessions ) ;
  }

  public KEY getUserKey( final Designator origin )
      throws UnknownSessionException
  {
    return getUserKey( origin.sessionIdentifier ) ;
  }

  public KEY getUserKey( final SessionIdentifier sessionIdentifier )
      throws UnknownSessionException
  {
    return getUserSession( sessionIdentifier ).userKey() ;
  }

  public UserSession< KEY > getUserSession( final SessionIdentifier sessionIdentifier )
      throws UnknownSessionException
  {
    final SESSION session = sessions.get( sessionIdentifier ) ;
    if( session == null ) {
      throw new UnknownSessionException( sessionIdentifier ) ;
    } else {
      return session ;
    }
  }

  public boolean hasUserSession( final SessionIdentifier sessionIdentifier ) {
    final UserSession userSession = sessions.get( sessionIdentifier ) ;
    return userSession != null ;
  }

  public SessionIdentifier getUniqueSession( final KEY userKey ) {
    return getUniqueSession( userKey, true ) ;
  }

  public SessionIdentifier getUniqueSession(
      final KEY userKey,
      final boolean mustExist
  ) {

    SessionIdentifier uniqueSession = null ;
    for( final Map.Entry< SessionIdentifier, SESSION > entry : sessions.entrySet() ) {
      if( entry.getValue().userKey().equals( userKey ) ) {
        if( uniqueSession == null ) {
          uniqueSession = entry.getKey() ;
        } else {
          throw new MoreThanOneSessionException( userKey.toString() ) ;
        }
      }
    }
    if( uniqueSession == null && mustExist ) {
      throw new UnknownUserKeyException( userKey.toString() ) ;
    }
    return uniqueSession ;
  }

  public void putSession(
      final SessionIdentifier sessionIdentifier,
      final SESSION userSession
  ) throws SessionAlreadyExistsException, MoreThanOneSessionException {

    checkNotNull( userSession ) ;
    checkNotNull( sessionIdentifier ) ;

    for( final Map.Entry< SessionIdentifier, SESSION > entry : sessions.entrySet() ) {
      if( userSession.userKey().equals( entry.getValue().userKey() ) ) {
        throw new MoreThanOneSessionException( userSession.userKey() ) ;
      }
      if( sessionIdentifier.equals( entry.getKey() ) ) {
        throw new SessionAlreadyExistsException( sessionIdentifier ) ;
      }
    }
    sessions.put( sessionIdentifier, userSession ) ;
  }

  public void removeSession( final SessionIdentifier sessionIdentifier ) {
    sessions.remove( sessionIdentifier ) ;
  }

  public void removeAll() {
    sessions.clear() ;
  }



}
