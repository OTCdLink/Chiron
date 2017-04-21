package io.github.otcdlink.chiron.upend.session.implementation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.session.SignonDecision;
import io.github.otcdlink.chiron.middle.session.SignonFailure;
import io.github.otcdlink.chiron.middle.session.SignonFailureNotice;
import io.github.otcdlink.chiron.toolbox.collection.KeyHolder;
import io.github.otcdlink.chiron.toolbox.collection.SortedKeyHolderMap;
import io.github.otcdlink.chiron.upend.session.SignableUser;
import io.github.otcdlink.chiron.upend.session.SignonOutwardDuty;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Takes care of session states, enforcing invariants.
 * This class is not thread-safe.
 *
 * <h1>Invariants</h1>
 * <ul>
 *   <li>
 *     For each {@link SESSION_IDENTIFIER} there is at most one {@link CHANNEL}.
 *   </li><li>
 *     For each {@link CHANNEL} referenced by an instance of this class, there is only one
 *     {@link SESSION_IDENTIFIER}.
 *   </li><li>
 *     For each {@link SignableUser} referenced by an instance of this class, there is only one
 *     {@link SESSION_IDENTIFIER}.
 *   </li><li>
 *     The state associated to a {@link SESSION_IDENTIFIER} is kept inside a {@link SessionDetail}
 *     which defines its own state transitions. The {@link SessionBook} relies on those transitions.
 *   </li>
 * </ul>
 *
 *
 *
 * <h1>Design notes</h1>
 * <p>
 * Generic types here just mean the generic classes do not have to respect any special contract,
 * apart of standard {@code toString}, {@code hashCode}, and {@code equals}. The only exception is
 * {@link #remoteAddress(Object)} for extracting a {@link CHANNEL}'s remote address.
 * <p>
 * All methods of this class are synchronous. Instances of this class are not thread-safe.
 * Methods throw now exceptions, instead a {@link SignonFailureNotice} describes
 * what got wrong.
 * <p>
 * The initial Portal specification (d9d6f89af269a51186e6e3dc7a2c344d1b5ccc89) implies there
 * can be more than one {@link CHANNEL} per {@link SESSION_IDENTIFIER} as long as there is
 * no more than one {@link CHANNEL} with associated modification capabilities. This feature
 * is messy and got removed.
 * <p>
 * The {@link SessionBook} separates
 * {@link #create(KeyHolder.Key, Object, SignableUser, DateTime)}  session creation}
 * and {@link #activate(SESSION_IDENTIFIER, CHANNEL, DateTime, boolean)}  association}
 * because we need to keep {@link SignableUser} somewhere while secondary authentication happens.
 *
 * <h2>IP Spoofing prevention</h2>
 * <p>
 * When using
 * {@link #activate(SESSION_IDENTIFIER, CHANNEL, DateTime, boolean)}
 * or {@link #reuse(KeyHolder.Key, Object, DateTime)}
 * the {@link ADDRESS} must match the one used in
 * {@link #create(KeyHolder.Key, Object, SignableUser, DateTime)}
 * for the given {@link SESSION_IDENTIFIER}.
 * <p>
 * Because remote users may stand behind a proxy, they can't be uniquely identified by their
 * {@link ADDRESS} .
 *
 * <h2>Session hijacking prevention</h2>
 * <p>
 * There could be a window of opportunity to brute-force {@link SESSION_IDENTIFIER} when in
 * {@link SessionDetail.Orphaned} or {@link SessionDetail.Reusing} state. For this reason,
 * every failing attempt to transition away from one of those state causes the removal of the
 * {@link SessionDetail}.  
 */
class SessionBook<
    SESSION_IDENTIFIER extends KeyHolder.Key< SESSION_IDENTIFIER >,
    CHANNEL,
    ADDRESS
> {

  private static final Logger LOGGER = LoggerFactory.getLogger( SessionBook.class ) ;

  /**
   * Stable ordering with a {@link java.util.SortedMap}, this makes debugging easier.
   */
  private final SortedKeyHolderMap<
      SESSION_IDENTIFIER,
      SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS >
  > sessionDetailMap = new SortedKeyHolderMap( SessionIdentifier.COMPARATOR ) ;

  /**
   * Maximum lifetime for other than {@link SessionDetail.Active} based on
   * {@link SessionDetail#inactiveSince}.
   */
  public final Duration maximumInactivityDuration ;

  private final Function< CHANNEL, ADDRESS > addressExtractor ;

  SessionBook(
      final Function< CHANNEL, ADDRESS > addressExtractor,
      final Duration maximumInactivityDuration
  ) {
    this.addressExtractor = checkNotNull( addressExtractor ) ;
    this.maximumInactivityDuration = checkNotNull( maximumInactivityDuration ) ;
  }

  public < DETAIL extends SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > > DETAIL
  sessionDetail(
      final SESSION_IDENTIFIER sessionIdentifier
  ) {
    checkNotNull( sessionIdentifier ) ;
    return ( DETAIL ) sessionDetailMap.get( sessionIdentifier ) ;
  }

  /**
   * Create a {@link SessionDetail.Pending} with a fresh {@link SESSION_IDENTIFIER}.
   * The next step is {@link #activate(SESSION_IDENTIFIER, CHANNEL, DateTime, boolean)}
   * to upgrade to {@link SessionDetail.Active}.
   * Given {@link SESSION_IDENTIFIER}, {@link CHANNEL}, and {@link SignableUser} should not
   * previously appear anywhere in {@link #sessionDetailMap}.
   *
   * @return a (non-{@code null}) {@link SignonFailureNotice}, or a non-{@code null}
   *     {@link SignableUser}, wrapped in a non-{@code null} {@link SignonDecision}.
   */
  public SignonFailureNotice create(
      final SESSION_IDENTIFIER sessionIdentifier,
      final CHANNEL channel,
      final SignableUser user,
      final DateTime creationTime
  ) {
    checkNotNull( sessionIdentifier ) ;
    checkNotNull( user ) ;
    checkNotNull( channel ) ;
    checkNotNull( creationTime ) ;

    final Iterator< Map.Entry<
        SESSION_IDENTIFIER,
        SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > >
    > iterator = sessionDetailMap.entrySet().iterator() ;
    while( iterator.hasNext() ) {
      final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > sessionDetail =
          iterator.next().getValue() ;
      if( sessionDetail.user.login().equals( user.login() ) ) {
        if( sessionDetail instanceof SessionDetail.Orphaned ) {
          iterator.remove() ;
        } else {
          return userSessionAlreadyAttributed(
              user, sessionDetail.sessionIdentifier, sessionIdentifier ) ;
        }
      }
    }

    final SignonFailureNotice signonFailureNotice = checkChannelUnregistered(
        sessionIdentifier, channel, null ) ;
    if( signonFailureNotice != null ) {
      return signonFailureNotice ;
    }

    final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > detail =
        sessionDetailMap.get( sessionIdentifier ) ;

    if( detail == null ) {
      final SessionDetail.Pending< SESSION_IDENTIFIER, CHANNEL, ADDRESS > pending =
          SessionDetail.pending( sessionIdentifier, creationTime, channel, user ) ;
      sessionDetailMap.put( pending ) ;
      LOGGER.debug( "Registered new " + pending + "." ) ;
      return null ;
    } else {
      return sessionAlreadyExists( sessionIdentifier ) ;
    }

  }

  /**
   * Upgrades a {@link SessionDetail.Orphaned} into {@link SessionDetail.Reusing}.
   * The next step is {@link #activate(SESSION_IDENTIFIER, CHANNEL, DateTime, boolean)}
   * to upgrade to {@link SessionDetail.Active}.
   *
   * @param now current absolute time, used to resolve {@link SessionDetail.Orphaned} validity.
   *
   * @return a (non-{@code null}) {@link SignonFailureNotice}, or a non-{@code null}
   *     {@link SignableUser}, wrapped in a non-{@code null} {@link SignonDecision}.
   */
  public SignonDecision< SignableUser > reuse(
      final SESSION_IDENTIFIER sessionIdentifier,
      final CHANNEL channel,
      final DateTime now
  ) {
    checkNotNull( sessionIdentifier ) ;
    checkNotNull( channel ) ;
    final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > sessionDetail =
        sessionDetailMap.get( sessionIdentifier ) ;

    if( sessionDetail == null ) {
      return new SignonDecision<>( unknownSession( sessionIdentifier ) ) ;
    } else {

      final SignonFailureNotice signonFailureNotice0 ;
      signonFailureNotice0 = removeIfInactiveForTooLong( sessionIdentifier, sessionDetail, now ) ;
      if( signonFailureNotice0 != null ) {
        return new SignonDecision<>( signonFailureNotice0 ) ;
      }

      if( sessionDetail instanceof SessionDetail.Orphaned ) {
        final SessionDetail.Orphaned< SESSION_IDENTIFIER, CHANNEL, ADDRESS > orphaned =
            ( SessionDetail.Orphaned< SESSION_IDENTIFIER, CHANNEL, ADDRESS > ) sessionDetail ;
        if( ! remoteAddress( channel ).equals( orphaned.remoteAddress ) ) {
          final SessionDetail removed = sessionDetailMap.remove( sessionDetail.key() ) ;
          LOGGER.debug( "Removed " + removed + " because its remote address doesn't match " +
              orphaned + "'s." ) ;
          return new SignonDecision<>(
              new SignonFailureNotice( SignonFailure.UNMATCHED_NETWORK_ADDRESS ) ) ;
        }
        final SessionDetail.Reusing< SESSION_IDENTIFIER, CHANNEL, ADDRESS > reusing =
            orphaned.reusing( channel ) ;
        final SessionDetail replaced = sessionDetailMap.replace( reusing ) ;
        LOGGER.debug( "Replaced with " + replaced + " the old " + replaced + ".");
        return new SignonDecision<>( orphaned.user ) ;
      } else {
        final SessionDetail removed = sessionDetailMap.remove( sessionDetail.key() ) ;
            LOGGER.debug( "Removed " + removed + " because not " +
                SessionDetail.Orphaned.class.getSimpleName() + " as expected." ) ;
        return new SignonDecision<>( new SignonFailureNotice(
            SignonFailure.UNEXPECTED, "Unexpected state for existing session" ) ) ;
      }
    }

  }

  /**
   * Upgrades an existing {@link SessionDetail.Pending} or {@link SessionDetail.Reusing}
   * into {@link SessionDetail.Active}, which means {@link SESSION_IDENTIFIER}
   * was blessed by a call to
   * {@link SignonOutwardDuty#sessionCreated(Designator, SessionIdentifier, String)}.
   *
   * @param now current absolute time, used to resolve {@link SessionDetail.Orphaned} validity.
   *
   * @param join {@code false} when expecting a {@link SessionDetail.Pending},
   *     {@code true} when expecting {@link SessionDetail.Reusing}.
   *
   * @return a (non-{@code null}) {@link SignonFailureNotice}, or a non-{@code null}
   *     {@link SignableUser}, wrapped in a non-{@code null} {@link SignonDecision}.
   */
  public SignonDecision< SignableUser > activate(
      final SESSION_IDENTIFIER sessionIdentifier,
      final CHANNEL channel,
      final DateTime now,
      final boolean join
  ) {
    checkNotNull( sessionIdentifier ) ;
    checkNotNull( channel ) ;

    final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > sessionDetail =
        sessionDetailMap.get( sessionIdentifier ) ;
    if( sessionDetail == null ) {
      return new SignonDecision<>( unknownSession( sessionIdentifier ) ) ;
    }

    final SignonFailureNotice signonFailureNotice0 ;
    signonFailureNotice0 = removeIfInactiveForTooLong( sessionIdentifier, sessionDetail, now ) ;
    if( signonFailureNotice0 != null ) {
      return new SignonDecision<>( signonFailureNotice0 ) ;
    }

    if( sessionDetail.channel != null &&
        ! remoteAddress( sessionDetail.channel ).equals( remoteAddress( channel ) )
    ) {
      final SessionDetail removed = sessionDetailMap.remove( sessionDetail.key() ) ;
      LOGGER.debug( "Removed " + removed + " because remote address doesn't match " +
          channel + "'s." ) ;
      return new SignonDecision<>(
          new SignonFailureNotice( SignonFailure.UNMATCHED_NETWORK_ADDRESS ) ) ;
    }

    if( sessionDetail instanceof SessionDetail.Activable ) {
      final SessionDetail.Activable activable = ( SessionDetail.Activable ) sessionDetail ;
      if( ( join && sessionDetail instanceof SessionDetail.Reusing ) ||
          ( ! join && sessionDetail instanceof SessionDetail.Pending )
      ) {
        @SuppressWarnings( "unchecked" )
        final SessionDetail.Active active = activable.activate() ;
        final SessionDetail replaced = sessionDetailMap.replace( active ) ;
        LOGGER.debug( "Replaced with " + active + " previous " + replaced +
            " because of successful activation." ) ;
        return new SignonDecision<>( active.user ) ;
      }
    }

    final SessionDetail removed = sessionDetailMap.remove( sessionDetail.key() ) ;
    LOGGER.info( "Removed " + removed +
        " as fallback during an unsuccessful activation attempt." ) ;
    return new SignonDecision<>( new SignonFailureNotice(
        SignonFailure.UNEXPECTED,
        "Registered session not in expected state"
    ) ) ;

  }

  public CHANNEL removeSession( final SESSION_IDENTIFIER sessionIdentifier ) {
    checkNotNull( sessionIdentifier ) ;
    final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > removed =
        sessionDetailMap.remove( sessionIdentifier ) ;
    if( removed == null ) {
      return null ;
    } else {
      LOGGER.debug( "Removed " + removed + " as asked to remove the session." ) ;
      return removed.channel ;
    }
  }

  /**
   * Remove the {@link SessionDetail} with given {@link CHANNEL}, or makes it
   * {@link SessionDetail.Orphaned}.
   *
   * <h1>Use of invariants</h1>
   * This methods does not attempt to detect duplicate {@link CHANNEL}s, this should have been done
   * in {@link #create(KeyHolder.Key, Object, SignableUser, DateTime)} and
   * {@link #reuse(KeyHolder.Key, Object, DateTime)}.
   *
   * @param now a null {@code null} value means removing the {@link SessionDetail} (if any),
   *     a non-{@code null} value means changing the state to {@link SessionDetail.Orphaned}.
   *     Why this? The {@code now} parameter gives the value to set {@link SessionDetail#inactiveSince}
   *     to if {@link CHANNEL}s is removed. This makes sense if we want to keep the
   *     {@link SessionDetail.Orphaned} for a certain duration (for recovering after a network
   *     outage). On the other hand, the {@code now} parameter makes no sense when we want to remove
   *     the whole {@link SessionDetail.Connected} so we use nullity to convey this intention.
   *
   * @return {@code true} if removal happened.
   */
  public boolean removeChannel( final CHANNEL channel, final DateTime now ) {
    checkNotNull( channel ) ;
    List< SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > > found = new ArrayList<>( 1 ) ;
    for( final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > sessionDetail : sessionDetailMap.values() ) {
      if( channel.equals( sessionDetail.channel ) ) {
        found.add( sessionDetail ) ;
      }
    }
    if( found.isEmpty() ) {
      return false ;
    } else {
      if( found.size() > 1 ) {
        LOGGER.error( "Found " + channel + " more than once: " + ImmutableList.copyOf( found ) +
            ", invariants did not apply. Removing all channels." ) ;
        for( final SessionDetail< SESSION_IDENTIFIER, ?, ? > sessionDetail :
            sessionDetailMap.values()
        ) {
          sessionDetailMap.remove( sessionDetail.sessionIdentifier ) ;
          LOGGER.info( "Removed " + sessionDetail + " with no replacement." ) ;
        }
      }
      final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > sessionDetail = found.get( 0 ) ;
      if( now == null ) {
        sessionDetailMap.remove( sessionDetail.sessionIdentifier ) ;
      } else {
        if( sessionDetail instanceof SessionDetail.Active ) {
          final SessionDetail.Active< SESSION_IDENTIFIER, CHANNEL, ADDRESS > connected =
              ( SessionDetail.Active< SESSION_IDENTIFIER, CHANNEL, ADDRESS > ) sessionDetail ;
          final SessionDetail.Orphaned< SESSION_IDENTIFIER, CHANNEL, ADDRESS > orphaned =
              connected.orphaned( now, remoteAddress( connected.channel ) ) ;
          final SessionDetail replaced = sessionDetailMap.replace( orphaned ) ;
          LOGGER.info( "Replaced with " + orphaned + " previous " + replaced +
              " because channel removal did not imply session removal.") ;
        }
      }
      return true ;
    }
  }

  public ImmutableSet< CHANNEL > removeAllChannels() {
    final ImmutableSet.Builder< CHANNEL > removedChannels = ImmutableSet.builder() ;
    processAllChannels( sessionDetail -> {
      if( sessionDetail.channel != null ) {
        removedChannels.add( sessionDetail.channel ) ;
      }
      return ChannelProcessor.ProcessAction.CONTINUE ;
    } ) ;
    return removedChannels.build() ;
  }

// =========
// Utilities
// =========

  private ADDRESS remoteAddress( final CHANNEL channel ) {
    final ADDRESS address = addressExtractor.apply( channel ) ;
    checkState( address != null, "Bad implementation" ) ;
    return address ;
  }




  private SignonFailureNotice removeIfInactiveForTooLong(
      final SESSION_IDENTIFIER sessionIdentifier,
      final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > sessionDetail,
      final DateTime now
  ) {
    if( sessionDetail.inactiveSince != null ) {
      final DateTime earliestOrphanhood = now.minus( maximumInactivityDuration ) ;
      if( sessionDetail.inactiveSince.isBefore( earliestOrphanhood ) ) {
        final SessionDetail removed = sessionDetailMap.remove( sessionDetail.key() ) ;
        LOGGER.debug( "Removed " + removed + " because inactive for more than " +
            maximumInactivityDuration + "." ) ;
        return unknownSession( sessionIdentifier ) ;
      }
    }
    return null ;
  }


  /**
   * Ensure that given {@link CHANNEL} is reference only once in {@link #sessionDetailMap}.
   */
  private SignonFailureNotice checkChannelUnregistered(
      final SESSION_IDENTIFIER sessionIdentifier,
      final CHANNEL channel,
      final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > skip
  ) {
    for( final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > sessionDetail :
        sessionDetailMap.values()
    ) {
      if( sessionDetail != skip ) {
        if( channel.equals( sessionDetail.channel ) ) {
          return channelAlreadyRegistered( sessionIdentifier, sessionDetail.channel ) ;
        }
      }
    }
    return null ;
  }



  private void processAllChannels(
      final ChannelProcessor< SESSION_IDENTIFIER, CHANNEL, ADDRESS > processor
  ) {
    final Iterator< SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > > iterator =
        sessionDetailMap.values().iterator() ;
    while( iterator.hasNext() ) {
      final SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > sessionDetail = iterator.next() ;
      final ChannelProcessor.ProcessAction action = processor.process( sessionDetail ) ;
      if( action.deleting ) {
        iterator.remove() ;
      }
      if( action.stopping ) {
        break ;
      }
    }
  }

  private interface ChannelProcessor<
      SESSION_IDENTIFIER extends KeyHolder.Key< SESSION_IDENTIFIER >,
      CHANNEL,
      ADDRESS
  > {

    ProcessAction process(
        SessionDetail< SESSION_IDENTIFIER, CHANNEL, ADDRESS > sessionDetail
    ) ;

    enum ProcessAction {
      CONTINUE( false, false ),
      STOP( false, true ),
      DELETE( true, false ),
      DELETE_AND_STOP( true, true ),
      ;
      final boolean deleting ;
      final boolean stopping ;

      ProcessAction( final boolean deleting, final boolean stopping ) {
        this.deleting = deleting ;
        this.stopping = stopping ;
      }
    }
  }



// ========
// Failures
// ========


  /**
   * @return a non-null {@link SignonFailureNotice} that contains no information that
   *     {@link USER} attempting to sign in should not see.
   */
  public static< SESSION_IDENTIFIER, USER extends SignableUser > SignonFailureNotice
  userSessionAlreadyAttributed(
      final USER user,
      @SuppressWarnings( "unused" ) final SESSION_IDENTIFIER existingSessionIdentifier,
      final SESSION_IDENTIFIER newSessionIdentifier
  ) {
    return new SignonFailureNotice(
        SignonFailure.SESSION_ALREADY_ATTRIBUTED,
        "For user " + user.login() + " there is a session open, " +
            "can't add " + newSessionIdentifier
    ) ;
  }

  /**
   * @return a non-null {@link SignonFailureNotice} that contains no information that
   *     {@link USER} attempting to sign in should not see.
   */
  public static< SESSION_IDENTIFIER, USER extends SignableUser > SignonFailureNotice
  userSessionAlreadyAttributed(
      final USER user,
      final SESSION_IDENTIFIER existingSessionIdentifier
  ) {
    // Don't tell about existing session or the user himself.
    return new SignonFailureNotice(
        SignonFailure.SESSION_ALREADY_ATTRIBUTED,
        "For user " + user.login() + " there is already " + existingSessionIdentifier
    ) ;
  }
  /**
   * @return a non-null {@link SignonFailureNotice} that contains no information that
   *     {@link USER} attempting to sign in should not see.
   */
  public static< USER extends SignableUser > SignonFailureNotice
  channelAlreadySet() {
    return new SignonFailureNotice( SignonFailure.CHANNEL_ALREADY_SET ) ;
  }

  public static< SESSION_IDENTIFIER > SignonFailureNotice
  sessionAlreadyExists( final SESSION_IDENTIFIER sessionIdentifier ) {
    return new SignonFailureNotice(
        SignonFailure.SESSION_ALREADY_EXISTS,
        "Already exists: " + sessionIdentifier
    ) ;
  }


  public static< SESSION_IDENTIFIER, CHANNEL > SignonFailureNotice
  channelAlreadyRegistered(
      final SESSION_IDENTIFIER sessionIdentifier,
      final CHANNEL existingChannel
  ) {
    return new SignonFailureNotice(
        SignonFailure.CHANNEL_ALREADY_REGISTERED,
        "Channel " + existingChannel +
            " already registered for session " + sessionIdentifier + "."
    ) ;
  }

  public static< SESSION_IDENTIFIER, CHANNEL, ADDRESS > SignonFailureNotice
  channelHasDifferentRemoteAddress(
      final SESSION_IDENTIFIER sessionIdentifier,
      final ADDRESS initialRemoteAddress,
      final CHANNEL channel,
      final ADDRESS newRemoteAddress
      ) {
    return new SignonFailureNotice(
        SignonFailure.UNMATCHED_NETWORK_ADDRESS,
        "Session " + sessionIdentifier + " was created with remote address " +
            initialRemoteAddress + " and channel " + channel + " has a non-matching address " +
            newRemoteAddress
    ) ;
  }

  public static< SESSION_IDENTIFIER, ADDRESS > SignonFailureNotice
  sessionHasDifferentRemoteAddress(
      final SESSION_IDENTIFIER sessionIdentifier,
      final ADDRESS initialRemoteAddress,
      final ADDRESS newRemoteAddress
  ) {
    return new SignonFailureNotice(
        SignonFailure.UNMATCHED_NETWORK_ADDRESS,
        "Session " + sessionIdentifier + " was created with remote address " +
            initialRemoteAddress + " which doesn't match with " +
            newRemoteAddress
    ) ;
  }


  public static < SESSION_IDENTIFIER >SignonFailureNotice unknownSession(
      final SESSION_IDENTIFIER sessionIdentifier
  ) {
    return new SignonFailureNotice(
        SignonFailure.UNKNOWN_SESSION, "Unknown session " + sessionIdentifier ) ;
  }

  public static < SESSION_IDENTIFIER, ADDRESS > SignonFailureNotice recentAssociationFailed(
      final SESSION_IDENTIFIER sessionIdentifier,
      final ADDRESS remoteAddress
  ) {
    return new SignonFailureNotice(
        SignonFailure.REMOTE_ADDRESS_BLACKLISTED_AFTER_FAILED_ASSOCIATION,
        "There was a recent attempt to associate to non-existing session " + sessionIdentifier +
            " from " + remoteAddress
    ) ;
  }


}
