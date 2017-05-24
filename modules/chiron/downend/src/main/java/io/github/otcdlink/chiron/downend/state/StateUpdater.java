package io.github.otcdlink.chiron.downend.state;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.downend.DownendConnector;
import io.github.otcdlink.chiron.downend.DownendConnector.State;
import io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.CONNECTING;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.SIGNED_IN;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.STOPPED;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.STOPPING;

/**
 *
 * <pre>
 *                      STOPPED  <-----------+
 *                        |                  |
 *                        |              STOPPING           ...----> PROBLEM
 *                        |                  ^
 *                    Any-|                  |
 *                        |                  |
 *                        v                  |
 *                +---> CONNECTING ----------+
 *                |       |                  ^
 *                |       |                  |
 *                |       v                  |-EventLoop
 *                +---- CONNECTED -----------+
 *                |       |                  ⋮
 *      EventLoop-|       |-EventLoop
 *                |       |-[signonMaterializer!=null]
 *                |       |
 *                |       |                  ⋮
 *                |       v                  |-EventLoop
 *                +---- SIGNED_IN -----------+
 *
 * </pre>
 */
public final class StateUpdater {

  private static final Logger LOGGER = LoggerFactory.getLogger( StateUpdater.class ) ;
  private static final ImmutableMap<
      State,
      DownendConnector.Change< State >
  > STATE_CHANGE_SINGLETONS =
      ImmutableMap.of(
          STOPPED, new DownendConnector.Change<>( STOPPED ),
          CONNECTING, new DownendConnector.Change<>( CONNECTING ),
          SIGNED_IN, new DownendConnector.Change<>( SIGNED_IN ),
          STOPPING, new DownendConnector.Change<>( STOPPING )
      )
  ;

  private final URL connectionUrl ;
  private final TimeBoundary.PrimingForDownend primingTimeBoundary ;
  private final Supplier< String > ownerToString ;
  private final AtomicReference< StateBody > stateHolder = new AtomicReference<>() ;
  private final DownendConnector.ChangeWatcher stateWatcher ;

  public StateUpdater(
      final Supplier< String > ownerToString,
      final URL connectionUrl,
      final TimeBoundary.PrimingForDownend primingTimeBoundary,
      final DownendConnector.ChangeWatcher changeWatcher
  ) {
    this.ownerToString = checkNotNull( ownerToString ) ;
    this.connectionUrl = checkNotNull( connectionUrl ) ;
    this.primingTimeBoundary = checkNotNull( primingTimeBoundary ) ;
    this.stateWatcher = checkNotNull( changeWatcher ) ;
    stateHolder.set( StateBody.stopped( new CompletableFuture<>() ) );
  }

  /**
   * Current value, consider as potentially out-of-date because of a concurrent update.
   */
  public StateBody current() {
    return stateHolder.get() ;
  }

  /**
   * Attempts to calculate a connect timeout based on {@link StateBody#connectionDescriptor} using
   * {@link TimeBoundary.ForAll#reconnectDelayMs(java.util.Random)} if possible, or falls back
   * to {@link #primingTimeBoundary}.
   */
  public long connectTimeoutMs( final Random random ) {
    checkNotNull( random ) ;
    final ConnectionDescriptor connectionDescriptor = current().connectionDescriptor ;
    if( connectionDescriptor == null ) {
      return primingTimeBoundary.connectTimeoutMs() ;
    } else {
      return connectionDescriptor.timeBoundary.reconnectDelayMs( random ) ;
    }
  }

  public int pongTimeoutMs() {
    final StateBody stateBody = current() ;
    if( stateBody.connectionDescriptor != null ) {
      return stateBody.connectionDescriptor.timeBoundary.pongTimeoutMs ;
    } else {
      return primingTimeBoundary.pongTimeoutMs() ;
    }
  }


  /**
   *
   * @throws StateBody.StateTransitionException because there are edge cases where we don't want
   *     the transition to happen. Throwing an exception consumes more resources than returning
   *     some flag in {@link Transition} object but it happens in cases like double start, or
   *     reconnection colliding with a stop. Throwing an exception causes the calling code to
   *     nicely explode on unhandled cases, making diagnostic easier.
   */
  public Transition update( final Function< StateBody, StateBody > updater ) {
    final Transition[] stateTransitionReference = { null } ;
    stateHolder.getAndUpdate( current -> {
      Transition stateTransition = new Transition( current, updater.apply( current ) ) ;
      // This side-effect is safe because it reflects the last "winning" update.
      stateTransitionReference[ 0 ] = stateTransition ;
      return stateTransition.update ;
    } ) ;
    final Transition stateTransition = stateTransitionReference[ 0 ] ;
    DownendConnector.Change< State > change ;
    switch( stateTransition.update.state ) {
      case CONNECTED :
        change = new DownendConnector.Change.SuccessfulConnection(
            stateTransition.update.connectionDescriptor ) ;
        break ;
      case SIGNED_IN :
        change = new DownendConnector.Change.SuccessfulSignon(
            connectionUrl, stateTransition.update.login ) ;
        break ;
      default :
        change = STATE_CHANGE_SINGLETONS.get( stateTransition.update.state ) ;
        if( change == null ) {
          throw new RuntimeException(
              "No singleton " + DownendConnector.Change.class.getSimpleName() + " for " +
              stateTransition.update.state
          ) ;
        }
        break ;
    }
    try {
      if( stateTransition.update.state != stateTransition.previous.state ) {
        stateWatcher.stateChanged( change ) ;
        LOGGER.debug( "Notified of state changed into " + stateTransition.update.state +
            " from " + stateTransition.previous.state + " for " + ownerToString.get() + "." ) ;
      }
    } catch( final Exception e ) {
      LOGGER.error( "Failed to notify " + ownerToString.get() + " of " + change + ".", e ) ;
    }
    return stateTransition ;
  }

  public void notifyFailedConnectionAttempt() {
    try {
      stateWatcher.failedConnectionAttempt() ;
    } catch( final Exception e ) {
      LOGGER.error( "Failed to notify failed connection attempt of " +
          ownerToString.get() + ":", e ) ;
    }
  }


  public static final class Transition {
    public final StateBody previous ;
    public final StateBody update ;
    Transition(
        final StateBody previous,
        final StateBody update
    ) {
      this.previous = checkNotNull( previous ) ;
      this.update = checkNotNull( update ) ;
    }
  }

}
