package com.otcdlink.chiron.downend;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.middle.CommandFailureNotice;
import com.otcdlink.chiron.middle.TechnicalFailureNotice;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import mockit.Injectable;
import mockit.Verifications;
import mockit.VerificationsInOrder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class TrackerCuratorTest {

  @Test
  public void addAndRemove(
      @Injectable final TrackerCurator.Claim claim,
      @Injectable final Tracker tracker
  ) throws Exception {
    final TrackerCurator trackerCurator = new TrackerCurator( claim, updateableClock ) ;
    trackerCurator.trackerLifetimeMs( 2 ) ;
    final Command.Tag tag = trackerCurator.add( tracker ) ;
    final Tracker got = trackerCurator.get( tag ) ;
    assertThat( got ).describedAs( "Got a TrackerEnhancer" ).isNotSameAs( tracker ) ;
    assertThat( trackerCurator.get( tag ) )
        .describedAs( "May get several times as long as magic removal didn't happen" )
        .isSameAs( got )
    ;
    got.afterResponseHandled() ;
    new Verifications() { {
      claim.commandStatusChanged( CommandInFlightStatus.IN_FLIGHT ) ;
      claim.commandStatusChanged( CommandInFlightStatus.QUIET ) ;
    } } ;
    assertThat( trackerCurator.get( tag ) ).isNull() ;
  }

  @Test
  public void addAndRemoveWithError(
      @Injectable final TrackerCurator.Claim claim,
      @Injectable final Tracker tracker
  ) throws Exception {
    final TrackerCurator trackerCurator = new TrackerCurator( claim, updateableClock ) ;
    trackerCurator.trackerLifetimeMs( 2 ) ;
    final Command.Tag tag = trackerCurator.add( tracker ) ;
    final Tracker got = trackerCurator.get( tag ) ;
    got.afterRemoteFailure( FAILURE_NOTICE ) ;
    new VerificationsInOrder() { {
      claim.commandStatusChanged( CommandInFlightStatus.IN_FLIGHT ) ;
      claim.commandStatusChanged( CommandInFlightStatus.SOME_COMMAND_FAILED ) ;
      claim.commandStatusChanged( CommandInFlightStatus.QUIET ) ;
    } } ;
    assertThat( trackerCurator.get( tag ) ).isNull() ;
  }

  @Test
  public void disconnectAndReconnect(
      @Injectable final TrackerCurator.Claim claim,
      @Injectable final Tracker tracker1,
      @Injectable final Tracker tracker2
  ) throws Exception {
    final TrackerCurator trackerCurator = new TrackerCurator( claim, updateableClock ) ;
    trackerCurator.trackerLifetimeMs( 2 ) ;
    final Command.Tag tag1 = trackerCurator.add( tracker1 ) ;
    final Command.Tag tag2 = trackerCurator.add( tracker2 ) ;
    assertThat( tag1 ).isNotEqualTo( tag2 ) ;

    trackerCurator.notifyReconnection() ;
    trackerCurator.notifyConnectionBroken() ;
    trackerCurator.notifyReconnection() ;

    new Verifications() { {
      claim.commandStatusChanged( CommandInFlightStatus.IN_FLIGHT ) ;
      tracker1.onConnectionLost() ;
      tracker2.onConnectionLost() ;
    } } ;
    new Verifications() { {
      tracker1.onConnectionRestored() ;
      tracker1.onConnectionRestored() ;
    } } ;

    assertThat( trackerCurator.get( tag1 ) ).describedAs( "No removal should occur" ).isNotNull() ;
    assertThat( trackerCurator.get( tag2 ) ).describedAs( "No removal should occur" ).isNotNull() ;

  }

  @Test
  public void scavenge(
      @Injectable final TrackerCurator.Claim claim,
      @Injectable final Tracker tracker
  ) throws Exception {
    final TrackerCurator trackerCurator = new TrackerCurator( claim, updateableClock ) ;
    trackerCurator.trackerLifetimeMs( 2 ) ;
    final Command.Tag tag = trackerCurator.add( tracker ) ;
    new VerificationsInOrder() { {
      claim.commandStatusChanged( CommandInFlightStatus.IN_FLIGHT ) ;
    } } ;
    updateableClock.set( 3 ) ;
    trackerCurator.notifyReconnection() ;
    trackerCurator.scavengeTimeouts() ;
    assertThat( trackerCurator.get( tag ) ).isNull() ;

    new VerificationsInOrder() { {
      claim.commandStatusChanged( CommandInFlightStatus.SOME_COMMAND_FAILED ) ;
      claim.commandStatusChanged( CommandInFlightStatus.QUIET ) ;
      tracker.afterTimeout() ;
    } } ;

  }

  @Test
  public void ensureLifetimeSet(
      @Injectable final TrackerCurator.Claim claim,
      @Injectable final Tracker tracker
  ) throws Exception {
    final TrackerCurator trackerCurator = new TrackerCurator( claim, updateableClock ) ;
    assertThatThrownBy( () -> trackerCurator.add( tracker ) )
        .isInstanceOf( IllegalStateException.class ) ;
  }

  @Test
  public void doNotScavengeWhenDisconnected(
      @Injectable final TrackerCurator.Claim claim,
      @Injectable final Tracker tracker
  ) throws Exception {
    final TrackerCurator trackerCurator = new TrackerCurator( claim, updateableClock ) ;
    trackerCurator.trackerLifetimeMs( 2 ) ;
    final Command.Tag tag = trackerCurator.add( tracker ) ;
    new VerificationsInOrder() { {
      claim.commandStatusChanged( CommandInFlightStatus.IN_FLIGHT ) ;
    } } ;
    trackerCurator.notifyReconnection() ;
    trackerCurator.notifyConnectionBroken() ;
    updateableClock.set( 3 ) ;
    trackerCurator.scavengeTimeouts() ;
    trackerCurator.notifyReconnection() ;
    assertThat( trackerCurator.get( tag ) ).isNotNull() ;

    new VerificationsInOrder() { {
      claim.commandStatusChanged( CommandInFlightStatus.QUIET ) ;
      tracker.onConnectionRestored() ;
    } } ;

  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( TrackerCuratorTest.class ) ;

  private final UpdateableClock updateableClock = UpdateableClock.newClock( 0 ) ;

  private static final CommandFailureNotice FAILURE_NOTICE = new TechnicalFailureNotice(
      TechnicalFailureNotice.Kind.SERVER_ERROR, "Some error" ) ;

}