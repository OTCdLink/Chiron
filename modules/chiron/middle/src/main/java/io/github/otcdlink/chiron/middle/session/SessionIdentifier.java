package io.github.otcdlink.chiron.middle.session;

import io.github.otcdlink.chiron.toolbox.StringWrapper;
import io.github.otcdlink.chiron.toolbox.collection.KeyHolder;

import java.util.Comparator;

/**
 * Associates an authenticated user with some low-level connection(s).
 * <p>
 * Since it does not depend on the nature of the connection, it can be shared between
 * WebSocket sessions and HTML (Doorway) sessions.
 * <p>
 * For CometD, wraps a {@code org.cometd.bayeux.server.ServerSession#getId() Cometd session
 * identifier} for more strong-typing.
 *
 */
public final class SessionIdentifier extends StringWrapper< SessionIdentifier >
    implements KeyHolder.Key< SessionIdentifier >
{
  public SessionIdentifier( final String s ) {
    super( s ) ;
  }

  public String asString() {
    return wrapped ;
  }

  public static final Comparator< SessionIdentifier > COMPARATOR =
      new StringWrapper.WrapperComparator<>() ;
}
