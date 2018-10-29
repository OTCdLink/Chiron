package com.otcdlink.chiron.downend;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.command.codec.Codec;
import com.otcdlink.chiron.downend.state.StateBody;
import com.otcdlink.chiron.downend.state.StateUpdater;
import com.otcdlink.chiron.downend.tier.CommandReceiverTier;
import com.otcdlink.chiron.downend.tier.CommandWebsocketCodecDownendTier;
import com.otcdlink.chiron.downend.tier.DownendCommandInterceptorTier;
import com.otcdlink.chiron.downend.tier.DownendSupervisionTier;
import com.otcdlink.chiron.downend.tier.DownendTierName;
import com.otcdlink.chiron.downend.tier.PongTier;
import com.otcdlink.chiron.downend.tier.SessionDownendTier;
import com.otcdlink.chiron.downend.tier.SessionPhaseWebsocketCodecDownendTier;
import com.otcdlink.chiron.middle.ChannelTools;
import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.middle.tier.WebsocketFragmenterTier;
import com.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import com.otcdlink.chiron.middle.tier.WebsocketTools;
import com.otcdlink.chiron.toolbox.Credential;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.toolbox.internet.InternetProxyAccess;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.otcdlink.chiron.downend.DownendConnector.State.CONNECTED;
import static com.otcdlink.chiron.downend.DownendConnector.State.CONNECTING;
import static com.otcdlink.chiron.downend.DownendConnector.State.STOPPED;
import static com.otcdlink.chiron.downend.DownendConnector.State.STOPPING;
import static io.netty.channel.ChannelFutureListener.CLOSE;

/**
 * Establishes and keeps alive a WebSocket connection to some given host,
 * for sending and receiving {@link Command} objects.
 *
 * <h2>Signon sequence</h2>
 * This is the initial sequence when there is no {@link SessionIdentifier}:
 * <pre>
 * {@link DownendConnector}                        {@code UpendConnector}
 *    |                                                |
 *    |  &gt;--- 1: HTTP connection --------------------&gt; |
 *    |  &lt;---------------- 2: WebSocket handshake ---&lt; |
 *    |  &gt;--- 3: Primary signon ---------------------&gt; | {@link SessionLifecycle.PrimarySignon}
 *    |  &lt;----- 4: Missing Secondary code + token ---&lt; | {@link SessionLifecycle.SecondarySignonNeeded}
 *    |  &gt;--- 5: Secondary code + token -------------&gt; | {@link SessionLifecycle.SecondarySignon}
 *    |  &lt;----------------- 6: Session identifier ---&lt; | {@link SessionLifecycle.SessionValid}
 * </pre>
 *
 * This is the Resignon sequence (there is one {@link SessionIdentifier} that may be still valid):
 * <pre>
 * {@link DownendConnector}                        {@code UpendConnector}
 *    |                                                |
 *    |  &gt;--- 1: HTTP (re)connection ----------------&gt; |
 *    |  &lt;---------------- 2: WebSocket handshake ---&lt; |
 *    |  &gt;--- 3: Resubmit {@link SessionIdentifier} ---------&gt; | {@link SessionLifecycle.Resignon}
 *    |  &lt;----------------- 6: Session identifier ---&lt; | {@link SessionLifecycle.SessionValid}
 * </pre>
 *
 * <h2>Connectiona and Signon notification</h2>
 * <p>
 * The {@link ChangeWatcher#stateChanged(Change)} with {@link Change.SuccessfulConnection}
 * is about WebSocket layer. An application can send {@link Command}s as soon as it gets this
 * notification, if {@link ConnectionDescriptor#authenticationRequired} is {@code false}.
 * <p>
 * The {@link ChangeWatcher#stateChanged(Change)} with {@link State#SIGNED_IN} gets called
 * after obtaining a {@link SessionIdentifier}. This happens only if
 * {@link ConnectionDescriptor#authenticationRequired} was {@code true}.
 *
 * <h2>Threading model</h2>
 * <p>
 * All asynchronous operations occur in {@link Setup#eventLoopGroup}, which should not be
 * shut down while the {@link DownendConnector} is in {@link State#CONNECTING} or
 * {@link State#CONNECTED} state.
 * {@link #start()} and {@link #stop()} methods don't affect the {@link Setup#eventLoopGroup},
 * except by using its threads.
 */
public final class DownendConnector< ENDPOINT_SPECIFIC, DOWNWARD_DUTY, UPWARD_DUTY >
    implements Downend< ENDPOINT_SPECIFIC, UPWARD_DUTY >
{


  private static final Logger LOGGER = LoggerFactory.getLogger( DownendConnector.class ) ;

  /**
   * Contains the consistent set of parameters for creating a {@link DownendConnector}.
   */
  public static class Setup< ENDPOINT_SPECIFIC, DOWNWARD_DUTY > {
    public final EventLoopGroup eventLoopGroup ;

    /**
     * Refers to the same address as {@link #uri}.
     */
    public final URL url ;
    public final URI uri ;

    public final SslEngineFactory.ForClient sslEngineFactory ;
    public final TimeBoundary.PrimingForDownend primingTimeBoundary ;
    public final SignonMaterializer signonMaterializer ;
    public final ChangeWatcher changeWatcher;
    public final InternetProxyAccess internetProxyAccess ;

    /**
     * Processes {@link Command}s with a {@link Command.Tag}.
     */
    public final CommandConsumer< Command< ENDPOINT_SPECIFIC, DOWNWARD_DUTY > > commandReceiver ;

    public final Codec< ENDPOINT_SPECIFIC > endpointSpecificCodec ;

    public final CommandBodyDecoder< ENDPOINT_SPECIFIC, DOWNWARD_DUTY > commandDecoder ;

    public final CommandInterceptor.Factory commandInterceptorFactory ;

    public final WebsocketFrameSizer websocketFrameSizer ;

    public Setup(
        final EventLoopGroup eventLoopGroup,
        final URL url,
        final InternetProxyAccess internetProxyAccess,
        final SslEngineFactory.ForClient sslEngineFactory,
        final TimeBoundary.PrimingForDownend primingTimeBoundary,
        final SignonMaterializer signonMaterializer,
        final ChangeWatcher changeWatcher,
        final Codec< ENDPOINT_SPECIFIC > endpointSpecificCodec,
        final CommandBodyDecoder< ENDPOINT_SPECIFIC, DOWNWARD_DUTY > commandDecoder,
        final CommandConsumer< Command< ENDPOINT_SPECIFIC, DOWNWARD_DUTY > > commandReceiver,
        final CommandInterceptor.Factory commandInterceptorFactory,
        final WebsocketFrameSizer websocketFrameSizer
    ) {
      this.eventLoopGroup = checkNotNull( eventLoopGroup ) ;
      this.url = checkNotNull( url ) ;
      this.uri = UrxTools.toUriQuiet( url ) ;
      this.internetProxyAccess = internetProxyAccess ;
      this.sslEngineFactory = sslEngineFactory ;
      this.signonMaterializer = new SignonMaterializerInterceptor( signonMaterializer ) ;
      this.primingTimeBoundary = checkNotNull( primingTimeBoundary ) ;
      this.changeWatcher = changeWatcher;
      this.endpointSpecificCodec = checkNotNull( endpointSpecificCodec ) ;
      this.commandDecoder = checkNotNull( commandDecoder ) ;
      this.commandReceiver = checkNotNull( commandReceiver ) ;
      this.commandInterceptorFactory = commandInterceptorFactory ;
      this.websocketFrameSizer = checkNotNull( websocketFrameSizer ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + '{' +
          ( internetProxyAccess == null ? "" : "proxy=" + internetProxyAccess + ';'  ) +
          ( sslEngineFactory == null ? "" : "ssl=" + sslEngineFactory + ';'  ) +
          ( changeWatcher == null ? "" : "stateWatcher=" +
              ToStringTools.nameAndCompactHash( changeWatcher ) + ';'  ) +
          "url=" + url.toExternalForm() + ";" +
          "initialTimeBoundary=" + TimeBoundary.PrimingForDownend.toString( primingTimeBoundary ) +
          '}'
      ;
    }

    public boolean usingTls() {
      return sslEngineFactory != null ;
    }

    public int port() {
      return url.getPort() ;
    }

  }

  final Setup< ENDPOINT_SPECIFIC, DOWNWARD_DUTY > setup ;

  private final StateUpdater stateUpdater ;

  private final SessionDownendTier sessionDownendTier ;


  private final DownendSupervisionTier downendSupervisionTier =
      new DownendSupervisionTier( createClaim() ) ;


  public DownendConnector( final Setup< ENDPOINT_SPECIFIC, DOWNWARD_DUTY > setup ) {
    this.setup = checkNotNull( setup ) ;
    this.stateUpdater = new StateUpdater(
        this::toString,
        setup.url,
        setup.primingTimeBoundary,
        setup.changeWatcher
    ) ;
    ( ( SignonMaterializerInterceptor ) this.setup.signonMaterializer ).lastLoginUpdater =
        login -> stateUpdater.update( stateBody -> stateBody.login( login ) ) ;
    sessionDownendTier = new SessionDownendTier(
        new SessionDownendTier.Claim() {
          @Override
          public void sessionValid() {
            DownendConnector.this.signonHappened() ;
          }

          @Override
          public void signonCancelled() {
            DownendConnector.this.signonCancelled() ;
          }
        },
        setup.signonMaterializer
    ) ;
    LOGGER.info( "Created " + this + " with " + setup + "." ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + '{' +
        setup.uri.toASCIIString() +
        '}'
    ;
  }


// =========
// Utilities
// =========

  private void cancelPreviousAllFutures( final StateUpdater.Transition transition ) {
    if( transition != null ) {
      cancelAllFutures( transition.previous ) ;
    }
  }

  private void cancelAllFutures( final StateBody stateBody ) {
    if( stateBody != null ) {
      cancelAllFutures(
          stateBody.reconnectFuture,
          stateBody.nextPingFuture,
          stateBody.nextPongTimeoutFuture
      ) ;
    }
  }

  private void cancelAllFutures(
      final java.util.concurrent.ScheduledFuture< ? >... scheduledFutures
  ) {
    for( final java.util.concurrent.ScheduledFuture scheduledFuture : scheduledFutures ) {
      if( scheduledFuture != null ) {
        scheduledFuture.cancel( true ) ;
      }
    }
  }


// ====
// Send
// ====

  /**
   * Attempts to send a {@link Command}, silently failing if something goes wrong.
   * Implementation performs a lazy state check (based on {@link StateUpdater#current()})
   * but we can't lock everything to prevent a {@link #stop()} to occure before the complete
   * success or failure of the {@link #send(Command)}.
   * For this reason, the {@link TrackerCurator} should take care of timeouts, or other explicit
   * errors. The {@link ChangeWatcher} receives a {@link Change.Problem} at the first problem
   * encountered.
   */
  @Override
  public void send( final Command< ENDPOINT_SPECIFIC, UPWARD_DUTY > command ) {
    LOGGER.debug( "Sending " + command + " on " + this + " ..." ) ;
    send( stateUpdater.current(), command ) ;
  }

  public final CompletableFuture< Void > sendDelayed(
      final Command< ENDPOINT_SPECIFIC, UPWARD_DUTY > command,
      final int delayMs
  ) {
    final CompletableFuture< Void > sendCompletion = new CompletableFuture<>() ;
    final Channel channel = stateUpdater.current().channel ;
    if( channel == null ) {
      LOGGER.warn( "No " + Channel.class.getSimpleName() + " for " + this + "." ) ;
      return CompletableFuture.completedFuture( null ) ;
    }
    channel.eventLoop().schedule(
        () -> {
          final StateBody currentStateBody = stateUpdater.current() ;
          final ChannelFuture send = send( currentStateBody, command ) ;
          if( send == null ) {
            sendCompletion.completeExceptionally(
                new IllegalStateException( "Now in " + currentStateBody.state ) ) ;
          } else {
            send.addListener( future -> {
              if( future.cause() == null ) {
                sendCompletion.complete( null ) ;
              } else {
                sendCompletion.completeExceptionally( future.cause() ) ;
              }
            } ) ;
          }
        },
        delayMs,
        TimeUnit.MILLISECONDS
    ) ;
    return sendCompletion ;
  }

  private ChannelFuture send( final StateBody currentStateBody, final Object outbound ) {
    checkNotNull( outbound ) ;

    if( currentStateBody.readyToSend() ) {
      return currentStateBody.channel.writeAndFlush( outbound ) ;
    } else {
      // The contract is to let a timeout happen quietly.
      LOGGER.warn( "State is " + currentStateBody.state + ", can't send " + outbound + "." ) ;
      return null ;
    }
  }

// =====
// State
// =====

  /**
   * Need an interface for {@link State} so we can use {@link Change#kind} values which
   * represent method calls in {@link ChangeWatcher} that do not directly affect
   * {@link DownendConnector#state}.
   * Using an interface is also useful when extending {@link ChangeWatcher} into
   * {@link CommandTransceiver.ChangeWatcher} so it accepts parameters of {@link Change} type.
   *
   * @see ChangeWatcher#stateChanged(Change)
   */
  public interface ChangeDescriptor { }

  public enum State implements ChangeDescriptor {
    STOPPED, CONNECTING, CONNECTED, SIGNED_IN, STOPPING ;
  }

  public interface ChangeWatcher {
    /**
     * Input parameter is unparameterized (it should be {@code Change<State>}) so we can
     * derive {@link ChangeWatcher} and add more {@link ChangeDescriptor} implementations.
     */
    void stateChanged( final Change change ) ;

    void failedConnectionAttempt() ;

    void noSignon() ;
  }

  public static class Change< KIND extends Enum< KIND > & ChangeDescriptor > {
    public final KIND kind ;

    public Change( final KIND kind ) {
      this.kind = checkNotNull( kind ) ;
    }

    @Override
    public final String toString() {
      final String body = tostringBody() ;
      return ToStringTools.getNiceClassName( this ) + '{' +
          kind + ( Strings.isNullOrEmpty( body ) ? "" : ";" ) +
          body +
          '}'
      ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final Change that = ( Change ) other ;
      return kind == that.kind ;
    }

    @Override
    public int hashCode() {
      return kind.hashCode() ;
    }

    protected String tostringBody() {
      return "" ;
    }


    public static class SuccessfulConnection extends Change< State > {

      public final ConnectionDescriptor connectionDescriptor ;

      public SuccessfulConnection(
          final ConnectionDescriptor connectionDescriptor
      ) {
        super( State.CONNECTED ) ;
        this.connectionDescriptor = checkNotNull( connectionDescriptor ) ;
      }

      @Override
      protected String tostringBody() {
        return connectionDescriptor.toString() ;
      }

      @Override
      public boolean equals( final Object other ) {
        if( this == other ) {
          return true ;
        }
        if( other == null || getClass() != other.getClass() ) {
          return false ;
        }
        if( ! super.equals( other ) ) {
          return false ;
        }
        final SuccessfulConnection that = ( SuccessfulConnection ) other ;
        return connectionDescriptor.equals( that.connectionDescriptor ) ;
      }

      @Override
      public int hashCode() {
        int result = super.hashCode() ;
        result = 31 * result + connectionDescriptor.hashCode() ;
        return result ;
      }
    }

    public static class SuccessfulSignon extends Change< State > {

      private final URL connexionUrl ;
      private final String login ;

      public SuccessfulSignon(
          final URL connexionUrl,
          final String login
      ) {
        super( State.SIGNED_IN ) ;
        this.connexionUrl = checkNotNull( connexionUrl ) ;
        this.login = checkNotNull( login ) ;
      }

      /**
       * Returns {@link DownendConnector.Setup#url} with {@code ws[s]://} scheme,
       * and an Authority containing last User's login.
       */
      public URL semanticConnectionUrl() {
        try {
          return UrxTools.websocketUrl(
              UrxTools.hasSecureScheme( connexionUrl ),
              ( connexionUrl.getAuthority().contains( "@" ) ? "" : login + "@" ) +
                  connexionUrl.getHost(),
              connexionUrl.getPort(),
              connexionUrl.getPath()
          ) ;
        } catch( final MalformedURLException e ) {
          throw new RuntimeException(
              "Should not happen, connectionUrl supposed to be well-formed", e ) ;
        }
      }

      @Override
      protected String tostringBody() {
        return "login=" + login + ";connectionUrl=" + connexionUrl.toExternalForm() ;
      }

      @Override
      public boolean equals( final Object other ) {
        if( this == other ) {
          return true ;
        }
        if( other == null || getClass() != other.getClass() ) {
          return false ;
        }
        if( ! super.equals( other ) ) {
          return false ;
        }
        final SuccessfulSignon that = ( SuccessfulSignon ) other ;

        if( ! connexionUrl.toExternalForm().equals( that.connexionUrl.toExternalForm() ) ) {
          return false ;
        }
        return login.equals( that.login ) ;
      }

      @Override
      public int hashCode() {
        int result = super.hashCode() ;
        result = 31 * result + connexionUrl.toExternalForm().hashCode() ;
        result = 31 * result + login.hashCode() ;
        return result;
      }
    }

    public static class Problem extends Change< State > {
      public final Throwable cause ;

      public Problem( final Throwable cause ) {
        super( /*PROBLEM*/ null ) ;
        this.cause = checkNotNull( cause ) ;
      }

      @Override
      protected String tostringBody() {
        return cause.getClass().getSimpleName() ;
      }

      @Override
      public boolean equals( final Object other ) {
        if( this == other ) {
          return true ;
        }
        if( other == null || getClass() != other.getClass() ) {
          return false ;
        }
        if( ! super.equals( other ) ) {
          return false ;
        }
        final Problem problem = ( Problem ) other ;
        return cause.equals( problem.cause ) ;
      }

      @Override
      public int hashCode() {
        int result = super.hashCode() ;
        result = 31 * result + cause.hashCode() ;
        return result ;
      }
    }
  }

  private static final class SignonMaterializerInterceptor implements SignonMaterializer {

    private final SignonMaterializer delegate ;
//    private final AtomicReference< String > lastLogin = new AtomicReference<>() ;
    private Consumer< String > lastLoginUpdater = null ;

    /**
     * Avoids superfluous notifications so tests remain simple.
     * No need to synchronize access, changes run in a {@link Channel#eventLoop()}.
     */
    private boolean dialogOpen = false ;


    private SignonMaterializerInterceptor( final SignonMaterializer delegate ) {
      this.delegate = checkNotNull( delegate ) ;
    }

    @Override
    public void readCredential( final Consumer<Credential> credentialConsumer ) {
      dialogOpen = true ;
      delegate.readCredential( credential -> {
        lastLoginUpdater.accept( credential == null ? null : credential.getLogin() ) ;
        credentialConsumer.accept( credential ) ;
      } ) ;
    }

    @Override
    public void readSecondaryCode( final Consumer< SecondaryCode > secondaryCodeConsumer ) {
      delegate.readSecondaryCode( secondaryCodeConsumer ) ;
    }

    @Override
    public void waitForCancellation( final Runnable afterCancelled ) {
      if( dialogOpen ) {
        delegate.waitForCancellation( afterCancelled ) ;
      }
    }

    @Override
    public void setProgressMessage( final String message ) {
      if( dialogOpen ) {
        delegate.setProgressMessage( message ) ;
      }
    }

    @Override
    public void setProblemMessage( final SignonFailureNotice signonFailureNotice ) {
      if( dialogOpen ) {
        delegate.setProblemMessage( signonFailureNotice ) ;
      }
    }

    @Override
    public void done() {
      dialogOpen = false ;
      delegate.done() ;
    }
  }

  /**
   * Shortcut to test if {@link #state} is {@link State#CONNECTED}, or {@link State#SIGNED_IN}
   * when it makes sense (depending on {@link ConnectionDescriptor#authenticationRequired}.
   */
  private ImmutableSet< State > readiness = ImmutableSet.of() ;

  @Override
  public State state() {
    return stateUpdater.current().state ;
  }


// ============
// State wiring
// ============

  /**
   * Called by {@link DownendSupervisionTier#channelRead0(ChannelHandlerContext, Object)})}.
   */
  private void afterWebsocketHandshake( final ConnectionDescriptor connectionDescriptor ) {
    final ScheduledFuture< ? > pingSchedule = setup.eventLoopGroup.schedule(
        ( ScheduledInternally.Ping ) this::pingNow,
        connectionDescriptor.timeBoundary.pingIntervalMs(),
        TimeUnit.MILLISECONDS
    ) ;
    try {
      final StateUpdater.Transition transition = stateUpdater.update(
          stateBody -> stateBody.connected( connectionDescriptor, pingSchedule ) ) ;
      setup.signonMaterializer.setProgressMessage( null ) ;
      cancelAllFutures( transition.previous.reconnectFuture ) ;

      if( ! transition.update.connectionDescriptor.authenticationRequired ) {
        transition.update.startFuture.complete( null ) ;
      }
    } catch( StateBody.StateTransitionException e ) {
      pingSchedule.cancel( true ) ;
      LOGGER.debug( "Swallowing exception " + e.getMessage() + "." ) ;
    }
  }

  /**
   * Called by {@link #connectPipeline()}, which is called by
   * {@link DownendConnector#justScheduleReconnect(long)} and {@link #start()}.
   */
  private void bootstrapFailedToConnect() {
    stateUpdater.notifyFailedConnectionAttempt() ;
    scheduleReconnectWithStateUpdate() ;
  }

  /**
   * Called by:
   * - {@link #pingNow()}
   * - {@link #pongTimeout(long)},
   * - {@link DownendSupervisionTier#channelInactive(io.netty.channel.ChannelHandlerContext)},
   * - {@link DownendSupervisionTier#exceptionCaught(io.netty.channel.ChannelHandlerContext, java.lang.Throwable)}.
   */
  private void noChannel() {
    LOGGER.debug( "Detected that " + Channel.class.getSimpleName() + " closed/exception raised." ) ;
    long connectTimeoutMs = stateUpdater.connectTimeoutMs( RANDOM ) ;
    final ScheduledFuture< ? > reconnectFuture = justScheduleReconnect( connectTimeoutMs ) ;
    try {
      StateUpdater.Transition transition = stateUpdater.update(
          stateBody -> stateBody.noChannel( reconnectFuture ) ) ;
      cancelPreviousAllFutures( transition ) ;
      if( transition.previous.reconnectFuture != null ) {
        transition.previous.reconnectFuture.cancel( true ) ;
      }
      setup.signonMaterializer.setProblemMessage(
          new SignonFailureNotice( SignonFailure.CONNECTION_REFUSED, "Disconnected." ) ) ;
      setup.signonMaterializer.waitForCancellation( () -> { } ) ;
      if( transition.previous.channel != null ) {
        transition.previous.channel.close() ;
      }
    } catch( StateBody.StateTransitionException e ) {
      reconnectFuture.cancel( true ) ;
      if( e.current.state == STOPPING ) {
        cancelAllFutures( e.current ) ;
        stateUpdater.update( StateBody::stopped ) ;
        e.current.stopFuture.complete( null ) ;
      } else if( e.current.state == STOPPED ) {
        LOGGER.debug( "Already " + e.current.state + "." ) ;
      } else {
        throw e ;
      }
    }
  }

  /**
   * Called by {@link DownendSupervisionTier#channelRead0(ChannelHandlerContext, Object)}
   * when receiving a {@link CloseWebSocketFrame}.
   */
  private void kickoutHappened() {
    StateUpdater.Transition update = stateUpdater.update( StateBody::emergencyStop ) ;
    if( update.previous.startFuture != null ) {
      update.previous.startFuture.completeExceptionally( new RuntimeException(
          "Kicked out, this exception is unlikely to happen" ) ) ;
    }
    if( update.previous.stopFuture != null ) {
      update.previous.stopFuture.complete( null ) ;
    }
    cancelAllFutures(
        update.previous.nextPingFuture,
        update.previous.nextPongTimeoutFuture,
        update.previous.reconnectFuture
    ) ;
  }

  /**
   * Called by {@link SessionDownendTier}.
   */
  private void signonHappened() {
    StateUpdater.Transition update = stateUpdater.update( StateBody::signedIn ) ;
    if( update.previous.startFuture != null ) {
      update.previous.startFuture.complete( null ) ;
    }
  }

  /**
   * Called by {@link SessionDownendTier}.
   */
  private void signonCancelled() {
    setup.changeWatcher.noSignon() ;
  }


// ==============
// Start and stop
// ==============

  /**
   * Starts connecting. If already called, returns the same {@code CompletableFuture} unless
   * disconnection happened meanwhile.
   */
  @Override
  public CompletableFuture< Void > start() {

    final CompletableFuture< Void > startFuture = new CompletableFuture<>() ;
    final StateUpdater.Transition transition ;
    try {
      transition = stateUpdater.update(
          stateBody -> stateBody.startConnecting( startFuture ) ) ;
    } catch( final StateBody.StateTransitionException e ) {
      if( e.current.state == CONNECTED ) {
        throw new IllegalStateException( "Already started" ) ;
      } else if( e.current.state == CONNECTING ) {
        cancelAllFutures( e.current.reconnectFuture ) ;
        throw new IllegalStateException( "Already starting" ) ;
      }
      throw e ;
    }

    if( transition.previous.state == STOPPED ) {
      setup.eventLoopGroup.execute( () -> {
        LOGGER.debug( "Starting " + this + " ..." ) ;
        try {
          connectPipeline() ;
        } catch( InterruptedException e ) {
          throw new RuntimeException( "Should not happen", e ) ;
        }
      } ) ;
    }

    final CompletableFuture< Void > completableFuture = MoreObjects.firstNonNull(
        transition.update.startFuture, transition.previous.startFuture ) ;
    checkNotNull( completableFuture, "There should be a non-null startFuture at this point" ) ;
    return completableFuture ;
  }


  /**
   * Terminates connection and further reconnection attempts.
   * Sends a {@link CloseWebSocketFrame} if it was in {@link State#CONNECTED} state.
   * After calling this method, it is possible to {@link #start()} once again.
   */
  @Override
  public CompletableFuture< Void > stop() {
    LOGGER.info( "Got requested to stop " + this + "." ) ;
    final StateUpdater.Transition transition ;
    try {
      transition = stateUpdater.update(
          stateBody -> stateBody.stopping( new CompletableFuture<>() ) ) ;
      cancelPreviousAllFutures( transition ) ;
      sessionDownendTier.clearSessionIdentifier() ;
      if( transition.previous.channel != null ) {
        LOGGER.debug( "Current state allow to really stop " + this + " ..." ) ;
        transition.previous.channel.writeAndFlush( new CloseWebSocketFrame() )
            .addListener( CLOSE )
            /** The {@link Channel} itself will call {@link #noChannel()}. */
        ;
      } else {
        stateUpdater.update( StateBody::stopped ) ;
        transition.update.stopFuture.complete( null ) ;
      }
      return transition.update.stopFuture ;
    } catch( StateBody.StateTransitionException e ) {
      if( e.current.state == STOPPED ) {
        LOGGER.debug( "Already in " + e.current.state + "." ) ;
      } else {
        throw e ;
      }
      return CompletableFuture.completedFuture( null ) ;
    }
  }



// =====================
// Connect and reconnect
// =====================

  private static final Random RANDOM = new Random() ;

  private void scheduleReconnectWithStateUpdate() {
    final ScheduledFuture< ? > reconnectFuture = justScheduleReconnect(
        stateUpdater.connectTimeoutMs( RANDOM ) ) ;
    final StateUpdater.Transition transition ;
    try {
      transition = stateUpdater.update(
          stateBody -> stateBody.planToReconnect( reconnectFuture ) ) ;
    } catch( final StateBody.StateTransitionException e ) {
      reconnectFuture.cancel( true ) ;
      if( e.current.state == STOPPED || e.current.state == STOPPING ) {
        return ;
      } else {
        throw e ;
      }
    }
    cancelPreviousAllFutures( transition ) ;
  }

  private ScheduledFuture< ? > justScheduleReconnect( final long connectTimeoutMs ) {
    final ScheduledFuture< ? > schedule = setup.eventLoopGroup.schedule(
        ( ScheduledInternally.Reconnect ) () -> {
          try {
            connectPipeline();
          } catch( final InterruptedException e ) {
            LOGGER.error( "Unexpected thread interruption.", e );
          }
        },
        connectTimeoutMs,
        TimeUnit.MILLISECONDS
    ) ;
    LOGGER.debug( "Scheduled next connection attempt as " + schedule +
        " in " + connectTimeoutMs + " ms." ) ;
    return schedule ;
  }


// =================
// Pipeline creation
// =================

  private void connectPipeline() throws InterruptedException {
    final Bootstrap bootstrap = new Bootstrap() ;
    bootstrap.group( setup.eventLoopGroup )
        .channel( NioSocketChannel.class )
        .option( ChannelOption.CONNECT_TIMEOUT_MILLIS, setup.primingTimeBoundary.connectTimeoutMs() )
        .remoteAddress( setup.uri.getHost(), setup.uri.getPort() )
        .handler( new ChannelInitializer< SocketChannel >() {
          @Override
          protected void initChannel( final SocketChannel socketChannel ) {
            configure( socketChannel.pipeline() ) ;
          }
        } )
    ;
    if( setup.internetProxyAccess != null ) {
      bootstrap.resolver( NoopAddressResolverGroup.INSTANCE ) ;
    }

    LOGGER.debug( "Connecting bootstrap ..." ) ;

    bootstrap.connect()
        .addListener( ( ChannelFutureListener ) future -> {
          final Throwable problem = future.cause() ;
          if( problem == null ) {
            final Channel channel = future.channel() ;
            try {
              stateUpdater.update( stateBody -> stateBody.channel( channel ) ) ;
            } catch( StateBody.StateTransitionException e ) {
              if( e.current.state == STOPPING || e.current.state == STOPPED ) {
                LOGGER.debug( "Cancelling reconnection using " + channel.id() +
                    " because already " + e.current.state + " ..." ) ;
                channel.close() ;
              } else {
                throw e ;
              }
            }
            LOGGER.debug( "Bootstrap did connect using " + channel.id() + ". " +
                "Now WebSocket handshake should happen." ) ;
          } else {
            LOGGER.debug(
                "Bootstrap did not connect (" + problem.getClass().getSimpleName() + ", " +
                "\"" + problem.getMessage() + "\")."
            ) ;
            bootstrapFailedToConnect() ;
          }
        } )
    ;
  }

  private DownendSupervisionTier.Claim createClaim() {
    return new DownendSupervisionTier.Claim() {
      @Override
      public WebSocketClientHandshaker createHandshaker() {
        return WebSocketClientHandshakerFactory.newHandshaker(
            setup.uri,
            WebSocketVersion.V13,
            null,
            false,
            new DefaultHttpHeaders(),
            setup.websocketFrameSizer.maximumPayloadSize,
            WebsocketTools.MASK_WEBSOCKET_FRAMES_FROM_CLIENT,
            ! WebsocketTools.MASK_WEBSOCKET_FRAMES_FROM_CLIENT
        ) ;
      }

      @Override
      public void reconfigure(
          final ChannelPipeline channelPipeline,
          final ConnectionDescriptor connectionDescriptor,
          final WebSocketFrameEncoder webSocketFrameEncoder,
          final WebSocketFrameDecoder webSocketFrameDecoder,
          final Executor executor
      ) {
        DownendConnector.this.reconfigure(
            channelPipeline,
            connectionDescriptor,
            webSocketFrameEncoder,
            webSocketFrameDecoder,
            executor
        ) ;
      }

      @Override
      public void afterWebsocketHandshake( final ConnectionDescriptor connectionDescriptor ) {
        DownendConnector.this.afterWebsocketHandshake( connectionDescriptor ) ;
      }

      @Override
      public void channelInactiveOrExceptionCaught( final ChannelHandlerContext channelHandlerContext ) {
        DownendConnector.this.noChannel() ;
      }

      @Override
      public void kickoutHappened( final ChannelHandlerContext channelHandlerContext ) {
        DownendConnector.this.kickoutHappened() ;
      }

      @Override
      public void problem( final Throwable cause ) {
        LOGGER.error( "Unhandled problem.", cause );
      }
    } ;
  }

  private void configure( ChannelPipeline channelPipeline ) {
    final ChannelHandler proxyHandler = newProxyHandlerMaybe( setup.internetProxyAccess ) ;
    if( proxyHandler != null ) {
      channelPipeline.addFirst( DownendTierName.HTTP_PROXY.tierName(), proxyHandler ) ;
    }

    if( setup.sslEngineFactory != null ) {
      channelPipeline.addLast(
          DownendTierName.SSL_HANDLER.tierName(),
          new SslHandler( setup.sslEngineFactory.newSslEngine() )
      ) ;
      ChannelTools.decorateWithLogging(
          channelPipeline, DownendTierName.SSL_HANDLER.tierName(), false, false ) ;
    }

    channelPipeline.addLast(
        DownendTierName.INITIAL_HTTP_CLIENT_CODEC.tierName(), new HttpClientCodec() ) ;

    ChannelTools.decorateWithLogging(
        channelPipeline,
        DownendTierName.INITIAL_HTTP_CLIENT_CODEC.tierName(),
        false,
        false
    ) ;

    channelPipeline.addLast(
        DownendTierName.INITIAL_HTTP_OBJECT_AGGREGATOR.tierName(),
        // TODO: get sure we can use WebSocket's value.
        new HttpObjectAggregator( setup.websocketFrameSizer.fragmentSize ) ) ;

    channelPipeline.addLast(
        DownendTierName.SUPERVISION.tierName(),
        downendSupervisionTier
    ) ;

    channelPipeline.addLast(
        DownendTierName.PING_PONG.tierName(),
        new PongTier( DownendConnector.this::pongFrameReceived )
    ) ;

    channelPipeline.addLast(
        DownendTierName.COMMAND_CODEC.tierName(),
        new CommandWebsocketCodecDownendTier<>(
            setup.endpointSpecificCodec,
            setup.commandDecoder
        )
    ) ;

    channelPipeline.addLast(
        DownendTierName.COMMAND_RECEIVER.tierName(),
        new CommandReceiverTier<>( setup.commandReceiver ) ) ;

    ChannelTools.decorateWithLogging(
        channelPipeline,
        DownendTierName.COMMAND_CODEC.tierName(),
        false,
        true
    ) ;

    channelPipeline.addLast(
        DownendTierName.CATCHER.tierName(),
        new ChannelInboundHandlerAdapter() {
          @Override
          public void exceptionCaught(
              final ChannelHandlerContext channelHandlerContext,
              final Throwable cause
          ) throws Exception {
            LOGGER.error( "Caught " + channelPipeline + ": ", cause ) ;
          }
        }
    ) ;

    // ChannelTools.dumpPipeline( channelPipeline ) ;
  }

  private void reconfigure(
      final ChannelPipeline channelPipeline,
      final ConnectionDescriptor connectionDescriptor,
      @SuppressWarnings( "UnusedParameters" ) final WebSocketFrameEncoder webSocketFrameEncoder,
      final WebSocketFrameDecoder webSocketFrameDecoder,
      final Executor executor
  ) {
    channelPipeline.remove( channelPipeline.get(
        DownendTierName.INITIAL_HTTP_OBJECT_AGGREGATOR.tierName() ) ) ;

    final ChannelHandlerContext channelHandlerContext = channelPipeline.context(
        DownendTierName.INITIAL_HTTP_CLIENT_CODEC.tierName() ) ;

    final HttpClientCodec codec = ( HttpClientCodec ) channelHandlerContext.handler() ;
    // Remove the encoder part of the codec as the user may start writing frames
    // after this method returns.
    codec.removeOutboundHandler() ;

    channelPipeline.addAfter( channelHandlerContext.name(),
        DownendTierName.WS_DECODER.tierName(), webSocketFrameDecoder ) ;

    {
      /** Moving automatically-added {@link WebSocketEncoder} to the right place, by default
       * it takes place just after {@link DownendTierName#HTTP_PROXY}.*/
      channelPipeline.remove( DownendTierName.WS_ENCODER.tierName() ) ;
      channelPipeline.addAfter( DownendTierName.WS_DECODER.tierName(),
          DownendTierName.WS_ENCODER.tierName(), webSocketFrameEncoder ) ;
    }

    channelPipeline.addAfter(
        DownendTierName.WS_ENCODER.tierName(),
        DownendTierName.WS_FRAME_FRAGMENTER.tierName(),
        new WebsocketFragmenterTier( setup.websocketFrameSizer.fragmentSize )
    ) ;

    channelPipeline.addAfter(
        DownendTierName.WS_DECODER.tierName(),
        DownendTierName.WS_FRAME_AGGREGATOR.tierName(),
        new WebSocketFrameAggregator(
//            1
            Integer.MAX_VALUE
        )
    ) ;


    if( setup.commandInterceptorFactory != null ) {
      channelPipeline.addAfter(
          DownendTierName.COMMAND_CODEC.tierName(),
          DownendTierName.COMMAND_OUTBOUND_INTERCEPTOR.tierName(),
          new DownendCommandInterceptorTier( setup.commandInterceptorFactory.createNew() )
      ) ;
    }



    // Delay the removal of the decoder so the user can setup the pipeline if needed
    // to handle WebSocketFrame messages.
    // See https://github.com/netty/netty/issues/4533
    executor.execute( () -> {
      channelPipeline.remove( codec ) ;
      // ChannelTools.dumpPipeline( channelPipeline ) ;
    } ) ;

    // Session-related stuff starts here.

    if( connectionDescriptor.authenticationRequired ) {
      checkState( setup.signonMaterializer != null ) ;
      channelPipeline.addBefore(
          DownendTierName.COMMAND_CODEC.tierName(),
          DownendTierName.SESSION_PHASE_CODEC.tierName(),
          new SessionPhaseWebsocketCodecDownendTier()
      ) ;
      channelPipeline.addBefore(
          DownendTierName.COMMAND_CODEC.tierName(),
          DownendTierName.SESSION.tierName(),
          sessionDownendTier
      ) ;
    }


  }


  private static ProxyHandler newProxyHandlerMaybe(
      final InternetProxyAccess internetProxyAccess
  ) {
    if( internetProxyAccess == null ) {
      return null ;
    } else {
      final SocketAddress socketAddress = new InetSocketAddress(
          internetProxyAccess.hostPort.hostname.asString(),
          internetProxyAccess.hostPort.port
      ) ;
      if( internetProxyAccess.credential == null ) {
        return new Http11ProxyHandler( socketAddress ) ;
//        return new HttpProxyHandler( socketAddress ) ;
      } else {
//        return new HttpProxyHandler(
//            socketAddress,
//            internetProxyAccess.credential.getLogin(),
//            internetProxyAccess.credential.getPassword()
//        ) ;
        return new Http11ProxyHandler(
            socketAddress,
            internetProxyAccess.credential.getLogin(),
            internetProxyAccess.credential.getPassword()
        ) ;
      }
    }
  }

// =============
// Ping and Pong
// =============

  private void schedulePing() {
    final StateBody current = stateUpdater.current() ;
    ScheduledFuture< ? > schedule = setup.eventLoopGroup.schedule(
        this::pingNow,
        current.connectionDescriptor.timeBoundary.pingIntervalMs(),
        TimeUnit.MILLISECONDS
    ) ;
    final StateUpdater.Transition transition = stateUpdater.update(
        stateBody -> stateBody.planNextPing( schedule ) ) ;
    cancelAllFutures(
        transition.previous.nextPingFuture,
        transition.previous.nextPongTimeoutFuture
    ) ;
  }

  /**
   * Always accessed from {@link EventLoop} so we don't need to synchronize or use
   * an {@code AtomicLong}.
   */
  private long pingCounter = 0 ;

  private void pingNow() {
    StateBody stateBody = stateUpdater.current() ;
    final long currentPingCounter = pingCounter++ ;
    final int pongTimeoutMs = stateUpdater.pongTimeoutMs() ;
    if( stateBody.channel == null ) {
      LOGGER.warn( "Can't ping with a null " + Channel.class.getSimpleName() + ", skipping." ) ;
    } else {
      final ByteBuf buffer = stateBody.channel.alloc().buffer() ;
      buffer.writeLong( currentPingCounter ) ;
      final PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame( buffer ) ;
      final ChannelFuture channelFuture = send( stateUpdater.current(), pingWebSocketFrame ) ;
      if( channelFuture != null ) {
        channelFuture.addListener( future -> {
          if( future.isSuccess() ) {
            final ScheduledFuture< ? > schedule = setup.eventLoopGroup.schedule(
                ( ScheduledInternally.PongTimeout ) () -> pongTimeout( currentPingCounter ),
                pongTimeoutMs,
                TimeUnit.MILLISECONDS
            ) ;
            try {
              final StateUpdater.Transition transition =
                  stateUpdater.update( stateBody1 -> stateBody1.planPongTimeout( schedule ) ) ;
              cancelAllFutures( transition.previous.nextPongTimeoutFuture ) ;
            } catch( final StateBody.StateTransitionException e ) {
              LOGGER.debug( "Failed to plan pong timeout for " + this + "." ) ;
            }
            LOGGER.debug( "Ping #" + currentPingCounter + " sent, scheduled pong timeout " +
                "in " + pongTimeoutMs + " ms for ping #" + currentPingCounter + "." ) ;
          } else {
            LOGGER.debug( "Ping #" + currentPingCounter + " failed because of ",
                future.cause().getClass().getName() ) ;
            noChannel() ;
          }
        } ) ;
      }
    }
  }

  private void pongFrameReceived( final Long pingCounter ) {
    LOGGER.debug( "Received Pong for ping #" + pingCounter + "." ) ;
    schedulePing() ;
  }

  private void pongTimeout( final long pingCounter ) {
    LOGGER.debug( "Pong timeout happened for ping #" + pingCounter + "." ) ;
    noChannel() ;
  }

}
