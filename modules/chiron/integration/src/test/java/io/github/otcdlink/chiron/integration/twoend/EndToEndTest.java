package io.github.otcdlink.chiron.integration.twoend;

import io.github.otcdlink.chiron.AbstractConnectorFixture;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.downend.DownendConnector;
import io.github.otcdlink.chiron.downend.DownendConnectorTest;
import io.github.otcdlink.chiron.downend.SignonMaterializer;
import io.github.otcdlink.chiron.integration.echo.DownwardEchoCommand;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import io.github.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import io.github.otcdlink.chiron.middle.CommandAssert;
import io.github.otcdlink.chiron.middle.tier.CommandInterceptor;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import io.github.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import io.github.otcdlink.chiron.toolbox.ObjectTools;
import io.github.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import io.netty.channel.Channel;
import io.netty.util.ResourceLeakDetector;
import mockit.FullVerificationsInOrder;
import mockit.Injectable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

import static io.github.otcdlink.chiron.downend.DownendConnector.State.CONNECTING;
import static io.github.otcdlink.chiron.fixture.TestNameTools.setTestThreadName;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * To sniff local traffic on Mavericks:
 * sudo tcpdump -i lo0 -vv -w ~/Desktop/OTCdLink/VirtualBox/Capture/localhost.pcap -n "tcp port 10001"
 * Use Wireshark in a Windows VirtualBox Guest for viewing.
 */
@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class EndToEndTest {

  @Test( timeout = TIMEOUT_MS )
  public void simpleUnauthenticatedEcho() throws Exception {
    setTestThreadName() ;
    fixture.initialize(
        fixture.downendSetup( AbstractConnectorFixture.PingTimingPresets.NO_PING ),
        fixture::websocketUnauthenticatedUpendSetup
    ) ;

    fixture.commandRoundtrip( fixture.dummySessionIdentifier() ) ;
  }

  @Test( timeout = TIMEOUT_MS )
  public void unauthenticatedEchoWithFrameAggregation() throws Exception {
    setTestThreadName() ;
    fixture.initialize(
        fixture.downendSetup( WebsocketFrameSizer.explicitSizer( 1, 20 ) ),
        fixture::websocketUnauthenticatedUpendSetup
    ) ;
    fixture.commandRoundtrip( fixture.dummySessionIdentifier() ) ;
  }


  @Test( timeout = TIMEOUT_MS )
  public void simpleAuthenticatedEcho(
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final OutwardSessionSupervisor< Channel, InetAddress >
          outboundSessionSupervisor
  ) throws Exception {
    EndToEndTestFragments.simpleAuthenticatedEcho(
        fixture, signonMaterializer, outboundSessionSupervisor ) ;
  }


  @Test( timeout = TIMEOUT_MS )
  public void commandInterceptor(
      @Injectable final CommandConsumer< Command< Designator, EchoUpwardDuty< Designator > > >
          upendCommandConsumer
  ) throws Exception {
    setTestThreadName() ;
    fixture.initialize(
        fixture.downendSetup( AbstractConnectorFixture.PingTimingPresets.NO_PING ),
        fixture.websocketUnauthenticatedUpendSetup(
            upendCommandConsumer,
            new CommandInterceptor() {
              @Override
              public boolean interceptUpward( final Command command, final Sink sink ) {
                LOGGER.info( "Intercepted " + command + ", now deciding ..." ) ;
                if( command instanceof UpwardEchoCommand ) {
                  final UpwardEchoCommand echoCommand = ( UpwardEchoCommand ) command ;
                  if( echoCommand.message.startsWith( "intercept-me" ) ) {
                    final DownwardEchoCommand downwardEchoCommand = new DownwardEchoCommand<>(
                        echoCommand.endpointSpecific, "intercepted!" ) ;
                    sink.sendBackward( downwardEchoCommand ) ;
                    LOGGER.info( "Sent backward: " + downwardEchoCommand + "." ) ;
                    return true ;
                  }
                }
                LOGGER.info( "Doing nothing with: " + command + " (returning false)." ) ;
                return false ;
              }
            }
        ),
        false,
        true
    ) ;

    fixture.downend().start().join() ;

    fixture.downend().send( AbstractConnectorFixture.upwardEchoCommand(
        AbstractConnectorFixture.TAG_0, "intercept-me" ) ) ;

    CommandAssert.assertThat( fixture.dequeueDownwardCommandReceived() ).isEquivalentTo(
        AbstractConnectorFixture.downwardEchoCommand(
            AbstractConnectorFixture.TAG_0, "intercepted!" ) )
    ;

    fixture.stopAll() ;
    fixture.waitForDownendConnectorState( DownendConnector.State.STOPPED ) ;

    new FullVerificationsInOrder() {{
      /** Our {@link CommandConsumer} should have seen no {@link Command}, was intercepted. */
    }} ;

  }

  @Test( timeout = TIMEOUT_MS )
  public void newTimeBoundary() throws Exception {
    setTestThreadName() ;

    final TimeBoundary.ForAll initialTimeBoundary = TimeBoundary.Builder.createNew()
        .pingInterval( 888_888_888 )
        .pongTimeoutOnDownend( 2500 )
        .reconnectDelay( 1000, 2000 )
        .pingTimeoutNever()
        .sessionInactivityForever()
        .build()
    ;
    final TimeBoundary.ForAll newTimeBoundary = TimeBoundary.Builder.createNew()
        .pingInterval( 999_999_999 )
        .pongTimeoutOnDownend( 2500 )
        .reconnectDelay( 1000, 2000 )
        .pingTimeoutNever()
        .sessionInactivityForever()
        .build()
    ;

    fixture.initialize(
        fixture.downendSetup( initialTimeBoundary ),
        fixture::websocketUnauthenticatedUpendSetup,
        false,
        true
    ) ;

    fixture.downend().start() ;

    waitForConnectionDescriptor( initialTimeBoundary ) ;

    fixture.commandRoundtrip( fixture.dummySessionIdentifier() ) ;

    LOGGER.info( "Setting new " + TimeBoundary.class.getSimpleName() + " and restarting ..." ) ;
    fixture.upendConnector().timeBoundary( newTimeBoundary ) ;
    fixture.upendConnector().stop().join() ;

    fixture.waitForDownendConnectorState( CONNECTING ) ;

    fixture.upendConnector().start().join() ;
    waitForConnectionDescriptor( newTimeBoundary ) ;

  }



// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER =
      LoggerFactory.getLogger( DownendConnectorTest.class ) ;

  //    private static final long TIMEOUT_MS = 5_000 ;
  private static final long TIMEOUT_MS = 1_000_000 ;

  private static final ResourceLeakDetector.Level INITIAL_RESOURCELEAKDETECTOR_LEVEL =
      ResourceLeakDetector.getLevel() ;


  private final EndToEndFixture fixture = new EndToEndFixture() ;

  @Before
  public void setUp() throws Exception {
    ResourceLeakDetector.setLevel( ResourceLeakDetector.Level.PARANOID ) ;
  }

  @After
  public void tearDown() throws Exception {
    fixture.stopAll() ;
    System.gc() ;
    ResourceLeakDetector.setLevel( INITIAL_RESOURCELEAKDETECTOR_LEVEL ) ;

  }


  private void waitForConnectionDescriptor( TimeBoundary.ForAll timeBoundary )
      throws InterruptedException
  {
    LOGGER.info( "Waiting for " + DownendConnector.class.getSimpleName() +
        " to receive expected " + TimeBoundary.class.getSimpleName() + " ..." ) ;

    final ObjectTools.Holder< DownendConnector.Change > connectionDescriptorHolder =
        ObjectTools.newHolder() ;

    fixture.waitForMatch( change -> {
      if( change.kind == DownendConnector.State.CONNECTED ) {
        connectionDescriptorHolder.set( change ) ;
        return true ;
      } else {
        return false ;
      }
    } ) ;

    final DownendConnector.Change.SuccessfulConnection successfulConnection =
        ( DownendConnector.Change.SuccessfulConnection ) connectionDescriptorHolder.get() ;
    LOGGER.info( "Obtained: " + successfulConnection ) ;
    assertThat( successfulConnection.connectionDescriptor.timeBoundary ).isEqualTo( timeBoundary ) ;
  }


}
