package com.otcdlink.chiron.middle.throttler;

import com.otcdlink.chiron.toolbox.clock.Clock;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Notifies a {@link NotifyingThrottler.Watcher} when
 * no more {@link Restriction} apply on it.
 */
public class NotifyingThrottler<
    COMMAND,
    RESTRICTION extends SessionScopedThrottler.Restriction< COMMAND >
>
    extends SessionScopedThrottler< COMMAND, RESTRICTION >
{

  private final Logger LOGGER = LoggerFactory.getLogger( NotifyingThrottler.class ) ;
  private final ScheduledExecutorService notificationRunner ;

  private final List< Watcher< RESTRICTION > > watchers = new ArrayList<>() ;

  /**
   * Positive adjustment to {@link #throttlingDuration()} so we have a reasonable chance to
   * enable actions only after {@link #throttlingDuration()} expired on Daemon.
   */
  private final int biasMs ;


  public NotifyingThrottler(
      final Clock clock,
      final RestrictionFactory< COMMAND, RESTRICTION > restrictionFactory,
      final ScheduledExecutorService notificationRunner,
      final Duration initialThrottlingDuration,
      final int biasMs
  ) {
    super( clock, restrictionFactory, initialThrottlingDuration ) ;
    this.notificationRunner = checkNotNull( notificationRunner ) ;
    this.biasMs = biasMs ;
  }

  @Override
  protected final void restrictionAdded( final RESTRICTION restriction ) {
    final long delayMs = throttlingDuration().getMillis() + biasMs ;
    LOGGER.debug( "Added " + restriction + ", scheduling cleanup in " + delayMs + " ms ..." ) ;
    notificationRunner.schedule( this::cleanup, delayMs, TimeUnit.MILLISECONDS ) ;
  }

  @Override
  protected final void restrictionRemoved( final RESTRICTION restriction ) {
    final boolean[] watcherIsBlocked = { false } ;
    final Iterator< Watcher< RESTRICTION > > iterator = watchers.iterator() ;
    while( iterator.hasNext() ) {
      final Watcher< RESTRICTION > watcher = iterator.next() ;
      visitAllRestrictions( r -> {
        final boolean blocked = watcher.blockedBy( r ) ;
        if( blocked ) {
          watcherIsBlocked[ 0 ] = true ;
          return false ;
        } else {
          return true ;
        }
      } ) ;
      if( ! watcherIsBlocked[ 0 ] ) {
        iterator.remove() ;
        LOGGER.debug( "Removed " + restriction + ", now notifying " +
            Watcher.class.getSimpleName() + " ..." ) ;
        watcher.restrictionRemoved() ;
      }
    }
  }

  /**
   * Adds a {@link Watcher} that gets its {@link Watcher#restrictionRemoved()} method called,
   * then gets removed from the list of {@link Watcher}s, once its
   * {@link Watcher#blockedBy(Restriction)} method returned {@code false} for every remaining
   * {@link Restriction}.
   */
  public final void addWatcher( final Watcher< RESTRICTION > watcher ) {
    watchers.add( watcher ) ;
  }

  /**
   * @see NotifyingThrottler#addWatcher(Watcher)
   */
  public interface Watcher< RESTRICTION extends Restriction > {
    boolean blockedBy( final RESTRICTION restriction ) ;
    void restrictionRemoved() ;
  }

}
