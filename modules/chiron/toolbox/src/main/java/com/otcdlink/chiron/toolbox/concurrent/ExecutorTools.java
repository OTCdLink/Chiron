package com.otcdlink.chiron.toolbox.concurrent;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ForwardingExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class ExecutorTools {

  private ExecutorTools() { }

  private static final Logger LOGGER = LoggerFactory.getLogger( ExecutorTools.class ) ;

  public interface ExecutorServiceFactory {
    ExecutorService create() ;
  }

  public interface ScheduledExecutorServiceFactory extends ExecutorServiceFactory {
    @Override
    ScheduledExecutorService create() ;
  }


  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger() ;

  /**
   * Uses a singleton counter to give unique names.
   */
  public static ThreadFactory newThreadFactory( final String threadPoolName ) {
    return new ThreadFactory() {
      @Override
      public Thread newThread( final Runnable runnable ) {
        final Thread thread = new Thread(
            runnable,
            threadPoolName + '-' + THREAD_COUNTER.getAndIncrement()
        ) ;
        thread.setDaemon( true ) ;
        thread.setUncaughtExceptionHandler( ( t, e ) -> LOGGER.error( "Problem:", e ) ) ;
        return thread;
      }

      @Override
      public String toString() {
        return ThreadFactory.class.getSimpleName() + "{" + threadPoolName + '}' ;
      }
    } ;
  }

  public static class ThemingThreadFactory {
    private static final AtomicInteger THEME_COUNTER = new AtomicInteger() ;
    private final String themePrefix ;

    public ThemingThreadFactory( final String theme ) {
      this( theme, null ) ;
    }

    public ThemingThreadFactory( final String theme, final String subTheme ) {
      checkArgument( ! Strings.isNullOrEmpty( theme ) ) ;
      final StringBuilder stringBuilder = new StringBuilder() ;
      stringBuilder.append( theme ) ;
      stringBuilder.append( "-" ) ;
      stringBuilder.append( THEME_COUNTER.incrementAndGet() ) ;
      stringBuilder.append( "-" ) ;
      if( subTheme != null ) {
        stringBuilder.append( subTheme ) ;
        stringBuilder.append( "-" ) ;
      }
      this.themePrefix = stringBuilder.toString() ;
    }

    private final AtomicInteger roleCounter = new AtomicInteger() ;

    public ThreadFactory threadFactory( final String role ) {
      return runnable -> {
        final Thread thread = new Thread( runnable ) ;
        thread.setName( themePrefix + "-" + role + "-" + roleCounter.getAndIncrement() ) ;
        thread.setDaemon( true ) ;
        return thread ;
      } ;
    }
  }

  public static ExecutorServiceFactory singleThreadedExecutorServiceFactory(
      final String threadPoolName
  ) {
    return () -> Executors.newSingleThreadExecutor( newThreadFactory( threadPoolName ) ) ;
  }

  public static ScheduledExecutorServiceFactory singleThreadedScheduledExecutorServiceFactory(
      final String threadPoolName
  ) {
    return () -> Executors.newScheduledThreadPool(
        1,
        new ThreadFactory() {
          @Override
          public Thread newThread( final Runnable runnable ) {
            final Thread thread = new Thread(
                runnable,
                threadPoolName + "-" + THREAD_COUNTER.getAndIncrement()
            ) ;
            thread.setDaemon( true ) ;
            return thread ;
          }

          @Override
          public String toString() {
            return ScheduledExecutorServiceFactory.class.getSimpleName() +
                ".SingleThreaded@" + System.identityHashCode( this ) +
                '{' + threadPoolName + '}'
            ;
          }
        }
    ) ;
  }


  /**
   * Fills some blank left by
   * {@link com.google.common.util.concurrent.ForwardingExecutorService}.
   */
  public static abstract class ForwardingScheduledExecutorService
      extends ForwardingExecutorService
      implements ScheduledExecutorService
  {

    @Override
    protected abstract ScheduledExecutorService delegate() ;

    @Override
    public ScheduledFuture< ? > schedule(
        final Runnable command,
        final long delay,
        final TimeUnit unit
    ) {
      return delegate().schedule( command, delay, unit ) ;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(
        final Callable< V > callable,
        final long delay,
        final TimeUnit unit
    ) {
      return delegate().schedule( callable, delay, unit ) ;
    }

    @Override
    public ScheduledFuture< ? > scheduleAtFixedRate(
        final Runnable command,
        final long initialDelay,
        final long period,
        final TimeUnit unit
    ) {
      return delegate().scheduleAtFixedRate( command, initialDelay, period, unit ) ;
    }

    @Override
    public ScheduledFuture< ? > scheduleWithFixedDelay(
        final Runnable command,
        final long initialDelay,
        final long delay,
        final TimeUnit unit
    ) {
      return delegate().scheduleWithFixedDelay( command, initialDelay, delay, unit ) ;
    }
  }


  public abstract static class ExecutorLifecycle<
      EXECUTOR_FACTORY extends ExecutorServiceFactory,
      EXECUTOR extends ExecutorService
  > {

    private final EXECUTOR_FACTORY executorServiceFactory ;

    /**
     * Need one global lock for running {@link #start()},
     * {@link #stop(boolean, long, TimeUnit)}, and other stuff.
     */
    protected final Lock lock = new ReentrantLock() ;

    private volatile EXECUTOR executorService = null ;

    private final AtomicInteger runningTaskCount = new AtomicInteger( 0 ) ;

    protected ExecutorLifecycle(
        final EXECUTOR_FACTORY executorServiceFactory
    ) {
      this.executorServiceFactory = checkNotNull( executorServiceFactory ) ;
    }

    /**
     * Don't keep a reference on returned value, so at each call we check non-nullity.
     */
    protected final EXECUTOR executorService() {
      final EXECUTOR current = executorService ;
      if( current == null ) {
        throw new IllegalStateException( "No executorService" ) ;
      }
      return current ;
    }

    @SuppressWarnings( "unchecked" )
  //  @Override
    public void start() throws Exception {
      lock.lock() ;
      try {
        checkState( executorService == null ) ;
        final EXECUTOR delegate = ( EXECUTOR ) executorServiceFactory.create() ;
        final Function< Runnable, Runnable > runnableWrapper = runnable -> () -> {
          executionStarted() ;
          try {
            runnable.run() ;
          } finally {
            executionTerminated() ;
          }
        } ;

        if( executorServiceFactory instanceof ScheduledExecutorServiceFactory ) {
          executorService = ( EXECUTOR ) new ForwardingScheduledExecutorService() {
            @Override
            protected ScheduledExecutorService delegate() {
              return ( ScheduledExecutorService ) delegate ;
            }

            @Override
            public void execute( final Runnable command ) {
              delegate.execute( runnableWrapper.apply( command ) ) ;
            }
          } ;
        } else {
          executorService = ( EXECUTOR ) new ForwardingExecutorService() {
            @Override
            protected ExecutorService delegate() {
              return delegate;
            }

            @Override
            public void execute( final Runnable command ) {
              delegate.execute( runnableWrapper.apply( command ) ) ;
            }
          } ;
        }
        safeAfterStart() ;
      } finally {
        lock.unlock() ;
      }
    }

    protected void safeAfterStart() throws Exception { }

    private void executionStarted() {
      runningTaskCount.incrementAndGet() ;
    }

    private volatile boolean stopping = false ;

    private final Condition stopCondition = lock.newCondition() ;


    private void executionTerminated() {
      if( runningTaskCount.decrementAndGet() == 0 && stopping ) {
        lock.lock() ;
        try {
          stopCondition.signal() ;
        } finally {
          lock.unlock() ;
        }
      }
    }

  //  @Override
    public void stop(
        final boolean force,
        final long timeout,
        final TimeUnit timeUnit
    ) throws Exception {
      stopping = true ;
      lock.lock() ;
      try {
        checkState( executorService != null ) ;
        if( force ) {
          executorService.shutdownNow() ;
          /**
           * With a forced stop, Executor's tasks (which are {@code Stratum0.Synchronous})
           * will wait for {@link #lock} after being cancelled, as they are calling
           * {@code Stratum0.ConsumptionSink#recordFailure(Stratum0, Propagation, Exception)}
           * with an {@code InterruptedException}. Since current block already owns the
           * {@link #lock} there would be a deadlock if not using the {@link #stopCondition}.
           */
          while( ! executorService.isTerminated() ) {
            stopCondition.await() ;
          }
        } else {
          executorService.shutdown() ;
          executorService.awaitTermination( timeout, timeUnit ) ;
        }
        executorService = null ;
        safeAfterStop() ;
      } finally {
        stopping = false ;
        lock.unlock() ;
      }
    }

    protected void safeAfterStop() throws Exception { }

  }

  public static ThreadFactory newCountingDaemonThreadFactory( final Class someClass ) {
    return newCountingDaemonThreadFactory( someClass.getSimpleName() ) ;
  }

  public static ThreadFactory newCountingDaemonThreadFactory( final String radix ) {
    final AtomicInteger threadCounter = new AtomicInteger() ;
    return runnable -> {
      final Thread thread = new Thread( runnable ) ;
      thread.setName( radix + "-" + threadCounter.getAndIncrement() ) ;
      thread.setDaemon( true ) ;
      return thread ;
    } ;

  }
}
