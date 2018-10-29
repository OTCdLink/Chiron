package com.otcdlink.chiron.integration.drill;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.ExtendedChange;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.downend.CommandInFlightStatus;
import com.otcdlink.chiron.downend.CommandTransceiver;
import com.otcdlink.chiron.downend.Downend;
import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.downend.SignonMaterializer;
import com.otcdlink.chiron.downend.Tracker;
import com.otcdlink.chiron.downend.babyupend.BabyUpend;
import com.otcdlink.chiron.fixture.tcp.http.ConnectProxy;
import com.otcdlink.chiron.fixture.tcp.http.HttpProxy;
import com.otcdlink.chiron.integration.drill.SketchLibrary.DummySessionPrimer;
import com.otcdlink.chiron.integration.drill.fakeend.FullDuplex;
import com.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.middle.PhoneNumber;
import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.session.SignableUser;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.mockster.Mockster;
import com.otcdlink.chiron.toolbox.Credential;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import com.otcdlink.chiron.toolbox.netty.Hypermessage;
import com.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import com.otcdlink.chiron.upend.TimeKit;
import com.otcdlink.chiron.upend.UpendConnector;
import com.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer;
import com.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands;
import com.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates a {@link DownendConnector} and an {@link UpendConnector}, or their lightweight
 * equivalent ({@link NettyHttpClient} and {@link BabyUpend} namely), so all connect together.
 * This includes setting up SSL, a proxy, and so on.
 */
public interface ConnectorDrill extends AutoCloseable {

  static DrillBuilder newBuilder() {
    return DrillBuilder.DEFAULT ;
  }

  TimeKit< UpdateableClock > timeKit() ;

  DrillBuilder builderBuiltWith() ;

  InternetAddressPack internetAddressPack() ;

  /**
   * Methods here must support asynchronicity because implementation may trigger
   * an Operative call on a Mock, with corresponding Verification happening later.
   * A synchronous call would cause a deadlock, then.
   */
  interface AnyEndLifecycle {
    CompletableFuture< Void > start() ;
    CompletableFuture< Void > stop() ;
  }

  interface AnyEndFullDuplex {

    /**
     * @throws FeatureUnavailableException if using
     *     {@link DrillBuilder.ForUpendConnector} not created with
     *     {@link DrillBuilder.ForUpendConnector#withAuthentication(Authentication)}.
     */
    FullDuplex< SessionLifecycle.Phase, SessionLifecycle.Phase > phasing() ;

    FullDuplex< CloseWebSocketFrame, CloseWebSocketFrame > closing() ;

  }

  @SuppressWarnings( "unused" )
  enum AutomaticLifecycle {
    START( true, false ),
    STOP( false, true ),
    BOTH( true, true ),
    NONE( false, false ),
    ;

    public final boolean start ;
    public final boolean stop ;

    AutomaticLifecycle( final boolean start, final boolean stop ) {
      this.start = start ;
      this.stop = stop ;
    }
  }

  enum Authentication {
    NONE( false ),
    ONE_FACTOR( true ),
    TWO_FACTOR( true ),
    ;
    public final boolean authenticating ;

    Authentication( boolean authenticating ) {
      this.authenticating = authenticating;
    }

    public static ImmutableSet< Authentication > ALL_AUTHENTICATED = Arrays.stream( values() )
        .filter( authentication -> authentication.authenticating )
        .collect(ImmutableSet.toImmutableSet()
    ) ;

    public static ImmutableSet< Authentication > ALL_VALUES = ImmutableSet.copyOf( values() ) ;
  }

  /**
   * Shortcut to know if {@link UpendConnector} has {@link Authentication#ONE_FACTOR} or
   * {@link Authentication#TWO_FACTOR} enabled.
   */
  Authentication authentication() ;

  void runOutOfVerifierThread( final Runnable runnable ) ;

  /**
   * @throws FeatureUnavailableException if there is no {@link ConnectProxy}.
   */
  HttpProxy httpProxy() ;

  void restartHttpProxy() ;

  void changeTimeBoundary( TimeBoundary.ForAll timeBoundary ) ;


// ==========
// Event loop
// ==========

  interface EventLoopOwner { }

  void processNextTaskCapture(
      Function< Downend.ScheduledInternally, TaskExecution > validityChecker
  ) ;

  void dumpTaskCapture() ;

  enum TaskExecution {
    EXECUTE, SKIP, UNEXPECTED
  }

  Function< Downend.ScheduledInternally, TaskExecution > SKIP_PING = expect(
      Downend.ScheduledInternally.Ping.class, TaskExecution.SKIP ) ;

  Function< Downend.ScheduledInternally, TaskExecution > EXECUTE_PING = expect(
      Downend.ScheduledInternally.Ping.class, TaskExecution.EXECUTE ) ;

  Function< Downend.ScheduledInternally, TaskExecution > EXECUTE_PONG_TIMEOUT = expect(
      Downend.ScheduledInternally.PongTimeout.class, TaskExecution.EXECUTE ) ;

  Function< Downend.ScheduledInternally, TaskExecution > EXECUTE_RECONNECT = expect(
      Downend.ScheduledInternally.Reconnect.class, TaskExecution.EXECUTE ) ;

  Function< Downend.ScheduledInternally, TaskExecution > SKIP_RECONNECT = expect(
      Downend.ScheduledInternally.Reconnect.class, TaskExecution.SKIP ) ;

  static Function< Downend.ScheduledInternally, TaskExecution > expect(
      final Class< ? extends Downend.ScheduledInternally > skip,
      final TaskExecution taskExecution
  ) {
    return task -> skip.isAssignableFrom( task.getClass() ) ?
        taskExecution : TaskExecution.UNEXPECTED ;
  }

// =======
// Downend
// =======

  /**
   * @throws FeatureUnavailableException if not created with
   *     {@link DrillBuilder#forDownendConnector()}.
   */
  ForSimpleDownend forSimpleDownend() ;

  /**
   * @throws FeatureUnavailableException if not created with
   *     {@link DrillBuilder#forCommandTransceiver()}.
   */
  ForCommandTransceiver forCommandTransceiver() ;

  /**
   * @throws FeatureUnavailableException if not created with
   *     {@link DrillBuilder#fakeDownend()}.
   */
  ForFakeDownend forFakeDownend() ;

  ForDownend.Kind downendKind() ;

  /**
   * Do we need more than one instance of this during one test?
   */
  interface ForDownend< ENDPOINT_SPECIFIC > extends EventLoopOwner, AnyEndLifecycle {

    EchoUpwardDuty< ENDPOINT_SPECIFIC > upwardDuty() ;

    EchoDownwardDuty< ENDPOINT_SPECIFIC > downwardDutyMock() ;

    SignonMaterializer signonMaterializerMock() ;

    enum Kind {
      DOWNEND_CONNECTOR,
      COMMAND_TRANSCEIVER,
      FAKE,
      ;
      public static final ImmutableSet< Kind > ALL_REAL = Arrays.stream( values() )
          .filter( kind -> kind != FAKE )
          .collect( ImmutableSet.toImmutableSet() )
      ;
    }
  }

  interface ForSimpleDownend extends ForDownend< Command.Tag > {
    DownendConnector.ChangeWatcher changeWatcherMock() ;

    /**
     * The usual deecapsulation method for extreme cases.
     */
    void applyDirectly(
        Consumer<
            DownendConnector<
                Command.Tag,
                EchoDownwardDuty< Command.Tag >,
                EchoUpwardDuty< Command.Tag >
            >
        > consumer,
        boolean runInNonVerifierThread
    ) ;

    < T > T applyDirectly(
        Function<
            DownendConnector<
                Command.Tag,
                EchoDownwardDuty< Command.Tag >,
                EchoUpwardDuty< Command.Tag >
            >,
            T
        > transformer
    ) ;


      /**
       * Creates a fresh instance with same configuration as defined in
       * {@link DrillBuilder.ForDownendConnector}, but unstarted.
       * There will be a new {@link DownendConnector.ChangeWatcher} and a new {@link CommandConsumer}.
       */
    ForSimpleDownend clone() ;

    ChangeAsConstant changeAsConstant() ;

    class ChangeAsConstant {
      public final DownendConnector.Change stopped ;
      public final DownendConnector.Change connecting ;
      public final DownendConnector.Change connected ;
      public final DownendConnector.Change stopping ;
      public final DownendConnector.Change signedIn ;

      public ChangeAsConstant(
          final DownendConnector.Change stopped,
          final DownendConnector.Change connecting,
          final DownendConnector.Change connected,
          final DownendConnector.Change stopping,
          final DownendConnector.Change signedIn
      ) {
        this.stopped = checkNotNull( stopped ) ;
        this.connecting = checkNotNull( connecting ) ;
        this.connected = checkNotNull( connected ) ;
        this.stopping = checkNotNull( stopping ) ;
        this.signedIn = checkNotNull( signedIn ) ;
      }
    }

  }

  interface ForCommandTransceiver extends ForDownend< Tracker > {

    /**
     * @return an instance of the mock created by underlying {@link Mockster}.
     *
     * @throws FeatureUnavailableException if not created with
     *     {@link DrillBuilder#forCommandTransceiver()}.
     */
    Tracker trackerMock() ;


    /**
     * Useful when single, pre-built {@link #trackerMock()} is not enough.
     *
     * @return an fresh instance of the mock created by underlying {@link Mockster}.
     *
     * @throws FeatureUnavailableException if not created with
     *     {@link DrillBuilder#forCommandTransceiver()}.
     */
    Tracker newTrackerMock() ;

    CommandTransceiver.ChangeWatcher changeWatcherMock() ;

    void applyDirectly(
        Consumer<
            CommandTransceiver<
                EchoDownwardDuty< Tracker >,
                EchoUpwardDuty< Tracker >
            >
        > consumer,
        boolean runInNonVerifierThread
    ) ;

    < T > T applyDirectly(
        Function<
            CommandTransceiver<
                EchoDownwardDuty< Tracker >,
                EchoUpwardDuty< Tracker >
            >,
            T
        > transformer
    ) ;

    ChangeAsConstant changeAsConstant() ;

    class ChangeAsConstant extends ForSimpleDownend.ChangeAsConstant {

      public final ExtendedChange failedConnectionAttempt ;
      public final ExtendedChange noSignon ;
      public final CommandInFlightStatus commandInFlightStatusQuiet ;
      public final CommandInFlightStatus commandInFlightStatusInFlight ;
      public final ExtendedChange.CommandInFlightStatusChange
          commandInFlightStatusSomeCommandFailed ;

      public ChangeAsConstant(
          final DownendConnector.Change stopped,
          final DownendConnector.Change connecting,
          final DownendConnector.Change connected,
          final DownendConnector.Change stopping,
          final DownendConnector.Change signedIn,
          final ExtendedChange failedConnectionAttempt,
          final ExtendedChange noSignon,
          final CommandInFlightStatus commandInFlightStatusQuiet,
          final CommandInFlightStatus commandInFlightStatusInFlight,
          final ExtendedChange.CommandInFlightStatusChange commandInFlightStatusSomeCommandFailed
      ) {
        super( stopped, connecting, connected, stopping, signedIn ) ;
        this.failedConnectionAttempt = checkNotNull( failedConnectionAttempt ) ;
        this.noSignon = checkNotNull( noSignon ) ;
        this.commandInFlightStatusQuiet = checkNotNull( commandInFlightStatusQuiet ) ;
        this.commandInFlightStatusInFlight = checkNotNull( commandInFlightStatusInFlight ) ;
        this.commandInFlightStatusSomeCommandFailed =
            checkNotNull( commandInFlightStatusSomeCommandFailed ) ;
      }
    }

  }

  interface ForFakeDownend extends AnyEndLifecycle {
    DownendDuplex duplex() ;
    void connect() ;
    NettyHttpClient.CompleteResponse httpRequest( Hypermessage.Request request ) ;
  }

  interface DownendDuplex extends AnyEndFullDuplex {
    FullDuplex< PongWebSocketFrame, PingWebSocketFrame > pinging() ;
    FullDuplex.ForTextWebSocket< EchoUpwardDuty< Command.Tag > > texting() ;
  }


// =====
// Upend
// =====

  interface ForUpend extends AnyEndLifecycle {
    enum Kind {
      REAL,
      FAKE,
      ;
      public static ImmutableSet< Kind > ALL_VALUES = ImmutableSet.copyOf( values() ) ;
    }
  }

  ForUpend.Kind upendKind() ;

  /**
   * @throws FeatureUnavailableException if not created with
   *     {@link DrillBuilder#forUpendConnector()}.
   */
  ForUpendConnector forUpendConnector() ;

  /**
   * @throws FeatureUnavailableException if not created with
   *     {@link DrillBuilder#fakeUpend()}.
   */
  ForFakeUpend forFakeUpend() ;

  interface ForUpendConnector extends AnyEndLifecycle, EventLoopOwner {
    EchoUpwardDuty< Designator > upwardDutyMock() ;
    EchoDownwardDuty< Designator > downwardDuty() ;
    OutwardSessionSupervisor< Channel, InetAddress, DummySessionPrimer > sessionSupervisorMock() ;
    void changeTimeBoundary( TimeBoundary.ForAll timeBoundary ) ;

    enum HttpRequestRelayerKind {
      NONE( null ), ALWAYS_OK( happyCommandRecognizer() ), ;

      public final HttpRequestRelayer httpRequestRelayer ;

      HttpRequestRelayerKind( HttpRequestRelayer httpRequestRelayer ) {
        this.httpRequestRelayer = httpRequestRelayer;
      }

      public static HttpRequestRelayer happyCommandRecognizer() {
        return ( httpRequest, channelHandlerContext ) -> {
          if( httpRequest.method().equals( HttpMethod.GET ) ) {
            if( httpRequest.uri().matches( ".*" ) ) {
              final boolean keepAlive = HttpUtil.isKeepAlive( httpRequest ) ;
              UsualHttpCommands.Html.htmlBodyOk(
                  "<h2>OK (200) response for " + httpRequest.uri() + "</h2>" +
                      "<p>" + httpRequest.content().toString( Charsets.UTF_8 ) + "</p>"
              ).feed( channelHandlerContext, keepAlive ) ;
              return true ;
            }
          }
          return false ;
        } ;
      }
    }
  }

  interface ForFakeUpend extends ForUpend {
    UpendDuplex duplex() ;
  }

  interface UpendDuplex extends AnyEndFullDuplex {

    FullDuplex< PingWebSocketFrame, PongWebSocketFrame > ponging() ;

    FullDuplex.ForTextWebSocket< EchoDownwardDuty< Command.Tag > > texting() ;
  }

// ================
// Useful constants
// ================

  /**
   * Used in {@link ForCommandTransceiver#changeAsConstant()}.
   */
  Credential GOOD_CREDENTIAL = new Credential( "Login", "Password" ) ;

  SessionIdentifier SESSION_IDENTIFIER = new SessionIdentifier( "5e55i0n" ) ;
  SessionIdentifier SESSION_IDENTIFIER_2 = new SessionIdentifier( "5e55i0n222" ) ;

  SignableUser SIGNABLE_USER = new SignableUser() {
    @Override
    public String login() {
      return GOOD_CREDENTIAL.getLogin() ;
    }
    private final PhoneNumber phoneNumber = new PhoneNumber( "+9876543210" ) ;
    @Override
    public PhoneNumber phoneNumber() {
      return phoneNumber ;
    }
  } ;

  SecondaryToken SECONDARY_TOKEN = new SecondaryToken( "5eG0nd4ryT0k3n" ) ;

  SecondaryCode SECONDARY_CODE = new SecondaryCode( "S3ec0nd4ryC0D3" ) ;


// ======
// Sketch
// ======

  void play( Sketch sketch ) throws Exception ;

  /**
   * A Sketch is a reusable part of a Story. A Story is an integration test.
   */
  interface Sketch {
    ImmutableSet< ForUpend.Kind > upendKindRequirements() ;
    ImmutableSet< ForDownend.Kind > downendKindRequirements() ;
    ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements() ;
    void run( ConnectorDrill drill ) throws Exception ;
  }

  abstract class Default implements Sketch {
    private final ImmutableSet< ForUpend.Kind > upendKindRequirements ;
    private final ImmutableSet< ForDownend.Kind > downendKindRequirements ;
    private final ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements ;

    public Default(
        final ForUpend.Kind upendKindRequirements,
        final ImmutableSet< ForDownend.Kind > downendKindRequirements,
        final ImmutableSet< Authentication > authenticationRequirements
    ) {
      this.upendKindRequirements = ImmutableSet.of( upendKindRequirements ) ;
      this.downendKindRequirements = checkNotNull( downendKindRequirements ) ;
      this.authenticationRequirements = checkNotNull( authenticationRequirements ) ;
      checkArgument( ! authenticationRequirements.isEmpty() ) ;
    }

    @Override
    public final ImmutableSet< ForUpend.Kind > upendKindRequirements() {
      return upendKindRequirements ;
    }

    @Override
    public final ImmutableSet< ForDownend.Kind > downendKindRequirements() {
      return downendKindRequirements ;
    }

    @Override
    public final ImmutableSet< Authentication > authenticationRequirements() {
      return authenticationRequirements ;
    }
  }


}
