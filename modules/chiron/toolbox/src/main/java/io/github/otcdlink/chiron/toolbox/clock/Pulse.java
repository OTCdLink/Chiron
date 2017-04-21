package io.github.otcdlink.chiron.toolbox.clock;

import io.github.otcdlink.chiron.toolbox.StateHolder;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.toolbox.concurrent.ExecutorTools.singleThreadedScheduledExecutorServiceFactory;

/**
 * Plan execution of recurring tasks at each transition to the next second/minute/hour/day.
 *
 * @see Resolution
 */
public abstract class Pulse implements Clock {

  private static final Logger LOGGER = LoggerFactory.getLogger( Pulse.class ) ;

  /**
   * Gets called from scheduler's thread, or from {@link #start()} caller's thread.
   */
  private final Tickee tickee ;

  /**
   * Need a lock to not run {@link #stop()} while there is a still-running {@link Tickee}.
   */
  private final Object lock = ToStringTools.createLockWithNiceToString( Pulse.class ) ;


  protected final Resolution resolution ;

  private final AtomicReference< DateTime > lastDateTime = new AtomicReference<>( null ) ;

  private enum State {
    STOPPED, STARTING, RUNNING, STOPPING,
  }

  public interface Tickee {
    void tick( final DateTime now ) ;

    @Deprecated
    Tickee NULL = new Tickee() {
      @Override
      public void tick( final DateTime now ) {
        LOGGER.warn( "Using " + this ) ;
      }

      @Override
      public String toString() {
        return Tickee.class.getSimpleName() + "{NULL}" ;
      }
    } ;
  }

  protected final StateHolder< State > state = new StateHolder<>( State.STOPPED ) ;

  protected Pulse( final Resolution resolution, final Tickee tickee ) {
    this.resolution = checkNotNull( resolution ) ;
    this.tickee = checkNotNull( tickee ) ;
  }

  /**
   * If transition occured between {@link #stop()} and {@link #start()}
   * then {@link #tickQuietly(DateTime)} gets called.
   */
  public final void start() {
    start( null ) ;
  }

  /**
   * If transition occured between {@link #stop()} and {@link #start()}
   * then {@link #tickQuietly(DateTime)} gets called.
   *
   * @param previous may be {@code null}. If not {@code null}, transition is evaluated between
   *     {@code previous} and now.
   */
  public final void start( final DateTime previous ) {
    synchronized( lock ) {
      if( resolution == Resolution.NEVER ) {
        state.update( State.RUNNING, State.STOPPED ) ;
        LOGGER.warn(
            "Using a resolution of " + resolution + " in " + this + ", not started for real." ) ;
      } else {
        state.update( State.STARTING, State.STOPPED ) ;
        startScheduler() ;
        state.update( State.RUNNING, State.STARTING ) ;
        evaluateTransitionNow( true, previous ) ;
        LOGGER.info( "Started " + this + "." ) ;
      }
    }
  }

  public final void stop() {
    synchronized( lock ) {
      if( resolution == Resolution.NEVER ) {
        state.update( State.STOPPED, State.RUNNING ) ;
        LOGGER.warn(
            "Using a resolution of " + resolution + " in " + this + ", not stopped for real." ) ;
      } else {
        state.update( State.STOPPING, State.RUNNING ) ;
        try {
          stopScheduler() ;
        } finally {
          state.update( State.STOPPED, State.STOPPING ) ;
        }
        LOGGER.info( "Stopped " + this + "." ) ;
      }
    }
  }

  protected final boolean running() {
    return state.get() == State.RUNNING ;
  }

  private void tickQuietly( final DateTime now ) {
    try {
      tickee.tick( now ) ;
    } catch( final Exception e ) {
      LOGGER.error( "Error when running " + tickee + " at " + now + ".", e ) ;
    }
  }

  protected final void evaluateTransitionNow() {
    evaluateTransitionNow( false, null ) ;
  }

  protected final void evaluateTransitionNow(
      final boolean evaluatePrevious,
      final DateTime previous
  ) {
    synchronized( lock ) {
      if( state.get() == State.RUNNING ) {
        final DateTime now = getCurrentDateTime() ;
        final DateTime last = evaluatePrevious
            ? PulseTools.mostRecent( previous, lastDateTime.getAndSet( now ) )
            : lastDateTime.getAndSet( now )
        ;
        final boolean transitioned = PulseTools.transitioned( last, now, resolution ) ;

        if( ( evaluatePrevious && previous == null ) || ( last != null && transitioned ) ) {
          tickQuietly( now ) ; // Supposed be called from scheduler's thread, dangerous otherwise.
        }

        final long delay = PulseTools.millisecondsBeforeNextTransition( now, resolution ) ;

        // Do this last as it updates current task. So when concrete implementation tries to
        // cancel/wait for completion of current task, it hits the one running.
        schedule( delay, this::evaluateTransitionNow ) ;
      }
    }
  }

  protected abstract void startScheduler() ;

  protected abstract void schedule( final long delayMilliseconds, final Runnable runnable ) ;

  protected abstract void stopScheduler() ;

  public enum Resolution {
    DECISECOND( 100 ),
    SECOND( 1_000 ),
    DECASECOND( 10_000 ),
    MINUTE( 60 * 1_000 ),
    HOUR( 60 * 60 * 1_000 ),
    DAY( 24 * 60 * 60 * 1_000 ),
    NEVER( -1 )
    ;
    public final int milliseconds ;

    Resolution( final int milliseconds ) {
      this.milliseconds = milliseconds ;
    }

    public static final Resolution DEFAULT = DAY ;
  }

  public interface Factory {
    Pulse create( Resolution resolution, Tickee tickee ) ;
    Factory DEFAULT = newWithExecutorService() ;

    static Factory newWithExecutorService() {
      return new Factory() {
        final ScheduledExecutorService scheduledExecutorService =
            singleThreadedScheduledExecutorServiceFactory( Pulse.class.getSimpleName() ).create() ;
        @Override
        public Pulse create( final Resolution resolution, final Tickee tickee ) {
          return new WithExecutorService( scheduledExecutorService, resolution, tickee ) ;
        }
      } ;
    }
  }

  @Override
  public String toString() {
    return
        ToStringTools.nameAndCompactHash( this ) + '{' +
            "resolution=" + resolution + ';' +
            "state=" + state.get() +
        '}'
    ;
  }

  public static class WithExecutorService extends Pulse {

    /**
     * No {@link ScheduledExecutorService#shutdown} occurs when calling {@link #stop()}.
     */
    private final ScheduledExecutorService scheduledExecutorService ;

    /**
     * No synchronization needed as long as {@link #scheduledExecutorService} is single-threaded.
     */
    private ScheduledFuture< ? > runningTask = null ;

    public WithExecutorService(
        final ScheduledExecutorService scheduledExecutorService,
        final Resolution resolution,
        final Tickee tickee
    ) {
      super( resolution, tickee ) ;
      this.scheduledExecutorService = scheduledExecutorService ;
    }

    @Override
    protected void startScheduler() { }

    @Override
    protected void schedule( final long delayMilliseconds, final Runnable runnable ) {
      // Can't check state because we are executing this code as part of current running task.
      // checkState( runningTask.isDone() || runningTask.isCancelled() ) ;
      runningTask = scheduledExecutorService.schedule(
          runnable, delayMilliseconds, TimeUnit.MILLISECONDS ) ;
    }

    @Override
    protected void stopScheduler() {
      final ScheduledFuture< ? > current = this.runningTask ;
      this.runningTask = null ;
      if( current != null ) {
        current.cancel( true ) ;
      }
    }

    @Override
    public long currentTimeMillis() {
      return System.currentTimeMillis() ;
    }

  }
}
