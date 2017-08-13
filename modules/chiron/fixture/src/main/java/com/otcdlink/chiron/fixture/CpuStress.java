package com.otcdlink.chiron.fixture;

import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import com.otcdlink.chiron.toolbox.number.Fraction;
import com.otcdlink.chiron.toolbox.number.PositiveIntegerRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.otcdlink.chiron.toolbox.number.Fraction.newFraction;

/**
 * Generates random CPU load.
 */
public class CpuStress {

  private static final Logger LOGGER = LoggerFactory.getLogger( CpuStress.class ) ;

  private final int numberOfThreads ;
  private final ThreadFactory threadFactory ;
  private final Random random ;
  private final PositiveIntegerRange runRangeNs ;
  private final PositiveIntegerRange sleepRangeNs ;
  private final Fraction threadYielding ;
  private final long maximumDuration ;
  private final TimeUnit timeUnit ;
  private final Object lock = ToStringTools.createLockWithNiceToString( CpuStress.class ) ;
  private CompletableFuture< ? > runConcluder ;


  public interface Defaults {

    static Fraction defaultThreadYielding() {
      return newFraction( 0f ) ;
    }

    static PositiveIntegerRange defaultSleepRangeNs() {
      return PositiveIntegerRange.newRange( 10_000_000, 50_000_000 ) ;
    }

    static PositiveIntegerRange defaultRunRangeNs() {
      return PositiveIntegerRange.newRange( 10_000_000, 50_000_000 ) ;
    }

    static Random defaultRandom() {
      return new Random( 0 ) ;
    }

    static ThreadFactory defaultThreadFactory() {
      return ExecutorTools.newThreadFactory( CpuStress.class.getSimpleName() + '-' ) ;
    }

    static int defaultNumberOfThreads() {
      return Runtime.getRuntime().availableProcessors() - 1 ;
    }
  }

  public CpuStress( final int numberOfThreads, final long duration, final TimeUnit timeUnit ) {
    this(
        numberOfThreads,
        Defaults.defaultThreadFactory(),
        Defaults.defaultRandom(),
        Defaults.defaultRunRangeNs(),
        Defaults.defaultSleepRangeNs(),
        Defaults.defaultThreadYielding(),
        duration,
        timeUnit
    ) ;
  }

  public CpuStress() {
    this(
        Defaults.defaultNumberOfThreads(),
        Defaults.defaultThreadFactory(),
        Defaults.defaultRandom(),
        Defaults.defaultRunRangeNs(),
        Defaults.defaultSleepRangeNs(),
        Defaults.defaultThreadYielding(),
        1,
        TimeUnit.MINUTES
    ) ;
  }
  public CpuStress(
      final int numberOfThreads,
      final ThreadFactory threadFactory,
      final Random random,
      final PositiveIntegerRange runRangeNs,
      final PositiveIntegerRange sleepRangeNs,
      final Fraction threadYielding,
      final long maximumDuration,
      final TimeUnit timeUnit
  ) {
    checkArgument( numberOfThreads > 0 ) ;
    this.numberOfThreads = numberOfThreads ;
    this.threadFactory = checkNotNull( threadFactory ) ;
    this.random = checkNotNull( random ) ;
    this.runRangeNs = checkNotNull( runRangeNs ) ;
    this.sleepRangeNs = checkNotNull( sleepRangeNs ) ;
    this.threads = new Thread[ numberOfThreads ] ;
    this.threadYielding = checkNotNull( threadYielding ) ;
    checkArgument( maximumDuration >= 0 ) ;
    this.maximumDuration = maximumDuration ;
    this.timeUnit = timeUnit ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' +
        "numberOfThreads=" + numberOfThreads + ';' +
        "runRangeNs=" + runRangeNs.boundsAsCompactString() + ';' +
        "sleepRangeNs=" + sleepRangeNs.boundsAsCompactString() + ';' +
        "threadYielding=" + threadYielding.asString() +
        '}'
    ;
  }

  private volatile boolean running = false ;
  private final Thread[] threads ;
  private ScheduledExecutorService scheduledExecutorService = null ;
  private ScheduledFuture< ? > cancellationFuture = null ;

  public CompletableFuture< ? > run() {
    synchronized( lock ) {
      checkState( ! running, "Already started" ) ;
      runConcluder = new CompletableFuture<>() ;
      for( int i = 0 ; i < numberOfThreads ; i ++ ) {
        threads[ i ] = threadFactory.newThread( this::loop ) ;
        threads[ i ].setDaemon( true ) ;
      }
      running = true ;
      for( int i = 0 ; i < numberOfThreads ; i ++ ) {
        threads[ i ].start() ;
      }
      scheduledExecutorService = Executors.newSingleThreadScheduledExecutor( runnable -> {
        final Thread thread = threadFactory.newThread( runnable ) ;
        thread.setName( "terminator-" + thread.getName() ) ;
        return thread ;
      } ) ;
      cancellationFuture = scheduledExecutorService.schedule(
          () -> {
            LOGGER.info( "Terminating " + this + " after a delay of " +
                maximumDuration + " " + timeUnit + " ..." ) ;
            terminate( false ) ;
          },
          maximumDuration,
          timeUnit
      ) ;
    }
    LOGGER.info( "Started " + this + "." ) ;
    return runConcluder;
  }

  @SuppressWarnings( { "StatementWithEmptyBody", "BusyWait" } )
  private void loop() {
    while( running ) {
      repeatUntilTimeElapsed(
          runRangeNs.random( this.random ),
          () -> { /* Waste CPU.*/ },
          BUSY_ITERATION_COUNT
      ) ;
      repeatUntilTimeElapsed(
          sleepRangeNs.random( this.random ),
          () -> {
            if( threadYielding.asBoolean( random ) ) {
              // Consumes Kernel CPU time.
              Thread.yield() ;
            } else {
              sleep( sleepRangeNs, random ) ;
            }
          },
          1
      ) ;
    }
  }

  private static void repeatUntilTimeElapsed(
      final int durationNs,
      final Runnable runnable,
      final int busyIterationCount
  ) {
    while( true ) {
      final long startTime = System.nanoTime() ;
      final long stopTime = startTime + durationNs ;
      for( int j = 0 ; j < busyIterationCount ; j++ ) {
        runnable.run() ;
      }
      if( System.nanoTime() > stopTime ) {
        break ;
      }
    }
  }

  private static void sleep( final PositiveIntegerRange sleepRangeNs, final Random random ) {
    try {
      final int sleepDurationNs = sleepRangeNs.random( random ) ;
      Thread.sleep( sleepDurationNs / 1_000_000, sleepDurationNs % 999999 ) ;
    } catch( final InterruptedException ignore ) { }
  }

  public boolean terminate() {
    return terminate( true ) ;
  }

  private boolean terminate( final boolean cancelScheduledTermination ) {
    synchronized( lock ) {
      LOGGER.trace( "Terminating " + this + " ..." ) ;
      if( cancelScheduledTermination && cancellationFuture != null ) {
        cancellationFuture.cancel( true ) ;
        cancellationFuture = null ;
      }
      if( scheduledExecutorService != null ) {
        scheduledExecutorService.shutdownNow() ;
        scheduledExecutorService = null ;
      }
      if( running ) {
        running = false ;
        for( final Thread thread : threads ) {
          thread.interrupt() ;
        }
        LOGGER.info( "Terminated " + this + "." ) ;
        runConcluder.complete( null ) ;
        return true ;
      } else {
        LOGGER.warn( this + " was already terminated." ) ;
        return false ;
      }
    }
  }

  public static void main( final String... arguments ) {
    new CpuStress().run().join() ;
  }

  private static final int BUSY_ITERATION_COUNT = 100_000 ;
  private static final long BUSY_LOOP_DURATION_NS ;
  static {
    BUSY_LOOP_DURATION_NS = approximateLoopDurationNs( BUSY_ITERATION_COUNT ) ;
    LOGGER.debug( "Calculated approximate loop duration: iterating " + BUSY_ITERATION_COUNT +
        " times took " + BUSY_LOOP_DURATION_NS + " ns." ) ;
  }

  @SuppressWarnings( "StatementWithEmptyBody" )
  private static long approximateLoopDurationNs( final int iterationCount ) {
    final long start = System.nanoTime() ;
    for( int i = 0 ; i < iterationCount ; i ++ ) { }
    final long loopDurationNs = System.nanoTime() - start ;
    return loopDurationNs ;
  }

}
