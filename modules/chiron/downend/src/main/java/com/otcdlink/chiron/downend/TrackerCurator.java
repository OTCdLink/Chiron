package com.otcdlink.chiron.downend;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.middle.CommandFailureNotice;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.clock.Clock;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Redispatches notifications to a set of {@link Tracker}s, removing them when it makes sense.
 */
public class TrackerCurator {

  public interface Claim {

    /**
     * Tells about immediate status change, so that {@link CommandInFlightStatus#QUIET}
     * may immediately follow {@link CommandInFlightStatus#SOME_COMMAND_FAILED}.
     * (Nothing makes the {@link CommandInFlightStatus#SOME_COMMAND_FAILED} status appear for some delay.)
     */
    void commandStatusChanged( CommandInFlightStatus commandInFlightStatus ) ;

  }

  private final Claim claim ;
  private final Clock clock ;
  private volatile Integer trackerLifetimeMs = null ;

  public TrackerCurator( final Claim claim, final Clock clock  ) {
    this.claim = checkNotNull( claim ) ;
    this.clock = checkNotNull( clock ) ;
  }

  private static Integer checkTrackerLifetimeMs( int trackerLifetimeMs ) {
    checkArgument( trackerLifetimeMs >= 0 ) ;
    return trackerLifetimeMs ;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger( TrackerCurator.class ) ;

  private final AtomicLong messageIdentifierGenerator = new AtomicLong() ;

  private final Object lock = ToStringTools.createLockWithNiceToString( TrackerCurator.class ) ;

  /**
   * Synchronize access on {@link #lock}.
   * A {@code TreeMap} keeps keys ordered, making debugging easier.
   */
  private final BiMap<Command.Tag, TrackerEnhancer > trackers =
      HashBiMap.create( new TreeMap<>() ) ;

  public void trackerLifetimeMs( final int trackerLifetimeMs ) {
    this.trackerLifetimeMs = checkTrackerLifetimeMs( trackerLifetimeMs ) ;
  }

  /**
   * Made public for tests only.
   */
  public static final String TAG_PREFIX = "TR-" ;

  public Command.Tag generateTag() {
    return new Command.Tag( TAG_PREFIX + messageIdentifierGenerator.getAndIncrement() ) ;
  }


  public Command.Tag add( final Tracker tracker ) {
    final Integer currentTrackerLifetimeMs = this.trackerLifetimeMs ;
    checkState( currentTrackerLifetimeMs != null ) ;
    final Command.Tag commandTag = generateTag() ;
    final TrackerEnhancer trackerEnhancer ;
    final boolean wasEmpty ;
    synchronized( lock ) {
      wasEmpty = trackers.isEmpty() ;
      trackerEnhancer = new TrackerEnhancer(
          clock.getCurrentDateTime().plus( currentTrackerLifetimeMs ),
          tracker
      ) ;
      trackers.put( commandTag, trackerEnhancer ) ;
    }
    LOGGER.debug( "Added " + keyValuePairAsString( commandTag, trackerEnhancer ) ) ;
    if( wasEmpty ) {
      commandFlightStatusChanged( CommandInFlightStatus.IN_FLIGHT ) ;
    }
    return commandTag ;
  }



  /**
   * Returns a {@link Tracker} that magically removes itself when some of its methods get called.
   */
  public Tracker get( final Command.Tag commandTag ) {
    if( commandTag == null ) {
      return null ;
    } else {
      final TrackerEnhancer trackerEnhancer ;
      synchronized( lock ) {
        trackerEnhancer = trackers.get( commandTag ) ;
      }
      if( trackerEnhancer == null ) {
        return null ;
      } else {
        return trackerEnhancer ;
      }
    }
  }

  /**
   * Returns a {@link Tracker} which transparently notifies the {@link #claim} and removes
   * itself. This makes sense when shortcutting normal flow to return {@link CommandFailureNotice}
   * before sending anything on the wire.
   */
  public Tracker enhance( final Tracker original ) {
    synchronized( lock ) {
      add( original ) ;
      for( final TrackerEnhancer trackerEnhancer : trackers.values() ) {
        if( trackerEnhancer.tracker == original ) {
          return trackerEnhancer ;
        }
      }
    }
    throw new IllegalStateException( "Could not find " + original + " we just added" ) ;
  }


  /**
   * Removal means sending appropriate {@link CommandInFlightStatus} if needed.
   */
  private void remove( final TrackerEnhancer trackerEnhancer, final boolean withError ) {
    final boolean gotEmpty ;
    synchronized( lock ) {
      trackers.inverse().remove( trackerEnhancer ) ;
      gotEmpty = trackers.isEmpty() ;
    }
    if( withError ) {
      commandFlightStatusChanged( CommandInFlightStatus.SOME_COMMAND_FAILED ) ;
    }
    if( gotEmpty ) {
      commandFlightStatusChanged( CommandInFlightStatus.QUIET ) ;
    }
  }


  /**
   * Avoid double notifications. Initial value is chosen to avoid unnecessary notification
   * the first time {@link DownendConnector} switches to {@link DownendConnector.State#SIGNED_IN}
   * (or {@link DownendConnector.State#CONNECTED} if there is no authentication).
   */
  private CommandInFlightStatus lastInFlightStatus = CommandInFlightStatus.QUIET ;

  private void commandFlightStatusChanged( final CommandInFlightStatus inFlightStatus ) {
    if( inFlightStatus != lastInFlightStatus ) {
      claim.commandStatusChanged( inFlightStatus ) ;
      lastInFlightStatus = inFlightStatus ;
    }
  }


  /**
   * Trick to not remove a {@link Tracker} when {@link DownendConnector}
   * asynchronously notifies of its first connection. Because notification is asynchronous,
   * it happens to register some {@link Tracker} before executing
   * {@link #notifyReconnection()}, therefore causing its removal.
   */
  private boolean firstConnectionOccured = false ;

  private boolean connected = false ;



  public void notifyReconnection() {
    final boolean pastFirstConnection ;
    synchronized( lock ) {
      connected = true ;
      pastFirstConnection = firstConnectionOccured ;
      firstConnectionOccured = true ;
      if( pastFirstConnection ) {
        notifyTrackersUnsynchronized(
            Tracker::onConnectionRestored,
            any -> true
        ) ;
      }
      commandFlightStatusChanged( CommandInFlightStatus.QUIET ) ;
    }
  }

  public void notifyConnectionBroken() {
    synchronized( lock ) {
      connected = false ;
      notifyTrackersUnsynchronized( Tracker::onConnectionLost, any -> true ) ;
    }
  }

  /**
   * Let some other code find some {@code ScheduledExecutorService} to do the job.
   */
  public void scavengeTimeouts() {
    synchronized( lock ) {
      if( connected ) {
        final long now = clock.getCurrentDateTime().getMillis() ;
        notifyTrackersUnsynchronized(
            Tracker::afterTimeout,
            trackerSlot -> trackerSlot.endOfLifeTimestamp < now
        ) ;
      }
    }
  }

  private void notifyTrackersUnsynchronized(
      final Consumer< Tracker > notificationMethod,
      final Function< TrackerEnhancer, Boolean > trackerSlotActionResolver
  ) {
    final boolean hadTracker = ! trackers.isEmpty() ;
    final Set< Map.Entry< Command.Tag, TrackerEnhancer > > notifiees = new LinkedHashSet<>() ;
    for( final Map.Entry< Command.Tag, TrackerEnhancer > entry : trackers.entrySet() ) {
      final boolean notify = trackerSlotActionResolver.apply( entry.getValue() ) ;
      if( notify ) {
        notifiees.add( entry ) ;
      }
    }
    /** Since {@link TrackerEnhancer} performs a removal on its own we should not call its methods
     * while iterating on {@link #trackers}, otherwise this would cause a
     * {@code ConcurrentModificationException}. */
    for( final Map.Entry< Command.Tag, TrackerEnhancer > entry : notifiees ) {
      notificationMethod.accept( entry.getValue() ) ;
    }
    if( hadTracker && trackers.isEmpty() ) {
      commandFlightStatusChanged( CommandInFlightStatus.QUIET ) ;
    }
  }


  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + '{' +
        "trackerLifetimeMs=" + trackerLifetimeMs + ';' +
        '}'
    ;
  }

  private static String mapEntryAsString( final Map.Entry< Command.Tag, TrackerEnhancer> entry ) {
    return keyValuePairAsString( entry.getKey(), entry.getValue() ) ;
  }

  private static String keyValuePairAsString(
      final Command.Tag commandTag,
      final TrackerEnhancer trackerEnhancer
  ) {
    return commandTag + "->" + trackerEnhancer ;
  }

  /**
   * Keeps the {@link #endOfLifeTimestamp} for {@link TrackerCurator#scavengeTimeouts()}, and magically
   * calls methods modifying {@link TrackerCurator#trackers} and calling
   * {@link TrackerCurator.Claim} methods.
   * This class avoids to transfer too many responsabilities to the calling code, which otherwise
   * should call {@link Tracker} notification methods <i>and</i> {@link TrackerCurator} removal
   * methods.
   */
  private class TrackerEnhancer implements Tracker {
    public final long endOfLifeTimestamp ;
    public final Tracker tracker ;

    public TrackerEnhancer( final DateTime endOfLifeTimestamp, final Tracker tracker ) {
      this.endOfLifeTimestamp = endOfLifeTimestamp.getMillis() ;
      this.tracker = checkNotNull( tracker ) ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final TrackerEnhancer that = ( TrackerEnhancer ) other ;
      if( endOfLifeTimestamp != that.endOfLifeTimestamp ) {
        return false ;
      }
      return tracker == that.tracker ;
    }

    @Override
    public int hashCode() {
      int result = ( int ) ( endOfLifeTimestamp ^ ( endOfLifeTimestamp >>> 32 ) ) ;
      result = 31 * result + System.identityHashCode( tracker ) ;
      return result ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + '{' + endOfLifeTimestamp + ';' + tracker + '}' ;
    }

// ===============
// Tracker methods
// ===============

    @Override
    public void afterResponseHandled() {
      remove( this, false ) ;
      tracker.afterResponseHandled() ;
    }

    @Override
    public void afterRemoteFailure( final CommandFailureNotice commandFailureNotice ) {
      remove( this, true ) ;
      tracker.afterRemoteFailure( commandFailureNotice ) ;
    }

    @Override
    public void afterTimeout() {
      remove( this, true ) ;
      tracker.afterTimeout() ;
    }

    @Override
    public void onConnectionRestored() {
      tracker.onConnectionRestored() ;
    }

    @Override
    public void onConnectionLost() {
      tracker.onConnectionLost() ;
    }


  }
}
