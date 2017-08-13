package com.otcdlink.chiron.toolbox;

import com.google.common.base.Joiner;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class StateHolder< STATE extends Enum< STATE > >
    implements ReadableStateHolder< STATE >
{

  private final AtomicReference< STATE > state ;
  private final Logger logger ;
  private final Supplier< String > owner ;

  public StateHolder( final STATE initial ) {
    this( initial, null, null ) ;
  }

  public StateHolder( final STATE initial, final Logger logger, final Supplier< String > owner ) {
    state = new AtomicReference<>( initial ) ;
    if( logger == null ) {
      checkArgument( owner == null ) ;
    } else {
      checkNotNull( owner ) ;
    }
    this.logger = logger ;
    this.owner = owner ;
  }

  @Override
  public String toString() {
    final STATE currentState = this.state.get() ;
    return getClass().getSimpleName() +
        '{' + ToStringTools.getNiceName( currentState.getClass() ) + '.' + currentState + '}'
    ;
  }

  /**
   * Never {@code null}.
   */
  @Override
  public final STATE get() {
    return state.get() ;
  }

  public final STATE set( final STATE state ) {
    checkNotNull( state ) ;
    final STATE old = this.state.getAndSet( state ) ;
    if( logger != null ) {
      logStateUpdate( state, old ) ;
    }
    return old ;
  }

  @Override
  @SafeVarargs
  public final void checkOneOf( final STATE... allowedStates ) {
    checkInArray( this, allowedStates ) ;
  }

  @Override
  public final void checkIn( final Object owner, final STATE allowedState ) {
    final STATE current = state.get() ;
    if( current != allowedState ) {
      throwIllegalStateException( owner, allowedState ) ;
    }
  }

  @Override
  public final void checkIn( final Object owner, final STATE allowed1, final STATE allowed2 ) {
    final STATE current = state.get() ;
    if( current != allowed1 && current != allowed2 ) {
      throwIllegalStateException( owner, allowed1, allowed2 ) ;
    }
  }

  @Override
  @SafeVarargs
  public final void checkIn(
      final Object owner,
      final STATE allowed1,
      final STATE allowed2,
      final STATE allowed3,
      final STATE... allowedOthers
  ) {
    @SuppressWarnings( "unchecked" )
    final STATE[] allowedAll = ( STATE[] ) new Enum[ 3 + allowedOthers.length ] ;

    allowedAll[ 0 ] = checkNotNull( allowed1 ) ;
    allowedAll[ 1 ] = checkNotNull( allowed2 ) ;
    allowedAll[ 2 ] = checkNotNull( allowed3 ) ;
    if( allowedOthers.length > 0 ) {
      System.arraycopy( allowedOthers, 3, allowedAll, 3, allowedOthers.length ) ;
    }
    checkInArray( owner, allowedAll ) ;
  }
  @Override
  @SafeVarargs
  public final void checkInArray(
      final Object owner,
      final STATE... allowedOthers
  ) {
    if( ! isOneOf( allowedOthers ) ) {
      throwIllegalStateException( owner, ( Object[] ) allowedOthers ) ;
    }
  }

  @Override
  @SafeVarargs
  public final boolean isOneOf( final STATE... states ) {
    final STATE current = state.get() ;
    for( final STATE allowed : states ) {
      if( current == allowed ) {
        return true ;
      }
    }
    return false ;
  }

  private static void throwIllegalStateException(
      final Object owner,
      final Object... allowedStates
  ) {
    throw new IllegalStateException(
        owner + " not in [" + Joiner.on( ',' ).join( allowedStates ) + "]" ) ;
  }

  @SafeVarargs
  public final STATE updateOrFail( final STATE newState, final STATE... allowedStates ) {
    return updateConditionally( newState, true, allowedStates ) ;
  }

  @SafeVarargs
  public final STATE updateOrFail(
      final STATE newState,
      final Predicate< STATE > blackholePredicate,
      final STATE... allowedStates
  ) {
    return updateConditionally( newState, blackholePredicate, true, allowedStates ) ;
  }

  @SafeVarargs
  public final boolean updateMaybe( final STATE newState, final STATE... allowedStates ) {
    return updateConditionally( newState, false, allowedStates ) != null ;
  }

  @SafeVarargs
  private final STATE updateConditionally(
      final STATE newState,
      final boolean failIfUnexpectedState,
      final STATE... allowedStates
  ) {
    return updateConditionally( newState, STATE_PREDICATE_ALWAYS_FALSE, failIfUnexpectedState, allowedStates ) ;
  }

  @SafeVarargs
  private final STATE updateConditionally(
      final STATE newState,
      final Predicate< STATE > blackholePredicate,
      final boolean failIfUnexpectedState,
      final STATE... allowedStates
  ) {
    final StateUpdate< STATE > stateUpdate = update(
        newState, blackholePredicate, false, allowedStates ) ;
    if( ! stateUpdate.wishedState() ) {
      if( blackholePredicate.test( stateUpdate.current ) ) {
        return stateUpdate.previous ;
      } else if( failIfUnexpectedState ) {
        throw new IllegalStateException(
            "Can't switch from " + stateUpdate.previous + " to " + newState ) ;
      } else {
        return null ;
      }
    }
    return stateUpdate.previous ;
  }

  /**
   * Atomic {@link #state} update, doesn't happen if current {@link STATE} not among allowed values.
   */
  @SafeVarargs
  public final StateUpdate< STATE > update(
      final STATE newState,
      final STATE... allowedStates
  ) {
    return update( newState, true, allowedStates ) ;
  }

  /**
   * Atomic {@link #state} update, doesn't happen if current {@link STATE} not among allowed values.
   */
  @SafeVarargs
  public final StateUpdate< STATE > update(
      final STATE newState,
      final boolean logFailedUpdate,
      final STATE... allowedStates
  ) {
    return update( newState, STATE_PREDICATE_ALWAYS_FALSE, logFailedUpdate, allowedStates ) ;
  }
  /**
   * Atomic {@link #state} update, doesn't happen if current {@link STATE} not among allowed values.
   *
   * @param blackholePredicate a non-{@code null} value means to not perform the transition if current
   *     current value has this value.
   */
  @SafeVarargs
  public final StateUpdate< STATE > update(
      final STATE newState,
      final Predicate< STATE > blackholePredicate,
      final boolean logFailedUpdate,
      final STATE... allowedStates
  ) {
    checkNotNull( blackholePredicate ) ;
    final UnaryOperator< STATE > stateUnaryOperator = unaryOperatorForAttemptingUpdate(
        newState, blackholePredicate, allowedStates ) ;

    /** Because our unary operator is purely functional, we can reapply it safely to previous
     * value, so we obtain the new value without re-getting, which would be non-atomic.
     * We delegate this calculation to the {@link StateUpdate} object so it keeps track of
     * {@link StateUpdate#previous} value. */
    final StateUpdate< STATE > stateUpdate =
        new StateUpdate<>( state.getAndUpdate( stateUnaryOperator ), newState, stateUnaryOperator ) ;
    if( logger != null ) {
      if( stateUpdate.wishedState() ) {
        logStateUpdate( stateUpdate.current, stateUpdate.previous ) ;
      } else if( blackholePredicate.test( stateUpdate.current ) ) {
        logBlackholeCausingNoUpdate( stateUpdate.current ) ;
      } else if( logFailedUpdate ){
        logger.warn( "State update into " + newState + " didn't happen, switch from " +
            this + " not allowed." ) ;
      }
    }
    return stateUpdate ;
  }

  @SafeVarargs
  private static < STATE > UnaryOperator< STATE > unaryOperatorForAttemptingUpdate(
      final STATE newState,
      final Predicate< STATE > blackholePredicate,
      final STATE... allowedStates
  ) {
    return s -> {
      if( blackholePredicate.test( s ) ) {
        return s ;
      }
      for( final STATE allowed : allowedStates )
        if( s == allowed ) {
          return newState ;
        }
      return s ;
    } ;
  }

//  public STATE getAndUpdate( final UnaryOperator< STATE > updateFunction ) {
//    checkNotNull( updateFunction ) ;
//    return state.getAndUpdate( updateFunction ) ;
//  }
//
//  public STATE updateAndGet( final UnaryOperator< STATE > updateFunction ) {
//    checkNotNull( updateFunction ) ;
//    return state.updateAndGet( updateFunction ) ;
//  }

  private void logStateUpdate( final STATE current, final STATE previous ) {
    final String ownerToString = owner.get() ;
    logger.info( "State updated into " + current + " from " +
        previous + " in " + ownerToString + "." ) ;
    if( current == previous ) {
      logger.warn( "Double update occured from " + previous + " to " + current + " into " +
          ownerToString + "." ) ;
    }
  }

  private void logBlackholeCausingNoUpdate( final STATE current ) {
    final String ownerToString = owner.get() ;
    logger.info( "State blackholed to " + current + " in " + ownerToString + "." ) ;
  }

  public static final class StateUpdate< STATE > {
    public final STATE previous ;
    public final STATE wished ;
    public final STATE current ;

    public StateUpdate(
        final STATE previous,
        final STATE wished,
        final UnaryOperator< STATE > stateUnaryOperator
    ) {
      this.previous = checkNotNull( previous ) ;
      this.wished = checkNotNull( wished ) ;
      final STATE applied = stateUnaryOperator.apply( previous ) ;
      this.current = applied == wished ? wished : previous ;
    }

    public boolean wishedState() {
      return current == wished ;
    }

    public boolean happened() {
      return current != previous ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + '{' +
          "current=" + current + ';' +
          "wished=" + wished + ';' +
          "previous" + previous +
          '}'
      ;
    }
  }

  private final Predicate< STATE > STATE_PREDICATE_ALWAYS_FALSE = __ -> false ;

}
