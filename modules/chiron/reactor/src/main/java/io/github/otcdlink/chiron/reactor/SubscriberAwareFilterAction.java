package io.github.otcdlink.chiron.reactor;

import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.processor.CancelException;
import reactor.core.support.Exceptions;
import reactor.fn.BiFunction;
import reactor.fn.Predicate;
import reactor.rx.action.Action;
import reactor.rx.subscription.FanOutSubscription;
import reactor.rx.subscription.PushSubscription;

import java.util.IdentityHashMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Delegates to a {@code BiFunction} the decision to call {@link Subscriber#onNext(Object)} or not,
 * so we can prevent some events to flow along some parts of the pipeline.
 *
 * How to reroute only events matching a condition to some well-known {@link Subscriber}:
 * <pre>
 * final Action< E, E > action = new SubscriberAwareFilterAction< E >(
 *   ( subscriber, event ) -> {
 *     final boolean shouldReroute = shouldReroute( event ) ;
 *     if( subscriber == subscriberToRerouteTo ) {
 *       return shouldReroute ;
 *     } else {
 *       return ! shouldReroute ;
 *     }
 *   }
 * ) ;
 * </pre>
 * The above can be shortened to:
 * <pre>
 * final Action< E, E > action = SubscriberAwareFilterAction.rerouteFor(
 *     subscriberToRerouteTo, event -> shouldReroute( event ) ) ;
 * </pre>
 * The generalization of the above (considering only 2 {@link Subscriber}s):
 * <pre>
 * final IdentityHashMap< Subscriber< ? super E >, Predicate< E > ) rules = new IdentityHashMap<>() ;
 * rules.put( subscriberToRerouteTo, event -> shouldReroute( event ) ) ;
 * rules.put( otherSubscriber, event -> ! shouldReroute( event ) ) ;
 * final Action< E, E > action = SubscriberAwareFilterAction.rerouteFor( rules ) ;
 * </pre>
 */
public final class SubscriberAwareFilterAction< O > extends Action< O, O > {

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( SubscriberAwareFilterAction.class ) ;

  private final BiFunction< Subscriber< ? super O >, O, Boolean > destinationChoser ;

  public SubscriberAwareFilterAction(
      final BiFunction< Subscriber< ? super O >, O, Boolean > destinationChoser
  ) {
    this.destinationChoser = checkNotNull( destinationChoser ) ;
  }

  @Override
  protected void doNext( final O event ) {
    // LOGGER.debug( "Deciding for " + event + " ..." ) ;
    final PushSubscription< O > myDownstreamSubscription = downstreamSubscription() ;
    if( myDownstreamSubscription instanceof FanOutSubscription ) {
      final FanOutSubscription< O > fanOut = ( FanOutSubscription< O > ) myDownstreamSubscription ;
      for( final PushSubscription< O > someSubscription : fanOut.getSubscriptions() ) {
        final Subscriber< ? super O > subscriber = someSubscription.getSubscriber() ;
        if( destinationChoser.apply( subscriber, event ) ) {
          pushToDestination( subscriber, event ) ;
          // LOGGER.debug( "Pushed " + event + " into " + subscriber + "." ) ;
        }
      }
    } else {
      final Subscriber< ? super O > subscriber = myDownstreamSubscription.getSubscriber() ;
      if( destinationChoser.apply( subscriber, event ) ) {
        pushToDestination( subscriber, event ) ;
        // LOGGER.debug( "Pushed " + event + " into " + subscriber + "." ) ;
      }
    }
  }

  private void pushToDestination( final Subscriber< ? super O > destination, final O event ) {
    try { // Copied from Action
      destination.onNext( event ) ;
    } catch( final CancelException ce ) {
      throw ce ;
    } catch ( final Throwable throwable ) {
      doError( Exceptions.addValueAsLastCause( throwable, event ) ) ;
    }
  }

  /**
   * If the {@link Predicate} evaluates to {@code true}, let the events flow only to given
   * {@link Subscriber}, otherwise let them flow to all {@link Subscriber}s except the given one.
   * This is like saying "Take the alternate path in this case."
   */
  public static < O > SubscriberAwareFilterAction< O > rerouteFor(
      final Subscriber< ? super O > subscriberToRerouteFor,
      final Predicate< O > reroutingPredicate
  ) {
    return new SubscriberAwareFilterAction<> ( ( subscriber, event ) -> {
      final boolean reroute = reroutingPredicate.test( event ) ;
      if( subscriber == subscriberToRerouteFor ) {
        return reroute ;
      } else {
        return ! reroute ;
      }
    } ) ;
  }

  /**
   * Creates an {@link Action} calling {@link Subscriber#onNext(Object)} for every
   * {@link Subscriber} that appears in {@code Map}'s keys, and for which the event matches
   * associated {@link Predicate}.
   * A {@code null} {@link Predicate} is considered as always returning {@code false}.
   * A {@code null} {@link Subscriber} is ignored.
   *
   * @param filteringRules a non-{@code null} object, will be defensively cloned.
   */
  public static < O > SubscriberAwareFilterAction< O > routeWith(
      final IdentityHashMap< Subscriber< ? super O >, Predicate< O > > filteringRules
  ) {
    @SuppressWarnings( "unchecked" )
    final IdentityHashMap< Subscriber< ? super O >, Predicate< O > > safeRules =
        ( IdentityHashMap< Subscriber< ? super O >, Predicate< O > > ) filteringRules.clone() ;
    return new SubscriberAwareFilterAction<> ( ( subscriber, event ) -> {
      final Predicate< O > predicate = safeRules.get( subscriber ) ;
      if( predicate == null ) {
        return false ;
      } else {
        return predicate.test( event ) ;
      }
    } ) ;
  }

  public static < O > SubscriberAwareFilterAction< O > route(
      final Subscriber< ? super O > subscriber1,
      final Predicate< O > predicate1
  ) {
    final IdentityHashMap< Subscriber< ? super O >, Predicate< O > > map = new IdentityHashMap<>() ;
    map.put( subscriber1, predicate1 ) ;
    return routeWith( map ) ;
  }

  public static < O > SubscriberAwareFilterAction< O > route(
      final Subscriber< ? super O > subscriber1, final Predicate< O > predicate1,
      final Subscriber< ? super O > subscriber2, final Predicate< O > predicate2
  ) {
    final IdentityHashMap< Subscriber< ? super O >, Predicate< O > > map = new IdentityHashMap<>() ;
    map.put( subscriber1, predicate1 ) ;
    map.put( subscriber2, predicate2 ) ;
    return routeWith( map ) ;
  }

  public static < O > SubscriberAwareFilterAction< O > route(
      final Subscriber< ? super O > subscriber1, final Predicate< O > predicate1,
      final Subscriber< ? super O > subscriber2, final Predicate< O > predicate2,
      final Subscriber< ? super O > subscriber3, final Predicate< O > predicate3
  ) {
    final IdentityHashMap< Subscriber< ? super O >, Predicate< O > > map = new IdentityHashMap<>() ;
    map.put( subscriber1, predicate1 ) ;
    map.put( subscriber2, predicate2 ) ;
    map.put( subscriber3, predicate3 ) ;
    return routeWith( map ) ;
  }

  public static < O > SubscriberAwareFilterAction< O > route(
      final Subscriber< ? super O > subscriber1, final Predicate< O > predicate1,
      final Subscriber< ? super O > subscriber2, final Predicate< O > predicate2,
      final Subscriber< ? super O > subscriber3, final Predicate< O > predicate3,
      final Subscriber< ? super O > subscriber4, final Predicate< O > predicate4
  ) {
    final IdentityHashMap< Subscriber< ? super O >, Predicate< O > > map = new IdentityHashMap<>() ;
    map.put( subscriber1, predicate1 ) ;
    map.put( subscriber2, predicate2 ) ;
    map.put( subscriber3, predicate3 ) ;
    map.put( subscriber4, predicate4 ) ;
    return routeWith( map ) ;
  }

  public static < O > SubscriberAwareFilterAction< O > route(
      final Subscriber< ? super O > subscriber1, final Predicate< O > predicate1,
      final Subscriber< ? super O > subscriber2, final Predicate< O > predicate2,
      final Subscriber< ? super O > subscriber3, final Predicate< O > predicate3,
      final Subscriber< ? super O > subscriber4, final Predicate< O > predicate4,
      final Subscriber< ? super O > subscriber5, final Predicate< O > predicate5
  ) {
    final IdentityHashMap< Subscriber< ? super O >, Predicate< O > > map = new IdentityHashMap<>() ;
    map.put( subscriber1, predicate1 ) ;
    map.put( subscriber2, predicate2 ) ;
    map.put( subscriber3, predicate3 ) ;
    map.put( subscriber4, predicate4 ) ;
    map.put( subscriber5, predicate5 ) ;
    return routeWith( map ) ;
  }

  public static < O > SubscriberAwareFilterAction< O > route(
      final Subscriber< ? super O > subscriber1, final Predicate< O > predicate1,
      final Subscriber< ? super O > subscriber2, final Predicate< O > predicate2,
      final Subscriber< ? super O > subscriber3, final Predicate< O > predicate3,
      final Subscriber< ? super O > subscriber4, final Predicate< O > predicate4,
      final Subscriber< ? super O > subscriber5, final Predicate< O > predicate5,
      final Subscriber< ? super O > subscriber6, final Predicate< O > predicate6
  ) {
    final IdentityHashMap< Subscriber< ? super O >, Predicate< O > > map = new IdentityHashMap<>() ;
    map.put( subscriber1, predicate1 ) ;
    map.put( subscriber2, predicate2 ) ;
    map.put( subscriber3, predicate3 ) ;
    map.put( subscriber4, predicate4 ) ;
    map.put( subscriber5, predicate5 ) ;
    map.put( subscriber6, predicate6 ) ;
    return routeWith( map ) ;
  }



}
