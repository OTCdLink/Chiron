package io.github.otcdlink.chiron.reactor;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.otcdlink.chiron.toolbox.MultiplexingException;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import org.reactivestreams.Processor;
import org.slf4j.Logger;
import reactor.Environment;
import reactor.core.Dispatcher;
import reactor.core.processor.RingBufferProcessor;
import reactor.fn.Consumer;
import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import reactor.fn.tuple.Tuple4;
import reactor.rx.Stream;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

final class FlowgraphBuilderTools {

  private FlowgraphBuilderTools() { }


  public static void startAll( final ImmutableSet<LifecycleEnabled> lifecycleEnableds )
      throws MultiplexingException
  {
    final MultiplexingException.Collector exceptionCollector =
        MultiplexingException.newCollector() ;
    for( final LifecycleEnabled lifecycleEnabled : lifecycleEnableds ) {
      try {
        lifecycleEnabled.start() ;
      } catch( final Exception e ) {
        exceptionCollector.collect( e ) ;
      }
    }
    exceptionCollector.throwIfAny( "Could not start " + Flowgraph.class.getSimpleName() ) ;
  }



  public static
  Tuple2< Consumer<
      Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit > > ,
      ImmutableList< Object >
  >
  dispatcherShutdown( final Dispatcher... dispatchers ) {
    return FlowgraphBuilderTools.forShutdown(
        tuple4 -> {
          if( tuple4.t1.alive() ) {
            tuple4.t1.awaitAndShutdown( tuple4.t3, tuple4.t4 );
          }
        },
        dispatchers
    ) ;
  }

  public static
  Tuple2<
      Consumer< Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit > >,
      ImmutableList< Object >
  >
  stratumShutdown( final ImmutableCollection< LifecycleEnabled > stratums ) {
    return FlowgraphBuilderTools.forShutdown(
        consumerParameters -> {
          try {
            consumerParameters.t1.stop( consumerParameters.t3, consumerParameters.t4 );
          } catch( Exception e ) {
            consumerParameters.t2.collect( e );
          }

        },
        stratums
    ) ;
  }

  public static
  Tuple2<
      Consumer< Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit > >,
      ImmutableList< Object >
  >
  semaphoreAcquisition( final Semaphore semaphore, final int permits ) {
    return FlowgraphBuilderTools.forShutdown(
        consumerParameters -> {
          try {
            consumerParameters.t1.tryAcquire(
                permits, consumerParameters.t3, consumerParameters.t4 ) ;
          } catch( Exception e ) {
            consumerParameters.t2.collect( e ) ;
          }
        },
        ImmutableList.of( semaphore )
    ) ;
  }

  @SafeVarargs
  public static< SHUTTABLE >
  Tuple2<
      Consumer< Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit > >,
      ImmutableList< Object >
  >
  forShutdown(
      final Consumer<
          Tuple4< SHUTTABLE, MultiplexingException.Collector, Long, TimeUnit > > consumer,
      final SHUTTABLE... shuttables
  ) {
    return forShutdown( consumer, ImmutableList.copyOf( shuttables ) ) ;
  }

  @SuppressWarnings( "unchecked" )
  public static< SHUTTABLE >
  Tuple2<
      Consumer< Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit > > ,
      ImmutableList< Object >
  >
  forShutdown(
      final Consumer<
          Tuple4< SHUTTABLE, MultiplexingException.Collector, Long, TimeUnit > > consumer,
      final ImmutableCollection< SHUTTABLE > shuttables
  ) {
    return Tuple.of(
        ( Consumer<Tuple4<Object, MultiplexingException.Collector, Long, TimeUnit>> )
            ( Object ) consumer,
        ImmutableList.copyOf( shuttables )
    ) ;
  }

  /**
   * Runs shutdown-like methods in a bounded time.
   */
  @SafeVarargs
  public static void shutdown(
      final long timeout,
      final TimeUnit timeUnit,
      final MultiplexingException.Collector exceptionCollector,
      final Tuple2<
          Consumer< Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit > >,
          ImmutableList< Object >
      >... shuttableTuples
  ) {
    shutdown( timeout, timeUnit, exceptionCollector, ImmutableList.copyOf( shuttableTuples ) ) ;
  }

  /**
   * Runs shutdown-like methods in a bounded time.
   */
  public static void shutdown(
      final long timeout,
      final TimeUnit timeUnit,
      final MultiplexingException.Collector exceptionCollector,
      final ImmutableList< Tuple2<
          Consumer< Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit > >,
          ImmutableList< Object >
      > > shuttableTuples
  ) {
    final long timeoutInMilliseconds = timeUnit.toMillis( timeout ) ;
    long remainingTime = timeoutInMilliseconds ;
    for(
        final Tuple2<
            Consumer< Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit > >,
            ImmutableList< Object >
        > shuttableTuple : shuttableTuples
    ) {
      final long start = System.currentTimeMillis() ;
      final Consumer<
          Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit >
      > shutdownOperation = shuttableTuple.t1 ;
      for( final Object shuttable : shuttableTuple.t2 ) {
        try {
          shutdownOperation.accept( Tuple.of(
              shuttable, exceptionCollector, remainingTime, TimeUnit.MILLISECONDS ) ) ;
        } catch( final Exception e ) {
          // Callee may not catch everything.
          exceptionCollector.collect( e ) ;
        }
      }
      final long elapsed = System.currentTimeMillis() - start ;
      remainingTime = Math.max( 0, timeoutInMilliseconds - elapsed ) ;
    }

  }

  public static < COMMAND > Processor< COMMAND, COMMAND > createProcessor(
      final String name,
      final int backlog
  ) {
    return RingBufferProcessor.create( name, backlog ) ;
  }

  public static Dispatcher createDispatcher( final String name, final int backlog ) {
    return Environment.newDispatcher( name, backlog ) ;
  }


  public static final class WrapConsumption {

    private WrapConsumption() { }

    public static < COMMAND > void forStream(
        final Stream< COMMAND > stream,
        final Logger logger,
        final String name,
        final Consumer< COMMAND > nextCommandConsumer,
        final Semaphore semaphore
    ) {
      final Consumer< Void > completionConsumer =
          semaphore == null ?
          Ø -> {} :
          Ø -> semaphore.release()
      ;
      stream.consume(
          withCommandConsumer( logger, name, nextCommandConsumer ),
          withErrorConsumer( logger, name, semaphore ),
          withCompletionConsumer( logger, name, completionConsumer )
      ) ;
    }

    public static Consumer< Void > withCompletionConsumer(
        final Logger logger,
        final String name,
        final Consumer< Void > completionConsumer
    ) {
      return Ø -> {
        // logger.debug( name + "#complete" ) ;
        completionConsumer.accept( null ) ;
      } ;
    }

    static Consumer< Throwable > withErrorConsumer(
        final Logger logger,
        final String name,
        final Semaphore semaphore
    ) {
      return throwable -> {
        logger.error( "Problem inside " + name, throwable ) ;
        if( semaphore != null ) {
          semaphore.release() ;
        }
      } ;
    }

    @SuppressWarnings( { "UnusedParameters", "Convert2MethodRef" } )
    static < COMMAND > Consumer< COMMAND > withCommandConsumer(
        final Logger logger,
        final String name,
        final Consumer<COMMAND> nextCommandConsumer
    ) {
      return command -> {
//        logger.debug( name + "#consume " + command ) ;
        nextCommandConsumer.accept( command ) ;
      } ;
    }
  }

  public static class SignallingCounter {

    private final Lock lock = new ReentrantLock() ;
    private final Condition condition = lock.newCondition() ;
    private int counter = 0 ;

    public void increment() {
      lock.lock() ;
      try {
        counter ++ ;
      } finally {
        lock.unlock() ;
      }
    }

    public void decrement() {
      lock.lock() ;
      try {
        checkState( counter > 0, "Increment/decrement mismatch, can't decrement " + 0 ) ;
        counter -- ;
        condition.signal() ;
      } finally {
        lock.unlock() ;
      }
    }

    public void waitForZero( final long delay, final TimeUnit timeUnit )
        throws InterruptedException, TimeoutException
    {
      checkArgument( delay >= 0 ) ;
      lock.lock() ;
      try {
        final long start = System.currentTimeMillis() ;
        long remaining = delay ;
        while( counter > 0 && remaining > 0 ) {
          condition.await( remaining, timeUnit ) ;
          remaining -= ( System.currentTimeMillis() - start ) ;
        }
        if( counter > 0 ) {
          throw new TimeoutException(
              "Counter is " + counter + " after " + delay + " " + timeUnit ) ;
        }
      } finally {
        lock.unlock() ;
      }
    }



    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + '{' + Integer.toString( counter ) + '}' ;
    }
  }
}
