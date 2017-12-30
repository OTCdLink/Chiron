package com.otcdlink.chiron.toolbox.concurrent;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import io.netty.channel.EventLoopGroup;
import io.netty.util.internal.PlatformDependent;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Executes tasks in some given {@code Executor}, but guarantees that all those launched with
 * the same {@link KEY} execute sequentially. This is equivalent to one single-threaded
 * {@code ScheduledServiceExecutor} per {@link KEY}'s hash value.
 * <p>
 * This class notably differs from an {@code ExecutorService} in the manner it can't be shut down,
 * because it delegates task execution to another {@code ExecutorService}. Of course all methods
 * mimicking {@code ExecutorService}'s take a {@link KEY} parameter.
 * <p>
 * When running a task, thread's name may be suffixed with a custom value derived from
 * the {@link KEY} by the (optional) {@link #keyStringifier}.
 *
 * TODO add more methods mimicking {@code ScheduledExecutorService}'s.
 *
 * @see reactor.core.scheduler.Scheduler#createWorker() which has probably safer and
 *     faster implementation.
 */
public final class ExecutorPartitioner< KEY > {

  private final ScheduledExecutorService scheduledExecutorService ;
  private final PartitionedExecutor[] partitionedExecutors ;
  private final Function< KEY, Integer > keyHasher ;
  private final Function< KEY, String > keyStringifier ;

  /**
   * Default is to use Netty's super-optimized non-blocking queue.
   */
  public static final Supplier< Queue< Runnable > > DEFAULT_QUEUE_SUPPLIER =
      PlatformDependent::newMpscQueue ;

  public static < KEY > Function< KEY, Integer > defaultKeyHasher() {
    return Object::hashCode ;
  }
  public static < KEY > Function< KEY, String > defaultKeyStringifier() {
    return Object::toString ;
  }


  public ExecutorPartitioner(
      final int partitionCount,
      final ScheduledExecutorService scheduledExecutorService,
      final Supplier< Queue< Runnable > > queueSupplier,
      final Function< KEY, Integer > keyHasher,
      final Function< KEY, String > keyStringifier
  ) {
    checkArgument( partitionCount > 0 ) ;
    this.scheduledExecutorService = checkNotNull( scheduledExecutorService ) ;
    this.partitionedExecutors = new PartitionedExecutor[ partitionCount ] ;
    for( int i = 0 ; i < partitionCount ; i ++ ) {
      partitionedExecutors[ i ] = new PartitionedExecutor(
          scheduledExecutorService, queueSupplier.get() ) ;
    }
    this.keyHasher = checkNotNull( keyHasher ) ;
    this.keyStringifier = keyStringifier ;
  }


// ========
// Contract
// ========

  public void execute( final KEY key, final Runnable runnable ) {
    final PartitionedExecutor partitionedExecutor = resolve( key ) ;
    partitionedExecutor.execute( applyKeyStringifier( key ), runnable ) ;
  }

  public < V > Future< V > submit( final KEY key, final Callable< V > callable ) {
    final PartitionedExecutor partitionedExecutor = resolve( key ) ;
    final CompletableFuture< V > completableFuture = new CompletableFuture<>() ;
    partitionedExecutor.execute(
        applyKeyStringifier( key ),
        wrapIntoRunnable( completableFuture, callable )
    ) ;
    return completableFuture ;
  }

  public ScheduledFuture< ? > schedule(
      final KEY key,
      final Runnable runnable,
      final long delay,
      final TimeUnit timeUnit
  ) {
    final PartitionedExecutor partitionedExecutor = resolve( key ) ;
    return scheduledExecutorService.schedule(
        () -> partitionedExecutor.execute( applyKeyStringifier( key ), runnable ),
        delay,
        timeUnit
    ) ;
  }

  public ScheduledFuture< ? > scheduleAtFixedRate(
      final KEY key,
      final Runnable runnable,
      final long initialDelay,
      final long period,
      final TimeUnit timeUnit
  ) {
    final PartitionedExecutor partitionedExecutor = resolve( key ) ;
    return scheduledExecutorService.scheduleAtFixedRate(
        () -> partitionedExecutor.execute( applyKeyStringifier( key ), runnable ),
        initialDelay,
        period,
        timeUnit
    ) ;
  }

// =========
// Internals
// =========

  private static class PartitionedExecutor {

    private final Executor delegate ;
    private final Queue< Runnable > queue ;
    private final AtomicInteger counter = new AtomicInteger() ;

    private PartitionedExecutor(
        final Executor delegate,
        final Queue< Runnable > queue
    ) {
      this.delegate = checkNotNull( delegate ) ;
      this.queue = checkNotNull( queue ) ;
    }

    public void execute( final String threadNameSuffix, final Runnable command ) {
      if( counter.getAndIncrement() > 0 ) {
        // There is already a Runnable ready to consume from the queue.
        queue.add( command ) ;
      } else {
        delegate.execute( () -> {
          final String originalThreadName ;
          if( Strings.isNullOrEmpty( threadNameSuffix ) ) {
            originalThreadName = null ;
          } else {
            originalThreadName = Thread.currentThread().getName() ;
            Thread.currentThread().setName( originalThreadName + "/" + threadNameSuffix ) ;
          }
          try {
            command.run() ;
            while( counter.decrementAndGet() > 0 ) {
              final Runnable nextRunnable = queue.remove() ;
              nextRunnable.run() ;
            }
          } catch( final Throwable throwable ) {
            if( delegate instanceof EventLoopGroup ) {
              // Netty ignores the UncaughtExceptionHandler.
              Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(
                  Thread.currentThread(), throwable ) ;
            } else {
              Throwables.throwIfUnchecked( throwable ) ;
              throw new Error( "Should not happen", throwable ) ;
            }
          } finally {
            if( originalThreadName != null ) {
              Thread.currentThread().setName( originalThreadName ) ;
            }
          }
        } ) ;
      }
    }

  }

  private static < V > Runnable wrapIntoRunnable(
      final CompletableFuture< V > completableFuture,
      final Callable< V > callable
  ) {
    return () -> {
      try {
        completableFuture.complete( callable.call() ) ;
      } catch( Exception e ) {
        completableFuture.completeExceptionally( e ) ;
      }
    } ;
  }

  private PartitionedExecutor resolve( KEY key ) {
    final int keyHash = keyHasher.apply( key ) ;  // Implicit nullity check.
    return partitionedExecutors[ keyHash % partitionedExecutors.length ] ;
  }

  private String applyKeyStringifier( KEY key ) {
    return keyStringifier == null ? null : keyStringifier.apply( key ) ;
  }


}
