package com.otcdlink.chiron.integration.twoend;

import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.downend.DownendConnectorTest;
import com.otcdlink.chiron.downend.SignonMaterializer;
import com.otcdlink.chiron.fixture.BlockingMonolist;
import com.otcdlink.chiron.fixture.tcp.http.ConnectProxy;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import com.otcdlink.chiron.upend.session.SessionSupervisor;
import io.netty.channel.Channel;
import mockit.FullVerifications;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

import static com.otcdlink.chiron.AbstractConnectorFixture.SESSION_IDENTIFIER;

/**
 * More tests, using {@link ConnectProxy}.
 */
@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class EndToEndPlugTest {

  /**
   * Test health: should work as if there was no {@link ConnectProxy}.
   */
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
  public void simpleReconnect(
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final OutwardSessionSupervisor< Channel, InetAddress >
          outboundSessionSupervisor
  ) throws Exception {
    EndToEndTestFragments.authenticate(
        fixture, true, signonMaterializer, outboundSessionSupervisor ) ;

    final BlockingMonolist<SessionSupervisor.ReuseCallback> reuseCallbackCapture =
        new BlockingMonolist<>() ;
    new StrictExpectations() {{
      outboundSessionSupervisor.closed( ( Channel ) any, SESSION_IDENTIFIER, false ) ;
      outboundSessionSupervisor.tryReuse(
          SESSION_IDENTIFIER,
          ( Channel ) any,
          withCapture( reuseCallbackCapture )
      ) ;
    }} ;

    fixture.restartHttpProxy() ;

    final SessionSupervisor.ReuseCallback reuseCallback = reuseCallbackCapture.getOrWait() ;
    LOGGER.info( "Got " + reuseCallback ) ;
    new FullVerifications() {{ }} ;

    reuseCallback.reuseOutcome( null ) ;

    while( true ) {
      final DownendConnector.Change change = fixture.nextDownendChange() ;
      LOGGER.debug( "Obtained " + change + " while actively waiting for " +
          DownendConnector.State.SIGNED_IN + " ..." ) ;
      if( change.kind == DownendConnector.State.SIGNED_IN ) {
        break ;
      }
    }
    fixture.commandRoundtrip( SESSION_IDENTIFIER ) ;
    EndToEndTestFragments.terminate( fixture, outboundSessionSupervisor ) ;
  }


  @Test( timeout = TIMEOUT_MS )
  public void pongTimeout(
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final OutwardSessionSupervisor< Channel, InetAddress >
          outboundSessionSupervisor
  ) throws Exception {
    EndToEndTestFragments.authenticate(
        fixture,
        fixture.downendSetup(
//            new TimeBoundary( 1000, 2000, 1000, 2000, 3000 ),
            TimeBoundary.Builder.createNew()
                .pingInterval( 1000 )
                .pongTimeoutOnDownend( 2500 )
                .reconnectDelay( 1000, 2000 )
                .pingTimeoutOnUpend( 3000 )
                .sessionInactivityForever()
                .build()
            ,
            signonMaterializer
        ),
        fixture.websocketAuthenticatedUpendSetup( outboundSessionSupervisor ),
        signonMaterializer,
        outboundSessionSupervisor
    ) ;


    fixture.commandRoundtrip( SESSION_IDENTIFIER ) ;
  }


// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER =
      LoggerFactory.getLogger( DownendConnectorTest.class ) ;

  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.debug( "====== Ready to run tests ======" ) ;
  }


      private static final long TIMEOUT_MS = 5_000 ;
//  private static final long TIMEOUT_MS = 1_000_000 ;

  private final EndToEndFixture fixture = new EndToEndFixture() ;

  @After
  public void tearDown() throws Exception {
    fixture.stopAll() ;
  }
}
