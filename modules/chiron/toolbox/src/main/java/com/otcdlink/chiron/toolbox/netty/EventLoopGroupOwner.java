package com.otcdlink.chiron.toolbox.netty;

import com.google.common.base.Strings;
import com.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Base class for using an externally-set {@link EventLoopGroup}, or creating one from
 * a {@link EventLoopGroupFactory} upon a call to {@link #regenerateEventLoopIfNeeded()}
 * (which is the responsability of the subclass).
 */
public abstract class EventLoopGroupOwner {

  private static final Logger LOGGER = LoggerFactory.getLogger( EventLoopGroupOwner.class ) ;

  private EventLoopGroup eventLoopGroup ;

  /**
   * If {@code null}, means we don't alter {@link #eventLoopGroup}'s lifecycle during
   * {@link InputOutputLifecycled#start()} and {@link InputOutputLifecycled#stop()}.
   */
  private final EventLoopGroupFactory eventLoopGroupFactory ;

  protected EventLoopGroupOwner(
      final EventLoopGroup eventLoopGroup
  ) {
    this( null, eventLoopGroup ) ;
  }

  protected EventLoopGroupOwner(
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactoryResolver
  ) {
    this( eventLoopGroupFactoryResolver, null ) ;
  }

  protected EventLoopGroupOwner(
      final Function< String, EventLoopGroupFactory > eventLoopGroupFactoryResolver,
      final EventLoopGroup eventLoopGroup
  ) {
    if( eventLoopGroupFactoryResolver == null ) {
      this.eventLoopGroupFactory = null ;
      this.eventLoopGroup = checkNotNull( eventLoopGroup ) ;
    } else {
      checkArgument( eventLoopGroup == null ) ;
      this.eventLoopGroupFactory =
          eventLoopGroupFactoryResolver.apply( ToStringTools.getNiceName( getClass() ) ) ;
      this.eventLoopGroup = eventLoopGroupFactory.newEventLoopGroup() ;
    }
  }

  /**
   * Creates a fresh {@link NioEventLoopGroup} so we can {@link InputOutputLifecycled#start()} again
   * after a {@link InputOutputLifecycled#stop()}.
   */
  public static class EventLoopGroupFactory {

    private final int threadPoolSize ;
    private final String threadNameRadix ;
    private final BiFunction< String, Runnable, Thread > threadCreator ;
    private final AtomicInteger threadCounter = new AtomicInteger( 0 ) ;

    /**
     * For each {@link #threadNameRadix} there is a counter that increments each time
     * we create a new {@link EventLoopGroup} for it.
     */
    private static final ConcurrentMap< String, AtomicInteger > poolCounters =
        new ConcurrentHashMap<>() ;

    /**
     * There is a temptation to add a {@link Logger} parameter, which would make uncaught
     * throwable reporting clearer.
     * TODO: support a {@link Logger} field in {@link EventLoopGroupOwner}.
     */
    public interface ThreadCreator {
      Thread create( String threadName, Logger logger, Runnable runnable ) ;
    }

    /**
     * @param threadNameRadix the name we want to give, minus counters.
     * @param threadCreator creates a {@code Thread} with given name, which includes counters.
     */
    public EventLoopGroupFactory(
        final String threadNameRadix,
        final BiFunction< String, Runnable, Thread > threadCreator,
        final int threadPoolSize
    ) {
      checkArgument( ! Strings.isNullOrEmpty( threadNameRadix ) ) ;
      this.threadNameRadix = threadNameRadix ;
      this.threadCreator = checkNotNull( threadCreator ) ;
      checkArgument( threadPoolSize >= 0 ) ;
      this.threadPoolSize = threadPoolSize ;
    }

    public static EventLoopGroupFactory defaultFactory( final String threadNameRadix ) {
      return new EventLoopGroupFactory(
          threadNameRadix,
          ( name, runnable ) -> {
            final Thread thread = new Thread( runnable ) ;
            thread.setName( name ) ;
            thread.setDaemon( true ) ;
            thread.setUncaughtExceptionHandler( ( t, e ) ->
                LOGGER.error( "Reporting uncaught exception in " + t.getName() + ".", e ) ) ;
            return thread ;
          },
          0
      ) ;
    }

    public EventLoopGroup newEventLoopGroup() {
      final int poolIndex = poolCounters.computeIfAbsent(
          threadNameRadix, Ø -> new AtomicInteger( 0 ) ).getAndIncrement() ;
      final ThreadFactory threadFactory = runnable -> {
        final String threadName = threadNameRadix + "-" + poolIndex
            + "-" + threadCounter.getAndIncrement() ;
        final Thread thread = threadCreator.apply( threadName, runnable ) ;
        return thread ;
      } ;
      return new NioEventLoopGroup( threadPoolSize, threadFactory ) ;
    }

  }

  protected final EventLoopGroup eventLoopGroup() {
    return eventLoopGroup ;
  }

  @Override
  public final String toString() {
    final StringBuilder stringBuilder = new StringBuilder() ;
    stringBuilder
        .append( ToStringTools.nameAndCompactHash( this ) )
        .append( '{' )
    ;
    augmentToString( stringBuilder ) ;
    stringBuilder.append( '}' ) ;
    return stringBuilder.toString() ;
  }

  protected void augmentToString( final StringBuilder stringBuilder ) { }


// =========
// Lifecycle
// =========

  protected final Object lock = ToStringTools.createLockWithNiceToString( getClass() ) ;

  public enum State { STOPPED, STARTING, STARTED, STOPPING }

  /**
   * Change must be synchronized on {@link #lock}.
   * Made volatile so {@link #state()} always reads a non-stale value.
   */
  protected volatile State state = State.STOPPED ;

  public final State state() {
    return state ;
  }

  /**
   * Terminates {@link #eventLoopGroup} if a new one can be created using
   * {@link #eventLoopGroupFactory}.
   *
   *
   */
  protected final CompletableFuture< ? > regenerateEventLoopIfNeeded() {
    if( eventLoopGroupFactory != null ) {
      final CompletableFuture< ? > completableFuture = new CompletableFuture<>() ;
      /** Don't call {@link Future#sync()} since this method can get called
       * from the {@link EventLoopGroup} itself so it would cause a deadlock.
       * TODO: support custom timeout/quiet period. */
      final io.netty.util.concurrent.Future< ? > nettyFuture =
          eventLoopGroup.shutdownGracefully( 0, 0, TimeUnit.MILLISECONDS ) ;
      nettyFuture.addListener( future -> {
        if( future.cause() == null ) {
          completableFuture.complete( null ) ;
        } else {
          completableFuture.completeExceptionally( future.cause() ) ;
        }
      } ) ;
      eventLoopGroup = eventLoopGroupFactory.newEventLoopGroup() ;
      return completableFuture ;
    } else {
      return COMPLETED_FUTURE ;
    }
  }


  protected static final CompletableFuture< ? > COMPLETED_FUTURE =
      CompletableFuture.completedFuture( null ) ;


// =======
// Logging
// =======

  public static < T > BiConsumer< T, Throwable > logStartCompletion( final Logger logger ) {
    return logCompletion( logger, "Start" ) ;
  }

  public static < T > BiConsumer< T, Throwable > logStopCompletion( final Logger logger ) {
    return logCompletion( logger, "Stop" ) ;
  }

  public static < T > BiConsumer< T, Throwable > logCompletion(
      final Logger logger,
      final String operationName
  ) {
    return ( Ø, t ) -> {
      if( t == null ) {
        logger.info( operationName + " succeeded." ) ;
      } else {
        logger.error( operationName + " failed", t ) ;
      }
    } ;
  }



}
