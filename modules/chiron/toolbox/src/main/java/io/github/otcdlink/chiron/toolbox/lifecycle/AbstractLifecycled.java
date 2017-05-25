package io.github.otcdlink.chiron.toolbox.lifecycle;

import com.google.common.base.Strings;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.lifecycle.Lifecycled;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Default base implementation for {@link Lifecycled}.
 */
public abstract class AbstractLifecycled< SETUP, COMPLETION >
    implements Lifecycled< SETUP, COMPLETION >
{

  protected final Logger logger ;

  protected final Object lock ;

  private State state = State.NEW ;

  public AbstractLifecycled( final Logger logger ) {
    this.logger = checkNotNull( logger ) ;
    this.lock = ToStringTools.createLockWithNiceToString( getClass() );
  }

  private void state( final State newState ) {
    logger.debug( "State of " + this + " transitioned into " + newState + " from " + state + "." ) ;
    state = newState ;
  }


  @Override
  public String toString() {
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

    final SETUP setup ;

    /**
     * Completes when entering {@link State#STARTED} state.
     */
    private final CompletableFuture< ? > startFuture = new CompletableFuture<>() ;

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

    protected ExecutionContext( final SETUP setup ) {
      this.setup = checkNotNull( setup ) ;
    }
  }

  private SETUP setup = null ;
  private ExecutionContext< SETUP, COMPLETION > executionContext = null ;

  @Override
  public final void setup( final SETUP setup ) {
    synchronized( lock ){
      checkState( state == State.NEW || state == State.STOPPED ) ;
      this.setup = checkNotNull( setup ) ;
      doSetup( setup ) ;
      state( State.STOPPED ) ;
    }
  }

  protected void doSetup( SETUP setup ) { }

  protected final SETUP setup() {
    synchronized( lock ){
      checkState( setup != null ) ;
      return setup ;
    }
  }

  @Override
  public final CompletableFuture< ? > startFuture() {
    synchronized( lock ) {
      checkState( executionContext != null, "Not started" ) ;
      return executionContext.startFuture ;
    }
  }

  @Override
  public final CompletableFuture< COMPLETION > terminationFuture() {
    synchronized( lock ) {
      checkState( executionContext != null, "Not started" ) ;
      return executionContext.terminationFuture ;
    }
  }


// =====
// Start
// =====

  @Override
  public final CompletableFuture< ? > start() {
    final ExecutionContext< SETUP, COMPLETION > newExecutionContext ;
    synchronized( lock ){
      checkState( state == State.STOPPED ) ;
      state( State.INITIALIZING ) ;
      newExecutionContext = newExecutionContext( setup ) ;
      executionContext = newExecutionContext ;
    }
    threadFactory( "start", null, null ).newThread( () -> {
      try {
        synchronized( lock ) {
          customInitialize() ;
          state( State.STARTING ) ;
        }
        customStart() ;
      } catch( Exception e ) {
        logger.error( "Custom initialization/start failed", e ) ;
        newExecutionContext.startFuture.completeExceptionally( e ) ;
      }
    } ).start() ;
    return newExecutionContext.startFuture ;
  }

  protected ExecutionContext< SETUP, COMPLETION > newExecutionContext( SETUP setup ) {
    return new ExecutionContext<>( setup ) ;
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
        executionContext.startFuture.complete( null ) ;
      } else {
        executionContext.startFuture.completeExceptionally( new IllegalStateException( "" ) ) ;
      }
    }
  }

  @Override
  public final CompletableFuture< COMPLETION > run() throws Exception {
    final CompletableFuture< COMPLETION > terminationFuture ;
    synchronized( lock ){
      start() ;
      terminationFuture = executionContext.terminationFuture ;
    }
    return terminationFuture ;
  }


// ====
// Stop
// ====

  protected final void completion( final COMPLETION completion ) {
    synchronized( lock ) {
      executionContext.completion = completion ;
    }
  }

  @Override
  public final CompletableFuture< COMPLETION > stop() {
    final CompletableFuture< COMPLETION > stopFuture ;
    logger.info( "Requested to stop " + this + " ..." ) ;
    synchronized( lock ) {
      if( state == State.STARTED ) {
        stopFuture = executionContext.terminationFuture ;
        state( State.STOPPING ) ;
      } else {
        throw new IllegalStateException(
            "Current state is " + state + ", can't stop " + this + "." ) ;
      }
    }
    try {
      customStop() ;
    } catch( final Exception e ) {
      stopFuture.completeExceptionally( e ) ;
    }
    return stopFuture ;
  }

  /**
   * Hook for subclasses to perform custom operations, like setting .
   */
  protected void customStop() throws Exception {
    customStopComplete() ;
  }

  protected final void customStopComplete() {
    final CompletableFuture< COMPLETION > terminationFuture ;
    final COMPLETION completion ;
    final State currentState ;
    synchronized( lock ) {
      currentState = state ;
      if( currentState == State.STOPPING ) {
        terminationFuture = executionContext.terminationFuture ;
        completion = executionContext.completion ;
        state( State.STOPPED ) ;
        /** Don't set {@link #executionContext} to null, some code may still call
         * {@link #startFuture()} or {@link #terminationFuture()}, it may happen if the remote
         * process ended very quickly.*/
      } else {
        terminationFuture = null ;
        completion = null ;
        logger.error( "Can't execute this method while in " + state + "." ) ;
      }
    }
    if( terminationFuture != null ) {
      logger.info(
          "Execution complete with resulting value of " + completion + " for " + this + "."
      ) ;
      terminationFuture.complete( completion ) ;
    }
  }


// ================
// Thread factories
// ================

  private static final AtomicInteger threadCounter = new AtomicInteger() ;

  private static final Map< String, AtomicInteger > SERVICE_INSTANCE_COUNTERS =
      new ConcurrentHashMap<>() ;

  /**
   * Creates a dedicated {@code ThreadFactory} with nice counters.
   * Created {@code Thread} will have a name looking like this:
   *
   * <pre>
   * myservice-1-myrole-mypurpose-2
   *           ^                  ^
   *           |                  |
   *           |                  3rd {@code Thread} created.
   *           2nd {@code ThreadFactory} created with "myservice".
   * </pre>
   *
   * @param service mandatory, can be this class' name.
   * @param role optional.
   * @param purpose optional.
   */
  protected final ThreadFactory threadFactory(
      final String service,
      final String role,
      final String purpose
  ) {
    checkArgument( ! Strings.isNullOrEmpty( service ) ) ;
    return runnable -> {
      final AtomicInteger instanceCounter = SERVICE_INSTANCE_COUNTERS.computeIfAbsent(
          service, Ø -> new AtomicInteger() ) ;
      final StringBuilder stringBuilder = new StringBuilder() ;
      stringBuilder.append( service ) ;
      stringBuilder.append( '-' ) ;
      stringBuilder.append( instanceCounter.getAndIncrement() ) ;
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
      thread.setDaemon( true ) ;
      return thread ;
    } ;
  }



}
