package io.github.otcdlink.chiron.downend;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.downend.DownendConnector.State;
import io.github.otcdlink.chiron.toolbox.StateHolder;
import org.slf4j.Logger;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.CONNECTED;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.CONNECTING;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.PROBLEM;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.SIGNED_IN;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.STOPPED;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.STOPPING;

final class DownendStateUpdater {

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
  private static final Predicate< State > PROBLEM_PREDICATE = Predicate.isEqual( PROBLEM ) ;

  private static final Predicate< State > PROBLEM_OR_CONNECTING_PREDICATE =
      s -> s == PROBLEM || s == CONNECTING ;

  private final Logger logger ;
  private final Supplier< String > ownerToString ;
  private final StateHolder< State > stateHolder ;
  private final DownendConnector.ChangeWatcher stateWatcher ;

  public DownendStateUpdater(
      final Logger logger,
      final Supplier< String > ownerToString,
      final State initialState,
      final DownendConnector.ChangeWatcher changeWatcher
  ) {
    this.stateHolder = new StateHolder<>( initialState, logger, ownerToString ) ;
    this.ownerToString = checkNotNull( ownerToString ) ;
    this.stateWatcher = checkNotNull( changeWatcher ) ;
    this.logger = checkNotNull( logger ) ;
  }

  private static DownendConnector.Change< State > stateChange(
      final State state
  ) {
    final DownendConnector.Change< State > change =
        STATE_CHANGE_SINGLETONS.get( state ) ;
    if( change == null ) {
      throw new IllegalArgumentException( "No predefined instance for " + state ) ;
    }
    return change;
  }

  public final State state() {
    return stateHolder.get() ;
  }

  public State update( final DownendConnector.State state ) {
    return update( stateChange( state ) ) ;
  }

  /**
   * @return {@code null} if previous {@link State} was identical to new one.
   */
  public State update(
      final DownendConnector.Change< State > change
  ) {
    final State[] allowedCurrentStates ;
    switch( change.kind ) {
      case CONNECTING :
        allowedCurrentStates = new State[] { STOPPED, CONNECTED, SIGNED_IN } ;
        break ;
      case CONNECTED :
        allowedCurrentStates = new State[] { CONNECTING } ;
        break ;
      case SIGNED_IN :
        allowedCurrentStates = new State[] { CONNECTED } ;
        break ;
      case STOPPING :
        allowedCurrentStates = new State[] { CONNECTED, SIGNED_IN, CONNECTING, STOPPING } ;
        break ;
      case STOPPED :
        allowedCurrentStates = new State[] { CONNECTED, STOPPING, STOPPED } ;
        break ;
      case PROBLEM :
        allowedCurrentStates = null ;
        break ;
      default :
        throw new IllegalArgumentException( "Unsupported: " + change ) ;
    }

    final Predicate< State > blackholePredicate = (
        ( change.kind == CONNECTING ) /*|| ( change.kind == STOPPING )*/ ?
        PROBLEM_OR_CONNECTING_PREDICATE :
        PROBLEM_PREDICATE
    ) ;

    return update( change, blackholePredicate, allowedCurrentStates ) ;
  }

  public State update(
      final State newState,
      final Predicate< State > blackholePredicate,
      final State... allowedCurrentStates
  ) {
    return update( stateChange( newState ), blackholePredicate, allowedCurrentStates ) ;
  }

  public State update(
      final DownendConnector.Change< State > change,
      final Predicate< State > blackholePredicate,
      final State... allowedCurrentStates
  ) {
    final State previous ;
    if( allowedCurrentStates == null ) {
      logger.error( "Could not find any allowed new state given " + change.kind +
          " for " + ownerToString.get() ) ;
      previous = stateHolder.set( PROBLEM ) ;
    } else {
      previous = stateHolder.updateOrFail(
          change.kind, blackholePredicate, allowedCurrentStates ) ;
    }
    if( previous == change.kind ) {
      return null ;
    } else if( blackholePredicate.test( previous ) ) {
      logger.debug( "State is already " + previous + ", skipping " + ownerToString.get() +
          "notification." ) ;
      return previous ;
    } else {
      try {
        stateWatcher.stateChanged( change ) ;
      } catch( final Exception e ) {
        logger.error( "Failed to notify " + ownerToString.get() + " of " + change + ":", e ) ;
      }
      return previous ;
    }
  }

  public void notifyFailedConnectionAttempt() {
    try {
      stateWatcher.failedConnectionAttempt() ;
    } catch( final Exception e ) {
      logger.error( "Failed to notify failed connection attempt of " +
          ownerToString.get() + ":", e ) ;
    }
  }

  public void notifyNoSignon() {
    try {
      stateWatcher.noSignon() ;
    } catch( final Exception e ) {
      logger.error( "Failed to notify Signon abandon to " + ownerToString.get() + ":", e ) ;
    }
  }

}
