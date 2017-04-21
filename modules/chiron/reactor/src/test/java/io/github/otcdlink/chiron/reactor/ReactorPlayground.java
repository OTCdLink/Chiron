package io.github.otcdlink.chiron.reactor;

import com.google.common.collect.ImmutableMap;
import org.junit.Ignore;
import org.junit.Test;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import reactor.Environment;
import reactor.core.config.DispatcherType;
import reactor.core.processor.CancelException;
import reactor.core.processor.RingBufferProcessor;
import reactor.fn.BiConsumer;
import reactor.fn.Consumer;
import reactor.fn.Supplier;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.action.Action;
import reactor.rx.broadcast.Broadcaster;
import reactor.rx.subscription.PushSubscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertEquals;

/**
 *
 * Hi all,
 * I'm trying to add something like a catch-all clause in my pipeline. The documentation doesn't
 * tell much about interactions between {@link Stream#observeError(Class, BiConsumer)},
 * {@link Stream#retry()} and {@link Stream#consume()}.
 * I wrote some tests and I'm happy with what I've found, but guessing it requires too many
 * assumptions so I suggest to add the following to the documentation:
 *
 * Any {@code Throwable} thrown in the Pipeline
 * propagates downstream. This causes every {@link Stream#observeError(Class, BiConsumer)}
 * to be called. If at the very bottom of the Pipeline there is no
 * {@link Stream#consume(Consumer, Consumer)} (the one with error handling), then nothing
 * visible happens, but at the next {@link Subscriber#onNext(Object)} a {@link CancelException}
 * is thrown. Any error thrown from a {@link Processor} that the {@link Stream} delegates to
 * is considered as thrown by the {@link Stream} itself.
 *
 * The {@link Stream#retry()} method stops downstream propagation of a matching error and
 * therefore prevents cancellation.
 *
 * A way to program a catch-all block that keeps the Pipeline alive is to it with
 * {@link Stream#observeError(Class, BiConsumer)} (matching every {@code Throwable}) and
 * {@link Stream#retry()}. Then there is no need to add an error handler to the
 * {@link Stream#consume()} method.
 *
 * I'm attaching the test cases, if worth something.
 *
 */
public class ReactorPlayground {

  @SuppressWarnings( "ThrowableResultOfMethodCallIgnored" )
  @Test( timeout = TIMEOUT )
  @Ignore( "Doesn't work" )
  public void tryingToCatchEverything() throws Exception {

    final BlockingQueue< Throwable > throwableRecorder = new ArrayBlockingQueue<>( 1 ) ;
    final BlockingQueue< Integer > consumptionRecorder = new ArrayBlockingQueue<>( 1 ) ;
    final BlockingQueue< Void > completionRecorder = new ArrayBlockingQueue<>( 1 ) ;

    final Processor< Integer, Integer > processor = RingBufferProcessor.create() ;
    final Stream< Integer > stream = Streams
        .wrap( processor )
        .map( i -> {
          println( "Processing " + i + " ..." ) ;
          if( i == -1 ) {
            println( "Throwing exception because we hit magic value " + i + "!" ) ;
            throw new RuntimeException( "Boom" ) ;
          }
          return i ;
        } )
        .observeError( Exception.class, ( i, e ) -> throwableRecorder.add( e ) )
        .lift( () -> new Action<Integer, Integer>() {
          @Override
          protected void doNext( final Integer command ) {
            broadcastNext( command );
          }

          @Override
          public void onError( final Throwable cause ) {
            throwableRecorder.add( cause );
          }
        } )
    ;

    // Using processor.onNext() directly causes multiple processings, using a Subscriber doesn't.
    Streams.just( -1, 0 ).subscribe( processor ) ;
    stream.consume(
        consumptionRecorder::add,
        Ø -> {},
        Ø -> completionRecorder.add( null )
    ) ;

    println( "Processor started, waiting for error observer ..." ) ;
    assertEquals( throwableRecorder.take().getMessage(), "Boom" ) ;
    assertEquals( ( int ) consumptionRecorder.take(), 0 ) ;
    assertEquals( completionRecorder.take(), null ) ;
  }

  @Test
  public void errorInProcessor() throws Exception {
    final BlockingQueue< Throwable > throwableRecorder = new ArrayBlockingQueue<>( 1 ) ;
    final Processor< Integer, Integer > processor = RingBufferProcessor.create() ;
    final Stream< Integer > stream = Streams
        .wrap( processor )
        .map( i -> {
          println( "Processing " + i + " ..." ) ;
          if( true ) {
            throw new RuntimeException( "Boom" ) ;
          }
          return i ;
        } )
        .observeError( Throwable.class, ( i, t ) -> throwableRecorder.add( t ) )
        .retry()
        ;

    // Using processor.onNext() directly causes multiple processings, using a Subscriber doesn't.
    Streams.just( 0 ).subscribe( processor ) ;
    stream.consume() ;

    println( "Processor started, waiting for error observer ..." ) ;
    final Throwable throwable = throwableRecorder.take() ;
    assertEquals( throwable.getMessage(), "Boom" ) ;

  }


  /**
   * A {@code Throwable} goes down all the {@link Stream} chain, causing every downstream
   * {@link Stream#observeError(Class, BiConsumer)} and
   * {@link Stream#consume(Consumer, Consumer)} to be called.
   * Since there is no {@link Stream#retry()} this causes further calls to
   * {@link Subscriber#onNext(Object)} to throw a {@link CancelException}.
   */
  @Test
  public void errorWithoutConsume() throws Exception {
    final Broadcaster< Integer > sourceBroadcaster = Broadcaster.create(
        Environment.newDispatcher( 1, 1, DispatcherType.SYNCHRONOUS )
    );
    final Stream< Integer > intStream = sourceBroadcaster
        .observe( i -> {
          if( i == -1 ) {
            throw new RuntimeException( "Boom: " + i ) ;
          }
        } )
        .observeError( Throwable.class, ( i, t ) -> println( "intStream#observeError " + t ) )
    ;
    intStream.consume() ;

    sourceBroadcaster.onNext( -1 ) ;
    try {
      sourceBroadcaster.onNext( 1 ) ;
      fail( "Did not catch " + CancelException.class ) ;
    } catch( final CancelException ignore ) { }

  }
    /**
     * A {@code Throwable} goes down all the {@link Stream} chain, causing every downstream
     * {@link Stream#observeError(Class, BiConsumer)} and
     * {@link Stream#consume(Consumer, Consumer)} to be called.
     * Since there is no {@link Stream#retry()} this causes further calls to
     * {@link Subscriber#onNext(Object)} to throw a {@link CancelException}.
     */
  @Test
  public void consumingAnErrorMeansPipelineDeath() throws Exception {
    final List< Throwable > environmentRecorder = new ArrayList<>() ;
    final List< Throwable > intObserveRecorder = new ArrayList<>() ;
    final List< Throwable > stringObserveRecorder = new ArrayList<>() ;
    final List< Throwable > consumeRecorder = new ArrayList<>() ;
    final Environment environment =
        new Environment().assignErrorJournal( environmentRecorder::add ) ;
    final Broadcaster< Integer > sourceBroadcaster = Broadcaster.create(
        environment,
        Environment.newDispatcher( 1, 1, DispatcherType.SYNCHRONOUS )
    ) ;
    final Stream< Integer > intStream = sourceBroadcaster
        .observe( i -> {
          if( i == -1 ) { throw new RuntimeException( "Boom: " + i ) ; }
        } )
        .observeError( Throwable.class, ( i, t ) -> {
          println( "intStream#observeError " + t ) ;
          intObserveRecorder.add( t ) ;
        } )
    ;

    final Stream< String > stringStream = intStream
        .map( i -> "s" + i )
        .observe( s -> {
          if( s.equals( "s1" ) ) {
            throw new RuntimeException( "Boom: " + s ) ;
          }
        } )
        .observeError( Throwable.class, ( s, t ) -> {
          println( "stringStream#observeError " + t ) ;
          stringObserveRecorder.add( t ) ;
        } )
    ;

    stringStream.consume(
        s -> println( "stringStream#consume " + s ),
        t -> {
          println( "stringStream#consumeError " + t ) ;
          consumeRecorder.add( t ) ;
        }
    ) ;
    sourceBroadcaster.onNext( -1 ) ;
    assertEquals( consumeRecorder.size(), 1 ) ;
    assertEquals( intObserveRecorder.size(), 1 ) ;
    assertEquals( stringObserveRecorder.size(), 1 ) ;
    assertEquals( environmentRecorder.size(), 0 ) ;

    try {
      sourceBroadcaster.onNext( 1 ) ;
      fail( "Did not catch " + CancelException.class.getName() ) ;
    } catch( final CancelException ignore ) { }

  }


  /**
   * The {@link Stream#retry()} at the start of the pipeline causes upstream
   * {@link Stream#observeError(Class, BiConsumer)} to be called, but prevents downstream
   * error propagation.
   * If the error occurs downstream (under the {@link Stream#retry()}) then it propates
   * normally.
   */
  @Test
  public void retryAtPipelineStart() throws Exception {
    final List< Throwable > environmentRecorder = new ArrayList<>() ;
    final List< Throwable > intObserveRecorder = new ArrayList<>() ;
    final List< Throwable > stringObserveRecorder = new ArrayList<>() ;
    final List< Throwable > consumeRecorder = new ArrayList<>() ;
    final Environment environment =
        new Environment().assignErrorJournal( environmentRecorder::add ) ;
    final Broadcaster< Integer > sourceBroadcaster = Broadcaster.create(
        environment,
        Environment.newDispatcher( 1, 1, DispatcherType.SYNCHRONOUS )
    ) ;
    final Stream< Integer > intStream = sourceBroadcaster
        .observe( i -> {
          if( i == -1 ) {
            throw new RuntimeException( "Boom: " + i ) ;
          }
        } )
        .observeError( Throwable.class, ( i, t ) -> {
          println( "intStream#observeError " + t ) ;
          intObserveRecorder.add( t ) ;
        } )
        .retry()
    ;

    final Stream< String > stringStream = intStream
        .map( i -> "s" + i )
        .observe( s -> {
          if( s.equals( "s1" ) ) {
            throw new RuntimeException( "Boom: " + s ) ;
          }
        } )
        .observeError( Throwable.class, ( s, t ) -> {
          println( "stringStream#observeError " + t ) ;
          stringObserveRecorder.add( t ) ;
        } )
    ;

    stringStream.consume(
        s -> println( "stringStream#consume " + s ),
        t -> {
          println( "stringStream#consumeError " + t ) ;
          consumeRecorder.add( t ) ;
        }
    ) ;
    sourceBroadcaster.onNext( -1 ) ;
    assertEquals( intObserveRecorder.size(), 1 ) ;
    assertEquals( stringObserveRecorder.size(), 0 ) ;
    assertEquals( consumeRecorder.size(), 0 ) ;
    assertEquals( environmentRecorder.size(), 0 ) ;

    clear( intObserveRecorder, stringObserveRecorder, consumeRecorder, environmentRecorder ) ;
    sourceBroadcaster.onNext( 0 ) ; // Doesn't break
    assertEquals( intObserveRecorder.size(), 0 ) ;
    assertEquals( stringObserveRecorder.size(), 0 ) ;
    assertEquals( consumeRecorder.size(), 0 ) ;
    assertEquals( environmentRecorder.size(), 0 ) ;

    clear( intObserveRecorder, stringObserveRecorder, consumeRecorder, environmentRecorder ) ;
    sourceBroadcaster.onNext( 1 ) ;
    assertEquals( intObserveRecorder.size(), 0 ) ;
    assertEquals( stringObserveRecorder.size(), 1 ) ;
    assertEquals( consumeRecorder.size(), 1 ) ;
    assertEquals( environmentRecorder.size(), 0 ) ;
  }

  /**
   * The {@link Stream#retry()} at the end of the pipeline prevents error propagation to
   * {@link Stream#consume(Consumer, Consumer)} but every downstream
   * {@link Stream#observeError(Class, BiConsumer)} gets called.
   */
  @Test
  public void recoverAtPipelineEnd() throws Exception {
    final List< Throwable > environmentRecorder = new ArrayList<>() ;
    final List< Throwable > intObserveRecorder = new ArrayList<>() ;
    final List< Throwable > stringObserveRecorder = new ArrayList<>() ;
    final List< Throwable > consumeRecorder = new ArrayList<>() ;
    final Environment environment =
        new Environment().assignErrorJournal( environmentRecorder::add ) ;
    final Broadcaster< Integer > sourceBroadcaster = Broadcaster.create(
        environment,
        Environment.newDispatcher( 1, 1, DispatcherType.SYNCHRONOUS )
    ) ;
    final Stream< Integer > intStream = sourceBroadcaster
        .observe( i -> {
          if( i == -1 ) {
            throw new RuntimeException( "Boom: " + i ) ;
          }
        } )
        .observeError( Throwable.class, ( i, t ) -> {
          println( "intStream#observeError " + t ) ;
          intObserveRecorder.add( t ) ;
        } )
    ;

    final Stream< String > stringStream = intStream
        .map( i -> "s" + i )
        .observe( s -> {
          if( s.equals( "s1" ) ) {
            throw new RuntimeException( "Boom: " + s ) ;
          }
        } )
        .observeError( Throwable.class, ( s, t ) -> {
          println( "stringStream#observeError " + t ) ;
          stringObserveRecorder.add( t ) ;
        } )
        .retry()
    ;

    stringStream.consume(
        s -> println( "stringStream#consume " + s ),
        t -> {
          println( "stringStream#consumeError " + t ) ;
          consumeRecorder.add( t ) ;
        }
    ) ;
    sourceBroadcaster.onNext( -1 ) ;
    assertEquals( intObserveRecorder.size(), 1 ) ;
    assertEquals( stringObserveRecorder.size(), 1 ) ;
    assertEquals( consumeRecorder.size(), 0 ) ;
    assertEquals( environmentRecorder.size(), 0 ) ;

    clear( intObserveRecorder, stringObserveRecorder, consumeRecorder, environmentRecorder ) ;
    sourceBroadcaster.onNext( 0 ) ; // Doesn't break
    assertEquals( intObserveRecorder.size(), 0 ) ;
    assertEquals( stringObserveRecorder.size(), 0 ) ;
    assertEquals( consumeRecorder.size(), 0 ) ;
    assertEquals( environmentRecorder.size(), 0 ) ;

    clear( intObserveRecorder, stringObserveRecorder, consumeRecorder, environmentRecorder ) ;
    sourceBroadcaster.onNext( 1 ) ;
    assertEquals( intObserveRecorder.size(), 0 ) ;
    assertEquals( stringObserveRecorder.size(), 1 ) ;
    assertEquals( consumeRecorder.size(), 0 ) ;
    assertEquals( environmentRecorder.size(), 0 ) ;
  }







  /**
   * Useful to see what happens in a simple case.
   */
  @Test
  public void streamInterception() throws Exception {

    final Semaphore endSemaphore = new Semaphore( 0 ) ;
    final Broadcaster< Integer > sourceBroadcaster = Broadcaster.create() ;


    final Stream< Integer > sourceStream = sourceBroadcaster
        .dispatchOn( Environment.get().getCachedDispatcher() )
        .observe( i -> println( "sourceStream#observe " + i ) )
        .observeComplete( Ø -> println( "sourceStream#observeComplete" ) )
    ;

    final Action< Integer, Integer > routingAction = new Action< Integer, Integer >() {
      @Override
      protected void doNext( final Integer i ) {
        println( "routingAction#doNext " + i ) ;
        broadcastNext( i ) ;
      }
    } ;
    sourceStream.subscribe( routingAction ) ;

    final Action< Integer, Integer > targetAction1 = new Action< Integer, Integer >() {
      @Override
      protected void doNext( final Integer i ) {
        println( "targetAction1#doNext " + i ) ;
      }

      @Override
      protected void doComplete() {
        endSemaphore.release() ;
      }

      @Override
      protected void doError( final Throwable t ) {
        println( "targetAction1#doError " + t ) ;
      }
    } ;
    final Action< Integer, Integer > targetAction2 = new Action< Integer, Integer >() {
      @Override
      protected void doNext( final Integer i ) {
        println( "targetAction2#doNext " + i ) ;
      }

      @Override
      protected void doComplete() {
        endSemaphore.release() ;
      }

      @Override
      protected void doError( final Throwable t ) {
        println( "targetAction2#doError " + t ) ;
      }
    } ;

    routingAction.subscribe( targetAction1 ) ;
    routingAction.subscribe( targetAction2 ) ;


    targetAction1.consume() ;
    targetAction2.consume() ;
    consumeAndWaitForCompletion( sourceBroadcaster, 1, endSemaphore,
        0, 1, 2, 3, 4, 5, 6, 7 ) ;

    endSemaphore.acquire( 1 ) ;
  }





  /**
   * Switches between two {@link Stream}s created on-the-fly. Trying to fix subscription problem.
   */
  @Test
  public void streamChoice1bis() throws Exception {

    final Semaphore doneSemaphore = new Semaphore( 0 ) ;

    // Doesn't work when trying to assign its own dispatcher.
    final Broadcaster< Integer > insertionBroadcaster = Broadcaster.create() ;
    final Stream< Integer > insertionStream = insertionBroadcaster
        .observe( s -> println( "Inserted: " + s ) )
        .observeComplete( Ø -> doneSemaphore.release() )
    ;

    final ImmutableMap< Counter, AtomicInteger > counters = ImmutableMap.of(
        Counter.FIRST, new AtomicInteger(),
        Counter.SECOND, new AtomicInteger()
    ) ;

    final Action< Integer, Integer > switchAction = new Action< Integer, Integer >() {

      /** Prevent cancellation when removing {@link #downstreamSubscription()}. */
      private boolean removingDownstreamSubscription = false ;

      // Remember last subscription so we can reuse it.
      private final PushSubscription< Integer > previousSubscription = null ;

      // Remember previous choice so we know if we should resubscribe.
      private final Counter previousCounter = null ;

      private Subscriber< Integer > accumulatorStream(
          final String name,
          final AtomicInteger counter
      ) {
        final Action< Integer, Void > action = new Action< Integer, Void >() {
          @Override
          protected void doNext( final Integer i ) {
            counter.addAndGet( i ) ;
            println( "Adding " + i + " to " + name + " (total is now " + counter.get() + ")." ) ;
          }
          @Override
          public void onComplete() {
            println( "Stream " + name + " completed." ) ;
          }
        } ;
        return action ;
      }

      @Override
      public void cancel() {
        if( ! removingDownstreamSubscription ) {
          super.cancel() ;
        }
      }

      @Override
      protected void doNext( final Integer inbound ) {
        if( inbound % 3 == 0 ) {
          if( downstreamSubscription() != null ) {
            downstreamSubscription().onComplete() ; // TODO: don't call it more than once.
            try {
              removingDownstreamSubscription = true ;
              downstreamSubscription().cancel() ;
            } finally {
              removingDownstreamSubscription = false ;
            }
          }
          final Counter counter = Counter.ordinal( inbound % 2 ) ;

          final Subscriber< Integer > newSubscriber =
              accumulatorStream( counter.name().toLowerCase(), counters.get( counter ) ) ;
          subscribe( newSubscriber ) ;
        }
        broadcastNext( inbound ) ;
      }
    } ;

    insertionStream.subscribe( switchAction ) ;

    consumeAndWaitForCompletion( insertionBroadcaster, doneSemaphore,
        0, 1, 2, 3, 4, 5, 6, 7 ) ;
    //  ^        ^        ^
    //  |        |        |
    //  |        |        counter1
    //  |        counter2
    //  counter1

    for( final Counter counter : Counter.values() ) {
      println( counter + " = " + counters.get( counter ).get() ) ;
    }
  }
  /**
   * Switches between two {@link Stream}s created on-the-fly. Doesn't work well because
   * of resubscription.
   */
  @Test
  public void streamChoice1() throws Exception {

    final Semaphore doneSemaphore = new Semaphore( 0 ) ;

    // Doesn't work when trying to assign its own dispatcher.
    final Broadcaster< Integer > insertionBroadcaster = Broadcaster.create() ;
    final Stream< Integer > insertionStream = insertionBroadcaster
        .observe( s -> println( "Inserted: " + s ) )
        .observeComplete( Ø -> doneSemaphore.release() )
    ;

    final AtomicInteger counter1 = new AtomicInteger() ;
    final AtomicInteger counter2 = new AtomicInteger() ;

    final Action< Integer, Integer > switchAction = new Action< Integer, Integer >() {

      /**
       * Prevent cancellation when removing {@link #downstreamSubscription()}.
       */
      private boolean removingDownstreamSubscription = false ;

      private Subscriber< Integer > accumulatorStream(
          final String name,
          final AtomicInteger counter
      ) {
        final Action< Integer, Void > action = new Action< Integer, Void >() {
          @Override
          protected void doNext( final Integer i ) {
            counter.addAndGet( i ) ;
            println( "Adding " + i + " to " + name + " (total is now " + counter.get() + ")." ) ;
          }

          @Override
          public void onComplete() {
            println( "Stream " + name + " completed." ) ;
          }

        } ;

        return action ;
      }

      @Override
      public void cancel() {
        if( ! removingDownstreamSubscription ) {
          super.cancel() ;
        }
      }

      @Override
      protected void doNext( final Integer inbound ) {
        if( inbound % 3 == 0 ) {
          if( downstreamSubscription() != null ) {
            downstreamSubscription().onComplete() ; // TODO: don't call it more than once.
            try {
              removingDownstreamSubscription = true ;
              downstreamSubscription().cancel() ;
            } finally {
              removingDownstreamSubscription = false ;
            }
          }
          if( inbound % 2 == 0 ) {
            subscribe( accumulatorStream( "one", counter1 ) ) ;
          } else {
            subscribe( accumulatorStream( "two", counter2 ) ) ;
          }
        }
        broadcastNext( inbound ) ;
      }
    } ;

    insertionStream.subscribe( switchAction ) ;

    consumeAndWaitForCompletion( insertionBroadcaster, doneSemaphore,
        0, 1, 2, 3, 4, 5, 6, 7 ) ;
    //  ^        ^        ^
    //  |        |        |
    //  |        |        counter1
    //  |        counter2
    //  counter1

    println( "counter1 = " + counter1.get() ) ;
    println( "counter2 = " + counter2.get() ) ;
  }



  static {
    Environment.initializeIfEmpty() ;
  }

  @SafeVarargs
  private static < O > void consumeAndWaitForCompletion(
      final Broadcaster< O > broadcaster,
      final Semaphore semaphore,
      final O... objects
  ) throws InterruptedException {
    consumeAndWaitForCompletion( broadcaster, 1, semaphore, objects ) ;
  }

  @SafeVarargs
  private static < O > void consumeAndWaitForCompletion(
      final Broadcaster< O > broadcaster,
      final int permits,
      final Semaphore semaphore,
      final O... objects
  ) throws InterruptedException {
    for( final O o : objects ) {
      broadcaster.onNext( o ) ;
    }
    broadcaster.onComplete() ;
    semaphore.acquire( permits ) ;
  }


  private static void println( final String text ) {
    System.out.println( '[' + Thread.currentThread().getName() + "] " + text ) ;
  }

  enum Counter {

    FIRST,
    SECOND,
    ;

    public static Counter ordinal( final int ordinal ) {
      for( int i = 0 ; i < values().length ; i ++ ) {
        if( i == ordinal ) {
          return values()[ i ] ;
        }
      }
      throw new IllegalArgumentException( "Unsupported: " + ordinal ) ;
    }
  }

  private static void clear( final List<?>... lists ) {
    for( final List< ? > list : lists ) {
      list.clear() ;
    }
  }

  public static < COMMAND > Supplier< Action< COMMAND, COMMAND > > createCatchingAction(
      final Consumer< Throwable > throwableConsumer
  ) {
    return () -> new Action< COMMAND, COMMAND >() {
      @Override
      protected void doNext( final COMMAND command ) {
        broadcastNext( command ) ;
      }
      @Override
      public void onError( final Throwable cause ) {
        throwableConsumer.accept( cause ) ;
      }
    } ;
  }


  private static final long TIMEOUT = 1000 ;
}
