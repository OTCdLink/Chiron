package com.otcdlink.chiron.downend;

import com.otcdlink.chiron.AbstractConnectorFixture;
import com.otcdlink.chiron.ConnectorChangeAssert;
import com.otcdlink.chiron.ExtendedChange;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.fixture.TestNameTools;
import com.otcdlink.chiron.integration.echo.DownwardEchoCommand;
import com.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.FullVerificationsInOrder;
import mockit.Injectable;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.otcdlink.chiron.AbstractConnectorFixture.PingTimingPresets.NO_PING;
import static com.otcdlink.chiron.AbstractConnectorFixture.PingTimingPresets.PING_TIMEOUT;
import static com.otcdlink.chiron.AbstractConnectorFixture.TAG_1;
import static com.otcdlink.chiron.AbstractConnectorFixture.downwardEchoCommand;
import static com.otcdlink.chiron.AbstractConnectorFixture.upwardEchoCommand;


@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class CommandTransceiverTest
    extends AbstractDownendTest<
        Tracker,
        CommandTransceiver< EchoDownwardDuty< Tracker >, EchoUpwardDuty< Tracker >>,
        CommandTransceiver.Setup< EchoDownwardDuty< Tracker > >
    >
{


  @Test( timeout = TIMEOUT_MS )
  public void simpleEchoWithImmediateReconnection(
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final Tracker tracker
  ) throws Exception {
    TestNameTools.setTestThreadName() ;
     initializeAndSignon( fixture, signonMaterializer ) ;

    new Expectations() {{
      tracker.afterResponseHandled() ;
    }} ;

    fixture.commandRoundtrip( tracker ) ;

    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() )
        .isInFlightStatusStateChange().is( CommandInFlightStatus.IN_FLIGHT ) ;
    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() )
        .isInFlightStatusStateChange().is( CommandInFlightStatus.QUIET ) ;

    new FullVerifications() {{ }} ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void simpleEcho(
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final Tracker tracker
  ) throws Exception {
    TestNameTools.setTestThreadName() ;
     initializeAndSignon(
         fixture,
         AbstractConnectorFixture.PingTimingPresets.QUIET,
         signonMaterializer
     ) ;
    new Expectations() {{
      tracker.afterResponseHandled() ;
    }} ;

    fixture.commandRoundtrip( tracker ) ;

    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() )
        .isInFlightStatusStateChange().is( CommandInFlightStatus.IN_FLIGHT ) ;
    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() )
        .isInFlightStatusStateChange().is( CommandInFlightStatus.QUIET ) ;

    new FullVerifications() {{ }} ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void noTracking(
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final Tracker tracker
  ) throws Exception {
    TestNameTools.setTestThreadName() ;
//      fixture.initializeNoSignonAndStartAll() ;
     initializeAndSignon( fixture, signonMaterializer ) ;

    fixture.commandRoundtrip( tracker, false ) ;

    new FullVerificationsInOrder() {{ }} ;
  }


  @Test( timeout = TIMEOUT_MS )
  public void trackerSegregation(
      @Injectable final Tracker tracker0,
      @Injectable final Tracker tracker1
  ) throws Exception {
    TestNameTools.setTestThreadName() ;
      fixture.initializeNoSignonAndStartAll( AbstractConnectorFixture.PingTimingPresets.QUIET ) ;

    new Expectations() {{
      tracker1.afterResponseHandled() ;
    }} ;

    final UpwardEchoCommand< Tracker > upward0 = upwardEchoCommand( tracker0, "Zero" ) ;
    final UpwardEchoCommand< Tracker > upward1 = upwardEchoCommand( tracker1, "One" ) ;
    final DownwardEchoCommand< Command.Tag > downward1 = downwardEchoCommand( TAG_1, "Hello One" ) ;

    fixture.commandToTextWebsocketGuide().recordNoResponse() ;
    fixture.commandToTextWebsocketGuide().record( downward1 ) ;

    LOGGER.info( "Recorded responses, now sending Upward " +
        Command.class.getSimpleName() + "s ..." ) ;

    fixture.downend().send( upward0 ) ;
    fixture.downend().send( upward1 ) ;

    LOGGER.info( "Sent Upward " + Command.class.getSimpleName() + "s, " +
        "now waiting for response ..." ) ;

    fixture.checkDequeuedDownwardCommandEquivalentTo(
        downwardEchoCommand( tracker1, "Hello One" ) ) ;

    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() )
        .isInFlightStatusStateChange().is( CommandInFlightStatus.IN_FLIGHT ) ;

    new FullVerifications() {{ }} ;
  }

  /**
   * No pong should cause a reconnection.
   */
  @Test( timeout = TIMEOUT_MS )
  public void connectionLostAndRestored( @Injectable final Tracker tracker ) throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( PING_TIMEOUT ) ;

    fixture.commandToTextWebsocketGuide().recordNoResponse() ;
    fixture.downend().send( upwardEchoCommand( tracker ) ) ;

    new Expectations() {{
      tracker.onConnectionLost() ;
      tracker.onConnectionRestored() ;
    }} ;

    fixture.waitForMatch( change -> isInFlightStatus( change, CommandInFlightStatus.IN_FLIGHT ) ) ;

    LOGGER.info( "Command sent, now causing a disconnection by disabling pong ..." ) ;

    fixture.pingPongGuide().pongEnabled( false ) ;

    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() ).hasKind( DownendConnector.State.CONNECTING ) ;

    LOGGER.info( "Reconnection is now in progress, enabling pong again ..." ) ;

    fixture.pingPongGuide().pongEnabled( true ) ;

    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() )
        .isInFlightStatusStateChange().is( CommandInFlightStatus.QUIET ) ;

    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() ).hasKind( DownendConnector.State.CONNECTED ) ;

    LOGGER.info( "Remaining " + DownendConnector.Change.class.getSimpleName() + "s: " +
        fixture.drainDownendChanges() + "." ) ;

    new FullVerifications() {{ }} ;

  }


  @Test( timeout = TIMEOUT_MS )
  public void trackerTimeout( @Injectable final Tracker tracker ) throws Exception {
    TestNameTools.setTestThreadName() ;
    fixture.initializeNoSignonAndStartAll( NO_PING ) ;
    final CommandTransceiver.ScavengeActivator.Voluntary scavengeActivator =
        CommandTransceiver.ScavengeActivator.voluntary( fixture.downend() ) ;

    fixture.commandToTextWebsocketGuide().recordNoResponse() ;
    fixture.downend().send( upwardEchoCommand( tracker ) ) ;

    new Expectations() {{
      tracker.afterTimeout() ;
    }} ;

    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() )
        .isInFlightStatusStateChange().is( CommandInFlightStatus.IN_FLIGHT ) ;

    LOGGER.info( "Command sent, now scavenging " + Tracker.class.getSimpleName() +
        "s ..." ) ;

    clock.increment( fixture.downendSetup().primingTimeBoundary.pongTimeoutMs() + 1 ) ;
    scavengeActivator.scavengeNow() ;

    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() )
        .isInFlightStatusStateChange().is( CommandInFlightStatus.SOME_COMMAND_FAILED ) ;

    ConnectorChangeAssert.assertThat( fixture.nextDownendChange() )
        .isInFlightStatusStateChange().is( CommandInFlightStatus.QUIET ) ;


    new FullVerifications() {{ }} ;

//    fixture.downend().stop().sync() ;
//    fixture.checkNoMoreDownendChange() ;
  }



// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( CommandTransceiverTest.class ) ;

  private final UpdateableClock clock ;

  public CommandTransceiverTest() {
    super( DownendFixture.newCommandTransceiverFixture(
        UpdateableClock.newClock( Stamp.FLOOR_MILLISECONDS ) ) ) ;
    clock = ( ( DownendFixture.CommandTransceiverFixture ) fixture ).clock ;
  }

  private static boolean isInFlightStatus(
      final DownendConnector.Change change,
      final CommandInFlightStatus commandInFlightStatus
  ) {
    return change instanceof ExtendedChange.CommandInFlightStatusChange &&
        ( ( ExtendedChange.CommandInFlightStatusChange ) change ).commandInFlightStatus ==
            commandInFlightStatus
    ;
  }


  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.info( "=== Netty classes loaded, tests begin here ===" ) ;
  }


}