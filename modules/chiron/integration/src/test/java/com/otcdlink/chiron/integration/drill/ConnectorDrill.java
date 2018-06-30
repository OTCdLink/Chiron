package com.otcdlink.chiron.integration.drill;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.ExtendedChange;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.downend.CommandInFlightStatus;
import com.otcdlink.chiron.downend.CommandTransceiver;
import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.downend.SignonMaterializer;
import com.otcdlink.chiron.downend.Tracker;
import com.otcdlink.chiron.downend.babyupend.BabyUpend;
import com.otcdlink.chiron.fixture.tcp.http.ConnectProxy;
import com.otcdlink.chiron.fixture.tcp.http.HttpProxy;
import com.otcdlink.chiron.integration.drill.fakeend.HalfDuplex;
import com.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.middle.PhoneNumber;
import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
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
import com.otcdlink.chiron.upend.session.SignableUser;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

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

  interface EventLoopOwner { }

  /**
   * Methods here must support asynchronicity because implementation may trigger
   * an Operative call on a Mock, with corresponding Verification happening later.
   * A synchronous call would cause a deadlock, then.
   */
  interface AnyEndLifecycle {
    CompletableFuture< Void > start() ;
    CompletableFuture< Void > stop() ;
  }

  interface AnyEndHalfDuplex {

    /**
     * @throws FeatureUnavailableException if using
     *     {@link DrillBuilder.ForUpendConnector} not created with
     *     {@link DrillBuilder.ForUpendConnector#withAuthentication(Authentication)}.
     */
    HalfDuplex< SessionLifecycle.Phase, SessionLifecycle.Phase > phasing() ;

    HalfDuplex< CloseWebSocketFrame, CloseWebSocketFrame > closing() ;

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
     * Creates a fresh instance with same configuration as defined in
     * {@link DrillBuilder.ForDownendConnector}, but unstarted.
     * There will be a new {@link DownendConnector.ChangeWatcher} and a new {@link CommandConsumer}.
     */
    ForSimpleDownend clone() ;

    ChangeAsConstant changesAsConstant() ;

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

    CommandTransceiver.ChangeWatcher changeWatcherMock() ;

    ChangeAsConstant changesAsConstants() ;

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
    DownendHalfDuplex halfDuplex() ;
    void connect() ;
    NettyHttpClient.CompleteResponse httpRequest( Hypermessage.Request request ) ;
  }

  interface DownendHalfDuplex extends AnyEndHalfDuplex {
    HalfDuplex< PongWebSocketFrame, PingWebSocketFrame > pinging() ;
    HalfDuplex.ForTextWebSocket< EchoUpwardDuty< Command.Tag > > texting() ;
  }


// =====
// Upend
// =====

  interface ForUpend extends AnyEndLifecycle {
    enum Kind {
      REAL, FAKE
    }
  }

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
    OutwardSessionSupervisor< Channel, InetAddress > sessionSupervisorMock() ;

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
              new UsualHttpCommands.Html(
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
    UpendHalfDuplex halfDuplex() ;
  }

  interface UpendHalfDuplex extends AnyEndHalfDuplex {

    HalfDuplex< PingWebSocketFrame, PongWebSocketFrame > ponging() ;

    HalfDuplex.ForTextWebSocket< EchoDownwardDuty< Command.Tag > > texting() ;
  }

// ================
// Useful constants
// ================

  /**
   * Used in {@link ForCommandTransceiver#changesAsConstants()}.
   */
  Credential GOOD_CREDENTIAL = new Credential( "Login", "Password" ) ;

  SessionIdentifier SESSION_IDENTIFIER = new SessionIdentifier( "5e55i0n" ) ;

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
    ForUpend.Kind upendKindRequirement() ;
    ImmutableSet< ForDownend.Kind > downendKindRequirements() ;
    ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements() ;
    void run( ConnectorDrill drill ) throws Exception ;
  }

  abstract class Default implements Sketch {
    private final ForUpend.Kind upendKindRequirement ;
    private final ImmutableSet< ForDownend.Kind > downendKindRequirements ;
    private final ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements ;

    public Default(
        final ForUpend.Kind upendKindRequirement,
        final ImmutableSet< ForDownend.Kind > downendKindRequirements,
        final ImmutableSet< Authentication > authenticationRequirements
    ) {
      this.upendKindRequirement = checkNotNull( upendKindRequirement ) ;
      this.downendKindRequirements = checkNotNull( downendKindRequirements ) ;
      this.authenticationRequirements = checkNotNull( authenticationRequirements ) ;
      checkArgument( ! authenticationRequirements.isEmpty() ) ;
    }

    @Override
    public final ForUpend.Kind upendKindRequirement() {
      return upendKindRequirement ;
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
