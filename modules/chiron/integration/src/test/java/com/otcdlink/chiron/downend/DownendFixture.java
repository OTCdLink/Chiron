package com.otcdlink.chiron.downend;

import com.otcdlink.chiron.AbstractConnectorFixture;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Command.Tag;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.conductor.Conductor;
import com.otcdlink.chiron.conductor.ConductorTools;
import com.otcdlink.chiron.conductor.ConductorTools.CommandToTextwebsocketframeConductor;
import com.otcdlink.chiron.conductor.Responder;
import com.otcdlink.chiron.downend.babyupend.BabyUpend;
import com.otcdlink.chiron.integration.echo.EchoCodecFixture;
import com.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.integration.twoend.EndToEndFixture;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import com.otcdlink.chiron.toolbox.CollectingException;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import com.otcdlink.chiron.toolbox.internet.InternetProxyAccess;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * To test a {@link DownendConnector} against a {@link BabyUpend}.
 */
public abstract class DownendFixture<
    ENDPOINT_SPECIFIC,
    DOWNEND extends Downend< ENDPOINT_SPECIFIC, EchoUpwardDuty< ENDPOINT_SPECIFIC >>,
    SETUP extends DownendConnector.Setup< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > >
>
    extends AbstractConnectorFixture< ENDPOINT_SPECIFIC, DOWNEND, SETUP >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( DownendFixture.class ) ;

  private ConnectionDescriptor connectionDescriptor = null ;

  private BabyUpend babyUpend = null ;

  protected DownendFixture( final boolean usePortForwarder ) {
    super() ;
  }

  // =========
// Factories
// =========

  public static DownendFixture<
      Command.Tag,
      DownendConnector<Command.Tag, EchoDownwardDuty< Tag >, EchoUpwardDuty<Command.Tag> >,
      DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > >
  > newDownendConnectorFixture() {
    return newDownendConnectorFixture( false ) ;
  }

  public static DownendFixture<
      Command.Tag,
      DownendConnector<Command.Tag, EchoDownwardDuty<Command.Tag>, EchoUpwardDuty<Command.Tag> >,
      DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > >
  > newDownendConnectorFixture( final boolean usePortForwarder ) {
    return new DownendFixture<
        Command.Tag,
        DownendConnector<Command.Tag, EchoDownwardDuty<Command.Tag>, EchoUpwardDuty<Command.Tag> >,
        DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > >
    >( usePortForwarder ) {
      @Override
      protected DownendConnector<Command.Tag, EchoDownwardDuty<Command.Tag>, EchoUpwardDuty<Command.Tag> >
      createConnector( final DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > > setup ) {
        return new DownendConnector<>( setup ) ;
      }

      @Override
      protected DownendConnector.Setup< Tag, EchoDownwardDuty< Tag > > newSetup(
          final TimeBoundary.ForAll timeBoundary,
          final SignonMaterializer signonMaterializer,
          final CommandTransceiver.ChangeWatcher stateWatcher,
          final InternetProxyAccess internetProxyAccess,
          final SslEngineFactory.ForClient sslEngineFactory,
          final URL websocketUrl,
          final CommandConsumer< Command< Tag, EchoDownwardDuty< Tag > > > commandReceiver,
          final CommandInterceptor commandInterceptor,
          final WebsocketFrameSizer websocketFrameSizer
      ) {
        return new DownendConnector.Setup<>(
            eventLoopGroup,
            websocketUrl,
            internetProxyAccess,
            sslEngineFactory,
            timeBoundary,
            signonMaterializer,
            stateWatcher,
            new EchoCodecFixture.TagCodec(),
            new EchoCodecFixture.PartialDownendDecoder<>(),
            commandReceiver,
            CommandInterceptor.Factory.always( commandInterceptor ),
            websocketFrameSizer
        ) ;
      }
    } ;
  }


  public static abstract class CommandTransceiverFixture extends DownendFixture<
      Tracker,
      CommandTransceiver< EchoDownwardDuty< Tracker >, EchoUpwardDuty< Tracker > >,
      CommandTransceiver.Setup< EchoDownwardDuty< Tracker > >
  > {
    public final UpdateableClock clock ;

    protected CommandTransceiverFixture( final UpdateableClock clock ) {
      super( false );
      this.clock = checkNotNull( clock ) ;
    }
  }

  public static DownendFixture<
      Tracker,
      CommandTransceiver< EchoDownwardDuty< Tracker >, EchoUpwardDuty< Tracker > >,
      CommandTransceiver.Setup< EchoDownwardDuty< Tracker > >
  > newCommandTransceiverFixture( final UpdateableClock clock ) {
    return new CommandTransceiverFixture( clock ) {
      @Override
      protected CommandTransceiver< EchoDownwardDuty< Tracker >, EchoUpwardDuty< Tracker > >
      createConnector( final CommandTransceiver.Setup< EchoDownwardDuty< Tracker > > setup ) {
        return new CommandTransceiver<>( setup ) ;
      }

      @Override
      protected CommandTransceiver.Setup< EchoDownwardDuty< Tracker > > newSetup(
          final TimeBoundary.ForAll timeBoundary,
          final SignonMaterializer signonMaterializer,
          final CommandTransceiver.ChangeWatcher stateWatcher,
          final InternetProxyAccess internetProxyAccess,
          final SslEngineFactory.ForClient sslEngineFactory,
          final URL websocketUrl,
          final CommandConsumer< Command< Tracker, EchoDownwardDuty< Tracker > > > commandReceiver,
          final CommandInterceptor commandInterceptor,
          final WebsocketFrameSizer websocketFrameSizer
      ) {
        return new CommandTransceiver.Setup<>(
            clock,
            eventLoopGroup,
            websocketUrl,
            internetProxyAccess,
            sslEngineFactory,
            timeBoundary,
            signonMaterializer,
            stateWatcher,
            new EchoCodecFixture.PartialDownendDecoder<>(),
            commandReceiver,
            CommandInterceptor.Factory.always( commandInterceptor ),
            EndToEndFixture.WEBSOCKET_FRAME_SIZER
        ) ;
      }
    } ;
  }


// =========
// Lifecycle
// =========


  private static ConnectionDescriptor connectionDescriptor(
      final TimeBoundary.ForAll timeBoundary,
      final boolean authenticate
  ) {
    return new ConnectionDescriptor( "NoVersion", authenticate, timeBoundary ) ;
  }

  public void initializeNoSignonAndStartAll() throws Exception {
    initializeNoSignonAndStartAll( TimeBoundary.DEFAULT ) ;
  }

  public void initializeNoSignonAndStartAll( final TimeBoundary.ForAll timeBoundary ) throws Exception {
    initializeNoSignonAndStartAll( downendSetup( timeBoundary ) ) ;
  }

  public void initializeNoSignonAndStartAll(
      final Supplier< SETUP > setupSupplier
  ) throws Exception {
    initialize(
        setupSupplier,
        true,
        true,
        false
    ) ;
  }

  public void initializeNoSignon(
      final Supplier< SETUP > setupSupplier,
      final boolean startDownendConnector,
      final boolean startBabyUpend
  ) throws Exception {
    initialize(
        setupSupplier,
        startDownendConnector,
        startBabyUpend,
        false
    ) ;
  }

  public void initialize(
      final SignonMaterializer signonMaterializer,
      final TimeBoundary.ForAll timeBoundary
  ) throws Exception {
    initialize(
        downendSetup( false, false, timeBoundary, signonMaterializer, null ),
        false,
        true,
        true
    ) ;
  }


  public void initialize(
      final Supplier< SETUP > setupSupplier,
      final boolean startDownendConnector,
      final boolean startBabyUpend,
      final boolean sessionLifecyclePhaseEnabled
  ) throws Exception {


    final ConductorTools.PingConductor
        pingResponderGuide = new ConductorTools.PingConductor() ;
    final Responder< PingWebSocketFrame, PongWebSocketFrame > safePingResponder =
        pingResponderGuide.responder() ;
    final ConductorTools.PingConductor.PingGuide safePingGuide =
        pingResponderGuide.guide() ;

    final Responder< TextWebSocketFrame, TextWebSocketFrame > safeTextFrameResponder ;
    final CommandToTextwebsocketframeConductor.CommandGuide safeCommandGuide ;

    final CommandToTextwebsocketframeConductor commandResponderGuide =
        new CommandToTextwebsocketframeConductor() ;
    safeCommandGuide = commandResponderGuide.guide() ;

    final ConductorTools.SessionLifecyclePhaseConductor.PhaseGuide safePhaseGuide ;
    if( sessionLifecyclePhaseEnabled ) {
      final ConductorTools.SessionLifecyclePhaseConductor phaseResponderGuide =
          new ConductorTools.SessionLifecyclePhaseConductor(
              sessionLifecyclePhaseEnabled )
      ;
      safePhaseGuide = phaseResponderGuide.guide() ;
      safeTextFrameResponder = new Conductor.CompositeResponder<>(
          phaseResponderGuide.responder(),
          commandResponderGuide.responder()
      ) ;
    } else {
      safePhaseGuide = null ;
      safeTextFrameResponder = commandResponderGuide.responder() ;
    }

    final ConductorTools.CloseFrameConductor
        closeFrameResponderGuide = new ConductorTools.CloseFrameConductor() ;
    final Responder< CloseWebSocketFrame, Void > safeCloseFrameResponder =
        closeFrameResponderGuide.responder() ;
    final ConductorTools.CloseFrameConductor.CloseFrameGuide safeCloseFrameGuide =
        closeFrameResponderGuide.guide() ;


    initialize(
        setupSupplier,
        startDownendConnector,
        startBabyUpend,
        connectionDescriptor(
            ( TimeBoundary.ForAll ) setupSupplier.get().primingTimeBoundary,
            sessionLifecyclePhaseEnabled
        ),
        safePingGuide,
        safePhaseGuide,
        safeCommandGuide,
        safeCloseFrameGuide,
        safeTextFrameResponder,
        safePingResponder,
        safeCloseFrameResponder
    ) ;
  }

  private void initialize(
      final Supplier< SETUP > setupSupplier,
      final boolean startDownendConnector,
      final boolean startBabyUpend,
      final ConnectionDescriptor connectionDescriptor,
      final ConductorTools.PingConductor.PingGuide pingGuide,
      final ConductorTools.SessionLifecyclePhaseConductor.PhaseGuide phaseGuide,
      final CommandToTextwebsocketframeConductor.CommandGuide commandGuide,
      final ConductorTools.CloseFrameConductor.CloseFrameGuide closeFrameGuide,
      final Responder< TextWebSocketFrame, TextWebSocketFrame > textFrameResponder,
      final Responder< PingWebSocketFrame, PongWebSocketFrame > pingResponder,
      final Responder< CloseWebSocketFrame, Void > closeFrameResponder
  ) throws Exception {
    initializeDownendConnector( setupSupplier.get() ) ;

    this.connectionDescriptor = checkNotNull( connectionDescriptor ) ;
    this.pingGuide = checkNotNull( pingGuide ) ;
    this.phaseGuide = phaseGuide ;
    this.commandGuide = commandGuide ;
    this.textwebsocketResponder = checkNotNull( textFrameResponder ) ;
    this.pingResponder = checkNotNull( pingResponder ) ;
    this.closeFrameGuide = closeFrameGuide ;
    this.closeFrameResponder = checkNotNull( closeFrameResponder ) ;
    if( startBabyUpend ) {
      babyUpend().start().join() ;
    }
    startHttpProxyServerMaybe() ;
    if( startDownendConnector ) {
      startDownendConnector( connectionDescriptor ) ;
    }
  }

  public void stopAll() throws CollectingException {
    stopAll(
      () -> { if( babyUpend != null ) babyUpend.stop() ; },
      () -> babyUpend = null
    ) ;
  }


// =====
// Upend
// =====

  private CommandToTextwebsocketframeConductor.CommandGuide commandGuide = null ;

  private Responder< TextWebSocketFrame, TextWebSocketFrame > textwebsocketResponder = null ;

  private Responder< PingWebSocketFrame, PongWebSocketFrame > pingResponder = null ;

  private Responder< CloseWebSocketFrame, Void > closeFrameResponder = null ;

  private ConductorTools.PingConductor.PingGuide pingGuide = null ;

  private ConductorTools.SessionLifecyclePhaseConductor.PhaseGuide phaseGuide = null ;

  private ConductorTools.CloseFrameConductor.CloseFrameGuide closeFrameGuide = null ;

  public CommandToTextwebsocketframeConductor.CommandGuide
  commandToTextWebsocketGuide() {
    checkState( commandGuide != null, "Not initialized properly" ) ;
    return commandGuide ;
  }

  public ConductorTools.SessionLifecyclePhaseConductor.PhaseGuide phaseGuide() {
    checkState( phaseGuide != null, "Not initialized properly" ) ;
    return phaseGuide ;
  }

  public ConductorTools.CloseFrameConductor.CloseFrameGuide closeFrameGuide() {
    checkState( closeFrameGuide != null, "Not initialized properly" ) ;
    return closeFrameGuide ;
  }

  /**
   * Ensure there is no unsent {@link SessionLifecycle.Phase}, taking care of {@link #phaseGuide} possible nullity.
   */
  public void checkPhaseGuideOutboundQueueEmptyIfAny() {
    if( phaseGuide != null ) {
      assertThat( phaseGuide.outboundQueueEmpty() ).isTrue() ;
    }
  }

  public ConductorTools.PingConductor.PingGuide
  pingPongGuide() {
    return pingGuide ;
  }

  public BabyUpend babyUpend() {
    checkState( textwebsocketResponder != null ) ;
    checkState( pingResponder != null ) ;
    if( babyUpend == null ) {
      babyUpend = new BabyUpend(
          upendListenAddress.getPort(),
          downendSetup().usingTls() ? SSL_ENGINE_FACTORY_FOR_SERVER.get() : null,
          connectionDescriptor,
          textwebsocketResponder,
          pingResponder,
          closeFrameResponder
      ) ;
    }
    return babyUpend ;
  }

  public void commandRoundtrip( final ENDPOINT_SPECIFIC downendSpecific )
      throws InterruptedException
  {
    commandRoundtrip( downendSpecific, true ) ;
  }

  public void commandRoundtrip( final ENDPOINT_SPECIFIC downendSpecific, final boolean tracked )
      throws InterruptedException
  {
    LOGGER.info( "Starting a " + Command.class.getSimpleName() +
        " roundtrip testing connection ..." ) ;

    if( phaseGuide != null ) {
      assertThat( phaseGuide.drainInbound() ).asList().hasSize( 0 ) ;
      assertThat( phaseGuide.outboundQueueEmpty() ).isTrue() ;

      /** This causes {@link #textwebsocketResponder} to look for next {@link Command}. */
      phaseGuide.recordNoResponse() ;
    }
    assertThat( commandGuide.drainInbound() ).asList().hasSize( 0 ) ;
    assertThat( commandGuide.outboundQueueEmpty() ).isTrue() ;

    commandGuide.record( downwardEchoCommand( TAG_0 ) ) ;
    downend().send( upwardEchoCommand( downendSpecific, tracked ) ) ;
    checkDequeuedDownwardCommandEquivalentTo( downwardEchoCommand( downendSpecific ) ) ;

    if( phaseGuide != null ) {
      assertThat( phaseGuide.drainInbound() ).asList().hasSize( 0 ) ;
      assertThat( phaseGuide.outboundQueueEmpty() ).isTrue() ;
    }
    assertThat( commandGuide.outboundQueueEmpty() ).isTrue() ;
    assertThat( commandGuide.drainInbound() ).asList().hasSize( 1 ) ;

    LOGGER.info( Command.class.getSimpleName() + " roundtrip done." ) ;
  }

  public void commandRoundtripUntracked( final ENDPOINT_SPECIFIC downendSpecific )
      throws InterruptedException
  {
    LOGGER.info( "Starting a " + Command.class.getSimpleName() +
        " roundtrip testing connection ..." ) ;

    if( phaseGuide != null ) {
      assertThat( phaseGuide.drainInbound() ).asList().hasSize( 0 ) ;
      assertThat( phaseGuide.outboundQueueEmpty() ).isTrue() ;

      /** This causes {@link #textwebsocketResponder} to look for next {@link Command}. */
      phaseGuide.recordNoResponse() ;
    }
    assertThat( commandGuide.drainInbound() ).asList().hasSize( 0 ) ;
    assertThat( commandGuide.outboundQueueEmpty() ).isTrue() ;

    commandGuide.record( downwardEchoCommand( TAG_0 ) ) ;
    downend().send( upwardEchoCommand( downendSpecific, true ) ) ;
    checkDequeuedDownwardCommandEquivalentTo( downwardEchoCommand( downendSpecific ) ) ;

    if( phaseGuide != null ) {
      assertThat( phaseGuide.drainInbound() ).asList().hasSize( 0 ) ;
      assertThat( phaseGuide.outboundQueueEmpty() ).isTrue() ;
    }
    assertThat( commandGuide.outboundQueueEmpty() ).isTrue() ;
    assertThat( commandGuide.drainInbound() ).asList().hasSize( 1 ) ;

    LOGGER.info( Command.class.getSimpleName() + " roundtrip done." ) ;
  }


}
