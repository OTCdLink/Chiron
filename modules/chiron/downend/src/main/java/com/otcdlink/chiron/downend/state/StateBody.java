package com.otcdlink.chiron.downend.state;

import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.channel.Channel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents the whole state of a {@link DownendConnector} that should mutate atomically.
 */
public final class StateBody {
  public final DownendConnector.State state ;
  public final Channel channel ;
  public final CompletableFuture< Void > startFuture ;
  public final CompletableFuture< Void > stopFuture ;
  public final ConnectionDescriptor connectionDescriptor ;
  public final ScheduledFuture< ? > reconnectFuture ;
  public final ScheduledFuture< ? > nextPingFuture ;
  public final ScheduledFuture< ? > nextPongTimeoutFuture ;
  public final String login ;

  public boolean readyToSend() {
    return connectionDescriptor != null &&
        ( connectionDescriptor.authenticationRequired ? state == DownendConnector.State.SIGNED_IN : state == DownendConnector.State.CONNECTED ) ;
  }

  @Override
  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder() ;
    stringBuilder.append( ToStringTools.nameAndCompactHash( this ) ) ;
    stringBuilder.append( '{' ) ;
    stringBuilder.append( "state=" ) ;
    stringBuilder.append( state.name() ) ;
    if( channel != null ) {
      stringBuilder.append( ';' ) ;
      stringBuilder.append( "channel=" ) ;
      stringBuilder.append( channel.id() ) ;
    }
    stringBuilder.append( '}' ) ;
    return stringBuilder.toString() ;
  }

  // ==================
// Transition methods
// ==================

  /**
   * Creates a fresh instance in initial state.
   * @param stopFuture
   */
  public static StateBody stopped( final CompletableFuture< Void > stopFuture ) {
    return new StateBody(
        DownendConnector.State.STOPPED,
        null,
        null,
        stopFuture,
        null,
        null,
        null,
        null,
        null
    ) ;
  }

  public StateBody startConnecting( final CompletableFuture< Void > startFuture ) {
    return new StateBody(
        checkTransition( DownendConnector.State.CONNECTING, DownendConnector.State.STOPPED ),
        null,
        startFuture,
        null,
        null,
        null,
        null,
        null,
        login
    ) ;
  }

  public StateBody channel( final Channel channel ) {
    return new StateBody(
        checkTransition( DownendConnector.State.CONNECTING, DownendConnector.State.CONNECTING, DownendConnector.State.CONNECTED ),
        channel,
        startFuture,
        null,
        connectionDescriptor,
        reconnectFuture,
        null,
        null,
        login
    ) ;
  }

  public StateBody login( final String login ) {
    return new StateBody(
        checkTransition( state, DownendConnector.State.CONNECTING, DownendConnector.State.CONNECTED ),
        channel,
        startFuture,
        null,
        connectionDescriptor,
        reconnectFuture,
        null,
        null,
        login
    ) ;
  }

  public StateBody planToReconnect(
      final ScheduledFuture< ? > reconnectFuture
  ) {
    return new StateBody(
        checkTransition( state, DownendConnector.State.CONNECTING ),
        null,
        startFuture,
        null,
        connectionDescriptor,
        reconnectFuture,
        null,
        null,
        login
    ) ;
  }

  public StateBody planNextPing(
      final ScheduledFuture< ? > pingFuture
  ) {
    return new StateBody(
        checkTransition( state, DownendConnector.State.CONNECTED, DownendConnector.State.SIGNED_IN ),
        channel,
        startFuture,
        null,
        connectionDescriptor,
        null,
        pingFuture,
        null,
        login
    ) ;
  }

  public StateBody planPongTimeout(
      final ScheduledFuture< ? > nextPongTimeoutFuture
  ) {
    return new StateBody(
        checkTransition( state, DownendConnector.State.CONNECTED, DownendConnector.State.SIGNED_IN ),
        channel,
        startFuture,
        null,
        connectionDescriptor,
        null,
        null,
        nextPongTimeoutFuture,
        login
    ) ;
  }

  public StateBody connected(
      final ConnectionDescriptor connectionDescriptor,
      final ScheduledFuture< ? > nextPingFuture
  ) {
    return new StateBody(
        checkTransition( DownendConnector.State.CONNECTED, DownendConnector.State.CONNECTING ),
        channel,
        startFuture,
        null,
        connectionDescriptor,
        null,
        nextPingFuture,
        null,
        login
    ) ;
  }

  public StateBody signedIn() {
    return new StateBody(
        checkTransition( DownendConnector.State.SIGNED_IN, DownendConnector.State.CONNECTED),
        channel,
        startFuture,
        null,
        connectionDescriptor,
        null,
        nextPingFuture,
        nextPongTimeoutFuture,
        login
    ) ;
  }

  public StateBody noChannel( final ScheduledFuture< ? > reconnectFuture ) {
    return new StateBody(
        checkTransition( DownendConnector.State.CONNECTING, DownendConnector.State.CONNECTED, DownendConnector.State.SIGNED_IN ),
        null,
        startFuture,
        stopFuture,
        connectionDescriptor,
        reconnectFuture,
        null,
        null,
        login
    ) ;
  }

  public StateBody stopping( final CompletableFuture< Void > stopFuture ) {
    return new StateBody(
        checkTransition( DownendConnector.State.STOPPING, DownendConnector.State.CONNECTING, DownendConnector.State.CONNECTED, DownendConnector.State.SIGNED_IN ),
        channel,  /** Propagate, so we can call {@link Channel#close()} once in this state. */
        null,
        stopFuture,
        connectionDescriptor,
        null,
        null,
        null,
        null
    ) ;
  }

  public StateBody stopped() {
    return new StateBody(
        checkTransition( DownendConnector.State.STOPPED, DownendConnector.State.STOPPING ),
        null,
        null,
        stopFuture,
        connectionDescriptor,
        null,
        null,
        null,
        null
    ) ;
  }

  public StateBody emergencyStop() {
    return new StateBody(
        checkTransition( DownendConnector.State.STOPPED, DownendConnector.State.CONNECTED, DownendConnector.State.SIGNED_IN, DownendConnector.State.STOPPING ),
        null,
        null,
        stopFuture == null ? CompletableFuture.completedFuture( null ) : stopFuture ,
        connectionDescriptor,
        null,
        null,
        null,
        null
    ) ;
  }

// ===================
// Creation and checks
// ===================

  private StateBody(
      final DownendConnector.State state,
      final Channel channel,
      final CompletableFuture< Void > startFuture,
      final CompletableFuture< Void > stopFuture,
      final ConnectionDescriptor connectionDescriptor,
      final ScheduledFuture< ? > reconnectFuture,
      final ScheduledFuture< ? > nextPingFuture,
      final ScheduledFuture< ? > nextPongTimeoutFuture,
      final String login
  ) {
    this.state = checkNotNull( state ) ;
    this.channel = channel ;
    this.startFuture = startFuture ;
    this.stopFuture = stopFuture ;
    this.connectionDescriptor = connectionDescriptor ;
    this.reconnectFuture = reconnectFuture ;
    this.nextPingFuture = nextPingFuture ;
    this.nextPongTimeoutFuture = nextPongTimeoutFuture ;
    this.login = login ;
    checkInvariants() ;
  }

  private DownendConnector.State checkTransition(
      final DownendConnector.State destinationState,
      final DownendConnector.State... allowedStates
  ) {
    for( final DownendConnector.State allowedState : allowedStates ) {
      if( allowedState == state ) {
        return destinationState ;
      }
    }
    throw new StateTransitionException( destinationState, this ) ;
  }

  public static class StateTransitionException extends RuntimeException {
    public final DownendConnector.State failedUpdate ;
    public final StateBody current ;
    public StateTransitionException(
        final DownendConnector.State failedUpdate,
        final StateBody current
    ) {
      super( "Can't transition into " + failedUpdate + " from " + current ) ;
      this.failedUpdate = failedUpdate ;
      this.current = current ;
    }
  }


  private void checkInvariants() {
    switch( state ) {
      case STOPPED :
        checkNullity( "channel", channel ) ;
        checkNullity( "startFuture", startFuture ) ;
        checkNonNullity( "stopFuture", stopFuture ) ;
//        checkNonNullity( "connectionDescriptor", connectionDescriptor ) ;
        checkNullity( "reconnectFuture", reconnectFuture ) ;
        checkNullity( "nextPingFuture", nextPingFuture ) ;
        checkNullity( "nextPongTimeoutFuture", nextPongTimeoutFuture ) ;
        checkNullity( "login", login ) ;
        break ;
      case CONNECTING :
        checkNonNullity( "startFuture", startFuture ) ;
        checkNullity( "nextPingFuture", nextPingFuture ) ;
        checkNullity( "nextPongTimeoutFuture", nextPongTimeoutFuture ) ;
        break ;
      case CONNECTED :
        checkNonNullity( "startFuture", startFuture ) ;
        checkNullity( "stopFuture", stopFuture ) ;
        checkNonNullity( "channel", channel ) ;
        checkNonNullity( "connectionDescriptor", connectionDescriptor ) ;
        break ;
      case SIGNED_IN :
        checkNonNullity( "startFuture", startFuture ) ;
        checkNullity( "stopFuture", stopFuture ) ;
        checkNonNullity( "channel", channel ) ;
        checkNonNullity( "connectionDescriptor", connectionDescriptor ) ;
        checkNonNullity( "login", login ) ;
        break ;
      case STOPPING :
        checkNullity( "startFuture", startFuture ) ;
        checkNonNullity( "stopFuture", stopFuture ) ;
        checkNonNullity( "connectionDescriptor", connectionDescriptor ) ;
        checkNullity( "login", login ) ;
        break ;
      default :
        throw new IllegalArgumentException( "Unsupported: " + state ) ;
    }
  }

  private void checkNullity( final String fieldName, Object value ) {
    if( value != null ) {
      throw new InvariantViolationException(
          state, "Field '" + fieldName + "' should be null but has value of " + value ) ;
    }
  }

  private void checkNonNullity( final String fieldName, Object value ) {
    if( value == null ) {
      throw new InvariantViolationException(
          state, "should be non-null: " + fieldName ) ;
    }
  }

  public static final class InvariantViolationException extends RuntimeException {
    public InvariantViolationException(
        final DownendConnector.State state,
        final String message
    ) {
      super( "For state " + state + ": " + message ) ;
      this.state = state ;
    }

    public final DownendConnector.State state ;

  }


}
