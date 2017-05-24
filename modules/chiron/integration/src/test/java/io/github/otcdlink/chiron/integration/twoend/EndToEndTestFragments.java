package io.github.otcdlink.chiron.integration.twoend;

import io.github.otcdlink.chiron.AbstractConnectorFixture;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.downend.DownendConnector;
import io.github.otcdlink.chiron.downend.SignonMaterializer;
import io.github.otcdlink.chiron.fixture.BlockingMonolist;
import io.github.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.toolbox.Credential;
import io.github.otcdlink.chiron.upend.UpendConnector;
import io.github.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import io.github.otcdlink.chiron.upend.session.SessionSupervisor;
import io.netty.channel.Channel;
import mockit.FullVerifications;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.otcdlink.chiron.AbstractConnectorFixture.CREDENTIAL_OK;
import static io.github.otcdlink.chiron.AbstractConnectorFixture.SESSION_IDENTIFIER;
import static io.github.otcdlink.chiron.fixture.TestNameTools.setTestThreadName;

final class EndToEndTestFragments {

  private static final Logger LOGGER = LoggerFactory.getLogger( EndToEndTestFragments.class ) ;

  private EndToEndTestFragments() { }

  public static void simpleAuthenticatedEcho(
      final EndToEndFixture fixture,
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final OutwardSessionSupervisor< Channel, InetAddress > outboundSessionSupervisor
  ) throws Exception {
    authenticate( fixture, signonMaterializer, outboundSessionSupervisor ) ;

    fixture.commandRoundtrip( SESSION_IDENTIFIER ) ;

    terminate( fixture, outboundSessionSupervisor ) ;
  }

  protected static void authenticate(
      final EndToEndFixture fixture,
      final Supplier< DownendConnector.Setup< Command.Tag, EchoDownwardDuty< Command.Tag > > >
          downendSetupSupplier,
      final Function<
          DownendConnector.Setup< Command.Tag, EchoDownwardDuty< Command.Tag > >,
          UpendConnector.Setup<EchoUpwardDuty< Designator > >
      > upendSetupSupplier,
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final OutwardSessionSupervisor< Channel, InetAddress > outboundSessionSupervisor
  ) throws Exception {
    authenticate(
        fixture,
        downendSetupSupplier,
        upendSetupSupplier,
        signonMaterializer,
        outboundSessionSupervisor,
        Ø -> { }
    ) ;
  }

  protected static void authenticate(
      final EndToEndFixture fixture,
      final Supplier< DownendConnector.Setup< Command.Tag, EchoDownwardDuty< Command.Tag > > >
          downendSetupSupplier,
      final Function<
          DownendConnector.Setup< Command.Tag, EchoDownwardDuty< Command.Tag > >,
          UpendConnector.Setup< EchoUpwardDuty< Designator > >
      > upendSetupSupplier,
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final OutwardSessionSupervisor< Channel, InetAddress > outboundSessionSupervisor,
      final Consumer< DownendConnector.Change > changeConsumer
  ) throws Exception {
    setTestThreadName() ;
    fixture.initialize(
        downendSetupSupplier,
        upendSetupSupplier,
        false,
        true
    ) ;
    final BlockingMonolist< Consumer< Credential > > primarySignonCredentialConsumerCapture =
        new BlockingMonolist<>() ;
    final BlockingMonolist< SessionSupervisor.PrimarySignonAttemptCallback >
        primarySignonAttemptCallbackCapture = new BlockingMonolist<>() ;

    new StrictExpectations() {{
      signonMaterializer.readCredential( withCapture( primarySignonCredentialConsumerCapture ) ) ;
      signonMaterializer.setProgressMessage( "Signing in …" ) ;
      signonMaterializer.setProgressMessage( null ) ;
      outboundSessionSupervisor.attemptPrimarySignon(
          CREDENTIAL_OK.getLogin(),
          CREDENTIAL_OK.getPassword(),
          ( Channel ) any,
          ( InetAddress ) any,
          withCapture( primarySignonAttemptCallbackCapture )
      ) ;
      signonMaterializer.done() ;
    }} ;

    final CompletableFuture< ? > startConcluder = fixture.downend().start() ;

    final Consumer< Credential > primarySignonCredentialConsumer =
        primarySignonCredentialConsumerCapture.getOrWait() ;

    primarySignonCredentialConsumer.accept( CREDENTIAL_OK ) ;

    LOGGER.info( "Passed " + Credential.class.getSimpleName() + " to " +
        SessionSupervisor.PrimarySignonAttemptCallback.class.getSimpleName() + "." ) ;

    final SessionSupervisor.PrimarySignonAttemptCallback primarySignonAttemptCallback =
        primarySignonAttemptCallbackCapture.getOrWait() ;

    primarySignonAttemptCallback.sessionAttributed( SESSION_IDENTIFIER ) ;

    fixture.waitForDownendConnectorState( changeConsumer, DownendConnector.State.SIGNED_IN ) ;
    startConcluder.join() ;

    new FullVerifications() {{ }} ;

  }

  protected static void authenticate(
      final EndToEndFixture fixture,
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final OutwardSessionSupervisor< Channel, InetAddress > outboundSessionSupervisor
  ) throws Exception {
    authenticate( fixture, false, signonMaterializer, outboundSessionSupervisor ) ;
  }

  protected static void authenticate(
      final EndToEndFixture fixture,
      final boolean useProxy,
      @Injectable final SignonMaterializer signonMaterializer,
      @Injectable final OutwardSessionSupervisor< Channel, InetAddress > outboundSessionSupervisor
  ) throws Exception {
    setTestThreadName() ;
    authenticate(
        fixture,
        fixture.downendSetup(
            false,
            useProxy,
            AbstractConnectorFixture.PingTimingPresets.NO_PING,
            signonMaterializer,
            null
        ),
        fixture.websocketAuthenticatedUpendSetup( outboundSessionSupervisor ),
        signonMaterializer,
        outboundSessionSupervisor
    ) ;

  }

  public static void terminate(
      final EndToEndFixture fixture,
      final OutwardSessionSupervisor< Channel, InetAddress > outwardSessionSupervisor
  ) throws InterruptedException {
    if( outwardSessionSupervisor != null ) {
      new StrictExpectations() {{
        outwardSessionSupervisor.closed( ( Channel ) any, ( SessionIdentifier ) any, false ) ;
        // Sometimes the call above doesn't happen, this seems to depend on ping timing sequence.
        minTimes = 0 ;
      }} ;
    }
    fixture.downend().stop() ;
    fixture.waitForDownendConnectorState( DownendConnector.State.STOPPED ) ;
  }
}
