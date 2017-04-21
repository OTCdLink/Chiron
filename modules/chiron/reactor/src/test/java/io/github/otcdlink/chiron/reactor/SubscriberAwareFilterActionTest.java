package io.github.otcdlink.chiron.reactor;

import org.junit.Test;
import org.reactivestreams.Subscriber;
import reactor.Environment;
import reactor.fn.Predicate;
import reactor.rx.Stream;
import reactor.rx.action.Action;
import reactor.rx.broadcast.Broadcaster;

import java.util.IdentityHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.otcdlink.chiron.reactor.SubscriberAwareFilterAction.rerouteFor;
import static io.github.otcdlink.chiron.reactor.SubscriberAwareFilterAction.routeWith;
import static org.assertj.core.api.Assertions.assertThat;

public class SubscriberAwareFilterActionTest {

  @Test
  public void rerouteSingle() throws Exception {
    final Action< Integer, Integer > subscriberAwareFilterAction = rerouteFor(
        oddBroadcaster, i -> i % 2 != 0 ) ;

    prepareWith( subscriberAwareFilterAction ) ;
    injectAndCheck() ;
  }

  @Test
  public void rerouteWithMap() throws Exception {
    final IdentityHashMap< Subscriber< ? super Integer >, Predicate< Integer > > rules =
        new IdentityHashMap<>() ;
    rules.put( oddBroadcaster, i -> i % 2 != 0 ) ;
    rules.put( evenBroadcaster, i -> i % 2 == 0 ) ;
    rules.put( null, i -> { throw new RuntimeException( "Wrong" ) ; } ) ;
    final Action< Integer, Integer > subscriberAwareFilterAction = routeWith( rules ) ;

    prepareWith( subscriberAwareFilterAction ) ;
    injectAndCheck() ;
  }


// =======
// Fixture
// =======

  static {
    Environment.initializeIfEmpty() ;
  }

  private static Stream< Integer > createCountingStream(
      final String name,
      final Broadcaster< Integer > broadcaster,
      final AtomicInteger counter,
      final Semaphore endSemaphore
  ) {
    return broadcaster
        .observe( i -> {
          counter.incrementAndGet() ;
          println( name, "broadcaster#consume ", i ) ;
        } )
        .observeError(
            Throwable.class,
            ( Ø, t ) -> {
              endSemaphore.release() ;
              println( name, "broadcaster#consume/error ", t ) ;
            }
        )
        .observeComplete(
            Ø -> {
              endSemaphore.release() ;
              println( "broadcaster#consume/complete " ) ;
            }
        )
    ;
  }

  private final Semaphore endSemaphore = new Semaphore( 0 ) ;
  private final Broadcaster< Integer > sourceBroadcaster = Broadcaster.create() ;
  private final Broadcaster< Integer > oddBroadcaster = Broadcaster.create() ;
  private final Broadcaster< Integer > evenBroadcaster = Broadcaster.create() ;

  private final AtomicInteger evenCounter = new AtomicInteger() ;
  private final AtomicInteger oddCounter = new AtomicInteger() ;

  private final Stream< Integer > sourceStream = sourceBroadcaster
      .dispatchOn( Environment.get().getCachedDispatcher() )
      .observe( i -> println( "sourceStream#observe " + i ) )
      .observeComplete( Ø -> println( "sourceStream#observeComplete" ) )
  ;

  private final Stream< Integer > oddStream = createCountingStream(
      "odd", oddBroadcaster, oddCounter, endSemaphore ) ;

  private final Stream< Integer > evenStream = createCountingStream(
      "even", evenBroadcaster, evenCounter, endSemaphore ) ;

  private void prepareWith( final Action< Integer, Integer > subscriberAwareFilterAction ) {
    sourceStream.subscribe( subscriberAwareFilterAction ) ;
    subscriberAwareFilterAction.subscribe( oddBroadcaster ) ;
    subscriberAwareFilterAction.subscribe( evenBroadcaster ) ;
    oddStream.consume() ;
    evenStream.consume() ;
  }

  private void injectAndCheck() throws InterruptedException {
    for( final Integer i : new Integer[]{ 0, 1, 2, 3, 4, 5, 6 } ) {
      sourceBroadcaster.onNext( i ) ;
    }
    sourceBroadcaster.onComplete() ;

    println( "Waiting for even and odd streams to complete ..." ) ;
    endSemaphore.acquire( 2 ) ;

    assertThat( evenCounter.get() ).isEqualTo( 4 ) ;
    assertThat( oddCounter.get() ).isEqualTo( 3 ) ;
  }


  private static void println( final Object... fragments ) {
    final Thread currentThread = Thread.currentThread() ;
    synchronized( System.out ) {
      System.out.print( String.format( "[%s] ", currentThread.getName() ) ) ;
      for( final Object fragment : fragments ) {
        System.out.print( fragment ) ;
      }
      System.out.println() ;
    }
  }

}