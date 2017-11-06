package com.otcdlink.chiron.toolbox.service;

import com.google.common.base.Strings;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.Uninterruptibles;
import com.otcdlink.chiron.toolbox.ToStringTools;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.otcdlink.chiron.toolbox.service.Service.State.BUSY;

/**
 * Default base implementation for {@link Service}.
 */
public abstract class AbstractService< SETUP, COMPLETION >
    implements Service< SETUP, COMPLETION >
{

  protected final Logger logger ;

  protected final Object lock ;

  protected final String serviceName ;

  private final boolean runSurvivorThread ;

  private State state = State.NEW ;

  public AbstractService( final Logger logger, final String serviceName ) {
    this( logger, false, serviceName ) ;
  }

  public AbstractService(
      final Logger logger,
      final boolean runSurvivorThread,
      final String serviceName
  ) {
    this.logger = checkNotNull( logger ) ;
    this.runSurvivorThread = runSurvivorThread;
    checkArgument( ! Strings.isNullOrEmpty( serviceName ) ) ;
    this.serviceName = serviceName ;
    this.lock = ToStringTools.createLockWithNiceToString( getClass() ) ;
  }

  /**
   * Caller must synchronize on {@link #lock}.
   */
  protected final void state( final State newState ) {
    logger.debug( "State of " + this + " transitioned into " + newState + " from " + state + "." ) ;
    state = newState ;
  }

  /**
   * Caller must synchronize on {@link #lock}.
   */
  protected final State state() {
    return state ;
  }

  /**
   * @param stateFunction some code that should execute quickly.
   */
  protected final < RESULT > RESULT executeSynchronously(
      final Function< State, RESULT > stateFunction
  ) {
    synchronized( lock ) {
      return stateFunction.apply( state ) ;
    }
  }


  @Override
  public final String toString() {
    @SuppressWarnings( { "StringBufferReplaceableByString", "MismatchedQueryAndUpdateOfStringBuilder" } )
    final StringBuilder stringBuilder = new StringBuilder() ;
    return ToStringTools.nameAndCompactHash( this ) + "{" + stringBuilder.toString() + "}" ;
  }

  @SuppressWarnings( "unused" )
  protected void enrichToString( final StringBuilder stringBuilder ) { }



// =====
// Setup
// =====

  protected static class ExecutionContext< SETUP, COMPLETION > {

    protected final SETUP setup ;

    public final boolean firstStart ;

    /**
     * A reference to the last {@code CompletableFuture} passed to
     * {@link #compute(ExecutorService, Predicate, Runnable, CompletableFuture)}
     */
    public CompletableFuture computationFuture = null ;


    protected ExecutionContext( final SETUP setup, boolean firstStart ) {
      this.setup = checkNotNull( setup ) ;
      this.firstStart = firstStart ;
    }

  }

  /**
   * Enriches an {@link ExecutionContext} with stuff that we don't want to expose to subclasses.
   */
  private class ExecutionContextDecorator {
    private final ExecutionContext< SETUP, COMPLETION > executionContext ;

    /**
     * If {@link AbstractService#runSurvivorThread} is {@code true}, this references a
     * thread that remains sleeping until {@link #customEffectiveStopComplete()} interrupts it.
     * This is useful when all other threads are daemon threads.
     */
    private final Thread survivorThread ;

    /**
     * Completes when entering {@link State#STARTED} state.
     */
    private final CompletableFuture< Void > startFuture = new CompletableFuture<>() ;

    /**
     * Completes when entering {@link State#STOPPED} state.
     */
    private final CompletableFuture< COMPLETION > terminationFuture = new CompletableFuture<>() ;

    /**
     * The value to be returned by {@link #terminationFuture()}, but subclasses should not use
     * {@link #terminationFuture()} directly because there is associated lifecycle operations
     * performed in this class.
     */
    private COMPLETION completion ;


    public ExecutionContextDecorator(
        final ExecutionContext< SETUP, COMPLETION > executionContext
    ) {
      this.executionContext = executionContext ;
      if( AbstractService.this.runSurvivorThread ) {
        final Semaphore doneStarting = new Semaphore( 0 ) ;
        survivorThread = threadFactory( "survivor", null, false ).newThread( () -> {
          doneStarting.release() ;
          while( ! Thread.interrupted() ) {
            Uninterruptibles.sleepUninterruptibly( Long.MAX_VALUE, TimeUnit.MILLISECONDS ) ;
          }
        } ) ;
        survivorThread.start() ;
        doneStarting.acquireUninterruptibly( 1 ) ;
      } else {
        survivorThread = null ;
      }
    }
  }

  private SETUP setup = null ;
  private ExecutionContextDecorator executionContextDecorator = null ;

  /**
   * Return current {@link ExecutionContext}.
   * Subclasses will do some transtyping, but it's better than adding a type parameter for that.
   */
  protected final ExecutionContext< SETUP, COMPLETION > bareExecutionContext() {
    synchronized( lock ) {
      return executionContextDecorator.executionContext ;
    }
  }

  @Override
  public final void setup( final SETUP setup ) {
    synchronized( lock ) {
      checkState( state == State.NEW || state == State.STOPPED ) ;
      this.setup = doSetup( checkNotNull( setup ) ) ;
      state( State.STOPPED ) ;
    }
  }

  /**
   * Hook to transform given {@link SETUP}.
   */
  protected SETUP doSetup( SETUP setup ) {
    return setup ;
  }

  public final SETUP setup() {
    synchronized( lock ){
      checkState( setup != null ) ;
      return setup ;
    }
  }

  @Override
  public final CompletableFuture< Void > startFuture() {
    synchronized( lock ) {
      checkState( executionContextDecorator != null, "Not started" ) ;
      return executionContextDecorator.startFuture ;
    }
  }

  @Override
  public final CompletableFuture< COMPLETION > terminationFuture() {
    synchronized( lock ) {
      checkState( executionContextDecorator != null, "Not started" ) ;
      return executionContextDecorator.terminationFuture ;
    }
  }


// =====
// Start
// =====

  @Override
  public final CompletableFuture< Void > start() {
    final ExecutionContextDecorator newDecorator ;
    synchronized( lock ){
      checkState( state == State.STOPPED ) ;
      state( State.INITIALIZING ) ;
      final boolean firstStart = executionContextDecorator == null ;
      newDecorator = new ExecutionContextDecorator( newExecutionContext( setup, firstStart ) ) ;
      checkState( newDecorator.executionContext.firstStart == firstStart ) ;
      executionContextDecorator = newDecorator ;
    }
    threadFactory( "start", null, false ).newThread( () -> {
      try {
        synchronized( lock ) {
          customInitialize() ;
          state( State.STARTING ) ;
        }
        customStart() ;
      } catch( Exception e ) {
        logger.error( "Custom initialization/start failed", e ) ;
        newDecorator.startFuture.completeExceptionally( e ) ;
      }
    } ).start() ;
    return newDecorator.startFuture ;
  }

  protected ExecutionContext< SETUP, COMPLETION > newExecutionContext(
      final SETUP setup,
      final boolean firstStart
  ) {
    return new ExecutionContext<>( setup, firstStart ) ;
  }

  /**
   * Called from any thread in a synchronized block, while state is {@link State#INITIALIZING}.
   */
  protected void customInitialize() throws Exception { }

  /**
   * Subclasses can hook on {@link #start()} by overriding this method.
   * Call to this method is not synchronized and calling thread may be any thread,
   * but {@link #state} is guaranteed to be {@link State#STARTING}.
   * Subclasses must eventually call {@link #customStartComplete()}, they can do this
   * from the thread calling this method, or from another thread created on their own.
   */
  protected void customStart() throws Exception {
    customStartComplete() ;
  }


  protected final void customStartComplete() {
    synchronized( lock ) {
      if( state == State.STARTING ) {
        state( State.STARTED ) ;
        executionContextDecorator.startFuture.complete( null ) ;
      } else {
        executionContextDecorator.startFuture.completeExceptionally( new IllegalStateException( "" ) ) ;
      }
    }
  }

  @Override
  public final CompletableFuture< COMPLETION > run() throws Exception {
    final CompletableFuture< COMPLETION > terminationFuture ;
    synchronized( lock ){
      start() ;
      terminationFuture = executionContextDecorator.terminationFuture ;
    }
    return terminationFuture ;
  }


// ====
// Stop
// ====

  protected final void completion( final COMPLETION completion ) {
    synchronized( lock ) {
      executionContextDecorator.completion = completion ;
    }
  }

  @Override
  public final CompletableFuture< COMPLETION > stop() {
    final CompletableFuture< COMPLETION > stopFuture ;
    final boolean doStop ;
    logger.info( "Requested to stop " + this + " ..." ) ;
    synchronized( lock ) {
      stopFuture = executionContextDecorator == null ? null : executionContextDecorator.terminationFuture ;
      if( state == State.STARTED || state == BUSY ) {
        state( State.STOPPING ) ;
        doStop = true ;
      } else if( state == State.STOPPING || state == State.STOPPED ) {
        logger.debug( "Do nothing when asked to stop, already " + state + "." ) ;
        doStop = false ;
      } else {
        throw new IllegalStateException(
            "Current state is " + state + ", can't stop " + this + "." ) ;
      }
      if( executionContextDecorator.executionContext.computationFuture != null ) {
        executionContextDecorator.executionContext.computationFuture.cancel( true ) ;
        logger.info( "Canceled running computation " +
            executionContextDecorator.executionContext.computationFuture + "." ) ;
      }
    }
    if( doStop ) {
      try {
        customExplicitStop() ;
      } catch( final Exception e ) {
        stopFuture.completeExceptionally( e ) ;
      }
    }
    return stopFuture ;
  }

  /**
   * Hook for subclasses to perform custom operations when {@link #stop()} is explicitely called.
   * This does <em>not</em> cover the case of a natural process end.
   */
  protected void customExplicitStop() throws Exception {
    customEffectiveStop() ;
  }

  /**
   * Hook for subclasses to perform custom operations before becoming {@link State#STOPPED}.
   */
  protected void customEffectiveStop() throws Exception {
    customEffectiveStopComplete() ;
  }

  protected final void customEffectiveStopComplete() {
    final CompletableFuture< COMPLETION > terminationFuture ;
    final COMPLETION completion ;
    final State currentState ;
    final Thread survivorThread ;
    synchronized( lock ) {
      currentState = state ;
      if( currentState == State.STOPPING ) {
        terminationFuture = executionContextDecorator.terminationFuture ;
        completion = executionContextDecorator.completion ;
        survivorThread = executionContextDecorator.survivorThread ;
        state( State.STOPPED ) ;
        /** Don't set {@link #executionContextDecorator} to null, some code may still call
         * {@link #startFuture()} or {@link #terminationFuture()}, it may happen if the remote
         * process ended very quickly.*/
      } else {
        terminationFuture = null ;
        completion = null ;
        survivorThread = null ;
        logger.error( "Can't stop while in " + state + ".",
            new Exception( "(Just for stack trace)" ) ) ;
      }
    }
    if( terminationFuture != null ) {
      logger.info(
          "Execution complete with resulting value of " + completion + " for " + this + "."
      ) ;
      terminationFuture.complete( completion ) ;
    }

    if( survivorThread != null ) {
      survivorThread.interrupt() ;
    }
  }


// =========
// Threading
// =========

  private static final AtomicInteger threadCounter = new AtomicInteger() ;

  private static final AtomicInteger GLOBAL_INSTANCE_COUNTER = new AtomicInteger( 0 ) ;

  /**
   * Using {@link MapMaker#weakKeys} causes the key to be evaluated using {@code ==} which is
   * exactly what we need.
   */
  private static final Map<AbstractService, Integer > INSTANCE_INDEX =
      new MapMaker().weakKeys().makeMap() ;

  protected final ThreadFactory threadFactory(
      final String role,
      final String purpose
  ) {
    return threadFactory( role, purpose, true ) ;
  }

  /**
   * Creates a dedicated {@code ThreadFactory} with nice counters.
   * Created {@code Thread} will have a name looking like this:
   *
   * <pre>
   * myservice-1-myrole-mypurpose-2
   * ^         ^                  ^
   * |         |                  |
   * |         |                  3rd {@code Thread} created.
   * |         2nd {@code ThreadFactory} created with this instance of {@link AbstractService}.
   * {@link #serviceName}
   * </pre>
   *
   * @param role optional.
   * @param purpose optional.
   */
  protected final ThreadFactory threadFactory(
      final String role,
      final String purpose,
      final boolean daemon
  ) {
    return runnable -> {
      final StringBuilder stringBuilder = new StringBuilder() ;
      stringBuilder.append( serviceNameWithCounterForThreadName() ) ;
      stringBuilder.append( '-' ) ;
      if( role != null ) {
        stringBuilder.append( role ) ;
        stringBuilder.append( '-' ) ;
      }
      if( purpose != null ) {
        stringBuilder.append( purpose ) ;
        stringBuilder.append( '-' ) ;
      }
      stringBuilder.append( threadCounter.getAndIncrement() ) ;
      final Thread thread = new Thread( runnable, stringBuilder.toString() ) ;
      thread.setDaemon( daemon ) ;

      // ThreadPoolExecutor ignores this because there is already a Throwable handler
      // in the Future object it returns.
      thread.setUncaughtExceptionHandler( ( emittingThread, throwable ) ->
          logger.error( "Caught in " + emittingThread + ": ", throwable ) ) ;

      return thread ;
    } ;
  }

  protected String serviceNameWithCounterForThreadName() {
    final Integer instanceCounter = INSTANCE_INDEX.computeIfAbsent(
        AbstractService.this, Ø -> GLOBAL_INSTANCE_COUNTER.getAndIncrement() ) ;
    return serviceName + "-" + instanceCounter ;
  }


  /**
   * Convenience method for executing a single computation that sets {@link #state}
   * to {@link State#BUSY}.
   * This method has the drawback to create a {@code Thread} for each execution.
   * For more intensive usage, subclasses should reuse their own {@code ExecutorService}
   * and call {@link #compute(ExecutorService, Callable, Predicate)}.
   */
  protected final < RESULT > CompletableFuture< RESULT > executeSingleComputation(
      final ThreadFactory threadFactory,
      final Callable< RESULT > callable
  ) {
    final ExecutorService runExecutorService = Executors.newSingleThreadExecutor( threadFactory ) ;
    return compute(
        runExecutorService,
        callable,
        state -> state == State.STARTED
    ) ;
  }

  /**
   * Checks current {@link #state}, switches it to {@link State#BUSY}, runs a {@link Runnable}
   * in given {@code ExecutorService}, and waits for given {@code CompletableFuture} to complete
   * (normally or exceptionally, including with cancellation), then goes back to the previous
   * {@link #state}.
   * <p>
   * The {@code Runnable} is here to run some custom code in the right thread, but only after
   * the {@link #state} has been checked.
   * <p>
   * The {@code Callable} should check {@code Thread#isInterrupted()} to complete prematurely
   * if the returned {@code CompletableFuture} was cancelled; in this case it should return
   * {@code null}).
   * <p>
   * Because of the state lock, the {@link #stop()} method will fail until the {@code Callable}
   * completes.
   *
   * @param executorService executes given {@code Runnable}, which may trigger other threads
   *     that do things required to complete given {@code CompletableFuture}.
   *
   * @param resultFuture a {@code CompletableFuture} that supports cancellation.
   *     It may complete exceptionally if an error occurs inside the {@code Callable},
   *     or complete with a {@code null} in case of cancellation.
   *     {@code null} may also be the value that given {@code Callable} is expected to return.
   */
  protected final < RESULT > void compute(
      final ExecutorService executorService,
      final Predicate< State > validInitialStatePredicate,
      final Runnable primer,
      final CompletableFuture< RESULT > resultFuture
  ) {
    compute( executorService, validInitialStatePredicate, BUSY, primer, resultFuture ) ;
  }

  /**
   * A variant of {@link #compute(ExecutorService, Predicate, Runnable, CompletableFuture)}
   * with a custom {@link State}.
   */
  protected final < RESULT > void compute(
      final ExecutorService executorService,
      final Predicate< State > validInitialStatePredicate,
      final State newState,
      final Runnable primer,
      final CompletableFuture< RESULT > resultFuture
  ) {
    checkNotNull( resultFuture ) ;
    checkArgument( newState == BUSY || ! State.MAP.values().contains( newState ) ) ;
    final State preExecutionState ;
    synchronized( lock ) {
      preExecutionState = state ;
      checkState( validInitialStatePredicate.test( preExecutionState ),
          "Not one of the expected state: " + preExecutionState ) ;
      state( newState ) ;
      executionContextDecorator.executionContext.computationFuture = resultFuture ;
    }

    // Can't reference the Future before its creation so we use a BlockingQueue for safety.
    final BlockingQueue< Future< ? > > futureQueue = new ArrayBlockingQueue<>( 1 ) ;

    resultFuture.whenComplete( ( result, throwable ) -> {
      try {
        if( throwable != null ) {
          // Causing thread interruption.
          futureQueue.take().cancel( true ) ;
        }
      } catch( InterruptedException ignore ) {
      } finally {
        synchronized( lock ) {
          checkState( state() == newState ) ;
          state( preExecutionState ) ;
          executionContextDecorator.executionContext.computationFuture = null ;
        }
      }
    } ) ;

    final Future< ? > executorFuture = executorService.submit( primer ) ;

    futureQueue.offer( executorFuture ) ;
  }


  /**
   * Runs a {@code Callable} with guarantees about {@link #state} and cancellation.
   *
   * @see #compute(ExecutorService, Predicate, Runnable, CompletableFuture)
   */
  protected final < RESULT > CompletableFuture< RESULT > compute(
      final ExecutorService executorService,
      final Callable< RESULT > callable,
      final Predicate< State > validInitialStatePredicate
  ) {
    final CompletableFuture< RESULT > completableFuture = new CompletableFuture<>() ;
    compute(
        executorService,
        validInitialStatePredicate,
        () -> {
          try {
            completableFuture.complete( callable.call() ) ;
          } catch( final Throwable t ) {
            completableFuture.completeExceptionally( t ) ;
          }
        },
        completableFuture
    ) ;
    return completableFuture ;
  }

  public static final Predicate< State > IS_STARTED = s -> s == State.STARTED ;

}
