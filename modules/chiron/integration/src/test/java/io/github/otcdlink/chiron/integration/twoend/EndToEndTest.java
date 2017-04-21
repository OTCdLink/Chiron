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
import io.github.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import io.github.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import io.netty.channel.Channel;
import mockit.FullVerificationsInOrder;
import mockit.Injectable;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

import static io.github.otcdlink.chiron.fixture.TestNameTools.setTestThreadName;

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


// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER =
      LoggerFactory.getLogger( DownendConnectorTest.class ) ;

  //    private static final long TIMEOUT_MS = 5_000 ;
  private static final long TIMEOUT_MS = 1_000_000 ;

  private final EndToEndFixture fixture = new EndToEndFixture() ;


  @After
  public void tearDown() throws Exception {
    fixture.stopAll() ;
  }




}
