package io.github.otcdlink.chiron.downend;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import io.github.otcdlink.chiron.codec.CommandBodyDecoder;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.command.codec.Codec;
import io.github.otcdlink.chiron.downend.tier.CommandReceiverTier;
import io.github.otcdlink.chiron.downend.tier.CommandWebsocketCodecDownendTier;
import io.github.otcdlink.chiron.downend.tier.DownendCommandInterceptorTier;
import io.github.otcdlink.chiron.downend.tier.DownendSupervisionTier;
import io.github.otcdlink.chiron.downend.tier.DownendTierName;
import io.github.otcdlink.chiron.downend.tier.PongTier;
import io.github.otcdlink.chiron.downend.tier.SessionDownendTier;
import io.github.otcdlink.chiron.downend.tier.SessionPhaseWebsocketCodecDownendTier;
import io.github.otcdlink.chiron.middle.ChannelTools;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle;
import io.github.otcdlink.chiron.middle.session.SignonFailure;
import io.github.otcdlink.chiron.middle.session.SignonFailureNotice;
import io.github.otcdlink.chiron.middle.tier.CommandInterceptor;
import io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import io.github.otcdlink.chiron.middle.tier.WebsocketFragmenterTier;
import io.github.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import io.github.otcdlink.chiron.toolbox.Credential;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.internet.InternetProxyAccess;
import io.github.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
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
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.CONNECTING;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.PROBLEM;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.SIGNED_IN;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.STOPPED;
import static io.github.otcdlink.chiron.downend.DownendConnector.State.STOPPING;

/**
 * Establishes and keeps alive a WebSocket connection to some given host.
 *
 * <h2>Signon sequence</h2>
 * This is the initial sequence when there is no {@link SessionIdentifier}:
 * <pre>
 * {@link DownendConnector}                        {@code UpendConnector}
 *    |                                                |
 *    |  >--- 1: HTTP connection --------------------> |
 *    |  <---------------- 2: WebSocket handshake ---< |
 *    |  >--- 3: Primary signon ---------------------> | {@link SessionLifecycle.PrimarySignon}
 *    |  <----- 4: Missing Secondary code + token ---< | {@link SessionLifecycle.SecondarySignonNeeded}
 *    |  >--- 5: Secondary code + token -------------> | {@link SessionLifecycle.SecondarySignon}
 *    |  <----------------- 6: Session identifier ---< | {@link SessionLifecycle.SessionValid}
 * </pre>
 *
 * This is the Resignon sequence (there is one {@link SessionIdentifier} that may be still valid):
 * <pre>
 * {@link DownendConnector}                        {@code UpendConnector}
 *    |                                                |
 *    |  >--- 1: HTTP (re)connection ----------------> |
 *    |  <---------------- 2: WebSocket handshake ---< |
 *    |  >--- 3: Resubmit {@link SessionIdentifier} ---------> | {@link SessionLifecycle.Resignon}
 *    |  <----------------- 6: Session identifier ---< | {@link SessionLifecycle.SessionValid}
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

  private final DownendStateUpdater stateUpdater ;

  private volatile Channel channel = null ;

  private final SessionDownendTier sessionDownendTier;


  private final DownendSupervisionTier downendSupervisionTier =
      new DownendSupervisionTier( createClaim() ) ;


  public DownendConnector( final Setup< ENDPOINT_SPECIFIC, DOWNWARD_DUTY > setup ) {
    this.setup = checkNotNull( setup ) ;
    this.stateUpdater = new DownendStateUpdater(
        LOGGER, this::toString, STOPPED, setup.changeWatcher ) ;
    LOGGER.info( "Created " + this + " with " + setup + "." ) ;
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

  private static void reschedule(
      final AtomicReference< ScheduledFuture< ? > > futureReference,
      final ScheduledFuture< ? > scheduledFuture
  ) {
    final ScheduledFuture< ? > previous = futureReference.getAndSet( scheduledFuture ) ;
    if( previous != null ) {
      previous.cancel( false ) ;
    }
  }

  private String lastMaterializedLogin() {
    return ( ( SignonMaterializerInterceptor ) setup.signonMaterializer ).lastLogin() ;
  }


// ============
// TimeBoundary
// ============

  /**
   * A mutable reference that keeps last value received as {@link ConnectionDescriptor},
   * so we can {@link TimeBoundary.ForDownend#reconnectDelayMs(Random)} after a connection broke.
   */
  private final AtomicReference< TimeBoundary.ForDownend > downendTimeBoundary =
      new AtomicReference<>() ;

  private TimeBoundary.ForDownend timeBoundaryOrNull() {
    return downendTimeBoundary.get() ;
  }

  private TimeBoundary.ForDownend timeBoundary() {
    final TimeBoundary.ForDownend downendTimeBoundary = this.downendTimeBoundary.get() ;
    checkState( downendTimeBoundary != null ) ;
    return downendTimeBoundary ;
  }

  private void timeBoundary( final TimeBoundary.ForAll timeBoundary ) {
    checkNotNull( timeBoundary ) ;
    downendTimeBoundary.set( timeBoundary ) ;
    LOGGER.info( "Did set " + timeBoundary + "." );
  }



// ====
// Send
// ====

  /**
   * Attempts to send a {@link Command}, silently failing if something goes wrong.
   * Implementation performs a lazy state check (based on instant value of
   * {@link DownendStateUpdater#state()}) which is wrong if sending occurs in a non-{@link Channel}
   * thread, while {@link #start()} or {@link #stop()} occured.
   * For this reason, the {@link TrackerCurator} should take care of timeouts, or other explicit
   * errors. The {@link ChangeWatcher} receives a {@link Change.Problem} at the first problem
   * encountered.
   * <p>
   * Passing some kind of {@link Promise} to the {@link #send(Command)} method would be redundant
   * with {@link Change.Problem} notification, considering that a {@link Change.Problem}
   * represents an unforeseen problem. It can occur only once in a {@link DownendConnector}'s life,
   * and should be fixed by a code change.
   */
  @Override
  public void send( final Command< ENDPOINT_SPECIFIC, UPWARD_DUTY > command ) {
    LOGGER.debug( "Sending " + command + " on " + this + " ..." ) ;
    send( ( Object ) command ) ;
  }

  private ChannelFuture send( final Object outbound ) {
    checkNotNull( outbound ) ;

    final State currentState = stateUpdater.state() ;
    if( readiness.contains( currentState ) ) {
      final ChannelPromise promise = channel.newPromise() ;
      promise.addListener( future -> {
        final Throwable cause = future.cause() ;
        if( cause != null ) {
          fatalProblemHappened( cause ) ;
        }
      } ) ;
      return channel.writeAndFlush( outbound, promise ) ;
    } else {
      // The contract is to let a timeout happen quietly.
      LOGGER.warn( "State is currently " + currentState + ", can't send " + outbound + "." ); ;
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
    STOPPED, CONNECTING, CONNECTED, SIGNED_IN, STOPPING, PROBLEM, ;
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
        return "TODO" ;
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
        super( PROBLEM ) ;
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
    private final AtomicReference< String > lastLogin = new AtomicReference<>() ;

    /**
     * Avoids superfluous notifications so tests remain simple.
     * No need to synchronize access, changes run in a {@link Channel#eventLoop()}.
     */
    private boolean dialogOpen = false ;


    public String lastLogin() {
      return lastLogin.get() ;
    }

    private SignonMaterializerInterceptor( final SignonMaterializer delegate ) {
      this.delegate = checkNotNull( delegate ) ;
    }

    @Override
    public void readCredential( final Consumer< Credential > credentialConsumer ) {
      dialogOpen = true ;
      delegate.readCredential( credential -> {
        lastLogin.set( credential == null ? null : credential.getLogin() ) ;
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
    return stateUpdater.state() ;
  }

  private final AtomicReference< CompletableFuture< ? > > startFutureReference = new AtomicReference<>() ;

  private void connectionHappened( final ConnectionDescriptor connectionDescriptor ) {
    readiness = connectionDescriptor.authenticationRequired ?
        ImmutableSet.of( State.CONNECTED, SIGNED_IN ) : ImmutableSet.of( State.CONNECTED ) ;
    timeBoundary( connectionDescriptor.timeBoundary ) ;
    stateUpdater.update( new Change.SuccessfulConnection( connectionDescriptor ) ) ;
    setup.signonMaterializer.setProgressMessage( null ) ;

    if( ! connectionDescriptor.authenticationRequired ) {
      final CompletableFuture< ? > currentSafeConcluder = startFutureReference.getAndSet( null ) ;
      if( currentSafeConcluder != null ) {
        currentSafeConcluder.complete( null ) ;
      }
      schedulePing() ;
    }
  }

  private void connectionFailed() {
    stateUpdater.notifyFailedConnectionAttempt() ;
    scheduleReconnect() ;
  }

  private void disconnectionHappened() {
    final State currentState = stateUpdater.state() ;
    if( currentState != STOPPING && currentState != STOPPED ) {
      // TODO: use a predicate to indicate a blackhole state.
      stateUpdater.update( State.CONNECTING ) ;
      setup.signonMaterializer.setProblemMessage(
          new SignonFailureNotice( SignonFailure.CONNECTION_REFUSED, "Disconnected." ) ) ;
      setup.signonMaterializer.waitForCancellation( () -> { } ) ;
      scheduleReconnect() ;
    }
  }

  private void kickoutHappened() {
    nullifyChannel() ;
    stateUpdater.update( STOPPED ) ;
  }

  private void signonHappened() {
    stateUpdater.update( new Change.SuccessfulSignon( setup.url, lastMaterializedLogin() ) ) ;
    final CompletableFuture< ? > currentStartFuture = startFutureReference.getAndSet( null ) ;
    if( currentStartFuture != null ) {
      currentStartFuture.complete( null ) ;
    }
    schedulePing() ;
  }

  private void signonCancelled() {
    setup.changeWatcher.noSignon() ;
  }

  private void fatalProblemHappened( final Throwable cause ) {
    stateUpdater.update( new Change.Problem( cause ) ) ;
    final CompletableFuture< ? > startFuture = startFutureReference.getAndSet( null ) ;
    if( startFuture != null ) {
      startFuture.completeExceptionally( cause ) ;
    }
    final Channel currentChannel = this.channel ;
    if( currentChannel != null ) {
      currentChannel.close() ;
      nullifyChannel() ;
    }

    LOGGER.debug(
        "Notified " + setup.changeWatcher + " of problem: " +
        cause.getClass().getName() + ".",
        cause
    ) ;
  }

  /**
   * Start connecting asynchronously.
   * {@link CompletableFuture#join()} blocks until failure, or one of {@link State#CONNECTED}.
   */
  @Override
  public CompletableFuture< ? > start() {

    final CompletableFuture< ? > startFuture = new CompletableFuture<>() ;
    startFutureReference.set( startFuture ) ;

    setup.eventLoopGroup.execute( () -> {
      LOGGER.debug( "Starting " + this + " ..." ) ;
      try {
        stateUpdater.update( State.CONNECTING, state -> false, STOPPED ) ;
        buildPipeline() ;
      } catch( final Exception e ) {
        if( e instanceof SocketException ) {
          connectionFailed() ;
        } else {
          if( ! ( e instanceof IllegalStateException ) ) {
            // This is more severe than a bad transition which is mainly an error in caller code,
            // so we switch to a "dead" state.
            stateUpdater.update( new Change.Problem( e ) ) ;
          }
          final CompletableFuture< ? > currentStartConcluder = startFutureReference.getAndSet( null ) ;
          if( currentStartConcluder != null ) {
            currentStartConcluder.completeExceptionally( e ) ;
          }
        }
      }
    } ) ;

    return startFuture ;
  }


  /**
   * Terminates connection and further reconnection attempts.
   * Sends a {@link CloseWebSocketFrame} if it was in {@link State#CONNECTED} state.
   * After calling this method, it is possible to {@link #start()} once again.
   */
  @Override
  public CompletableFuture< ? > stop() {
    if( stateUpdater.state() == STOPPED ) {
      LOGGER.info( "Already stopped " + this + "." ) ;
      return CompletableFuture.completedFuture( null ) ;
    }

    LOGGER.info( "Stopping " + this + " ..." ) ;
    cancelReconnect() ;
    final State previousState = stateUpdater.update( STOPPING ) ;
    if( previousState == State.CONNECTED || previousState == SIGNED_IN ||
        previousState == CONNECTING
    ) {
      final Channel currentChannel = this.channel ;
      if( currentChannel != null ) {
        final CompletableFuture< ? > safeConcluder = new CompletableFuture<>() ;
        currentChannel.writeAndFlush( new CloseWebSocketFrame() ) ;
        currentChannel.close().addListener( ( ChannelFutureListener ) future -> {
          stateUpdater.update( STOPPED ) ;
          LOGGER.info( "Stopped " + DownendConnector.this + "." ) ;
          if( future.isSuccess() ) {
            safeConcluder.complete( null ) ;
          } else {
            safeConcluder.completeExceptionally( future.cause() ) ;
          }
          nullifyChannel() ;
        } ) ;
        return safeConcluder ;
      } else {
        return CompletableFuture.completedFuture( null ) ;
      }
    } else if( previousState == PROBLEM ) {
      final CompletableFuture< ? > failed = new CompletableFuture<>() ;
      failed.completeExceptionally( new IllegalStateException( "Not stopped as already in " + PROBLEM + " state" ) ) ;
      return failed ;
    } else {
      return CompletableFuture.completedFuture( null ) ;
    }
  }


// =====================
// Connect and reconnect
// =====================

  private final AtomicReference< ScheduledFuture< ? > > reconnectFutureReference =
      new AtomicReference<>() ;

  private void cancelReconnect() {
    reschedule( reconnectFutureReference, null ) ;
  }

  private static final Random RANDOM = new Random() ;

  private void scheduleReconnect() {
    final TimeBoundary.ForDownend timeBoundary = timeBoundaryOrNull() ;
    final long delay ;
    if( timeBoundary == null ) {
      // TODO: save last obtained TimeBoundary or at least delay range.
      delay = setup.primingTimeBoundary.connectTimeoutMs() ;
    } else {
      delay = timeBoundary.reconnectDelayMs( RANDOM ) ;
    }
    reschedule( nextPingFutureReference, null ) ;
    reschedule( nextPongTimeoutFutureReference, null ) ;
    if( stateUpdater.update( State.CONNECTING ) == null ) {

      LOGGER.debug( "Scheduling next connection attempt in " + delay + " ms ..." ) ;
      final ScheduledFuture< ? > reconnectFuture = setup.eventLoopGroup.schedule(
          () -> {
            try {
              buildPipeline() ;
            } catch( final InterruptedException e ) {
              LOGGER.error( "Could not connect.", e ) ;
            }
          },
          delay,
          TimeUnit.MILLISECONDS
      ) ;
      reschedule( reconnectFutureReference, reconnectFuture ) ;
    } else {
      LOGGER.debug( "Current state means skipping further reconnection attempts." ) ;
    }
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
            setup.websocketFrameSizer.maximumPayloadSize
            // Client must mask the frame it sends, specification says they are invalid otherwise.
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
        DownendConnector.this.connectionHappened( connectionDescriptor ) ;
      }

      @Override
      public void disconnectionHappened( final ChannelHandlerContext channelHandlerContext ) {
        DownendConnector.this.disconnectionHappened() ;
        nullifyChannel() ;
      }

      @Override
      public void kickoutHappened( final ChannelHandlerContext channelHandlerContext ) {
        DownendConnector.this.kickoutHappened() ;
        nullifyChannel() ;
      }

      @Override
      public void problem( final Throwable cause ) {
        DownendConnector.this.fatalProblemHappened( cause ) ;
      }
    } ;
  }

  private void buildPipeline() throws InterruptedException {
    final Bootstrap bootstrap = new Bootstrap() ;
    bootstrap.group( setup.eventLoopGroup )
        .channel( NioSocketChannel.class )
        .option( ChannelOption.CONNECT_TIMEOUT_MILLIS, setup.primingTimeBoundary.connectTimeoutMs() )
        .remoteAddress( setup.uri.getHost(), setup.uri.getPort() )
        .handler( new ChannelInitializer< SocketChannel >() {
          @Override
          protected void initChannel( final SocketChannel socketChannel ) {
            final ChannelPipeline channelPipeline = socketChannel.pipeline() ;

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
            nullifyChannel() ;
            channel = future.channel() ;
            LOGGER.debug( "Bootstrap did connect. Now WebSocket handshake should happen." ) ;
          } else {
            LOGGER.debug(
                "Bootstrap did not connect " +
                    "(" + problem.getClass().getSimpleName() + ", \"" + problem.getMessage() + "\")."
            ) ;
            connectionFailed() ;
          }
        } )
    ;
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



  private void nullifyChannel() {
    if( channel != null ) {
      channel.close() ;
      channel = null ;
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

  /**
   * Change only occurs from {@link Setup#eventLoopGroup}'s thread.
   */
  private final AtomicReference< ScheduledFuture< ? > > nextPingFutureReference =
      new AtomicReference<>() ;

  /**
   * Change only occurs from {@link Setup#eventLoopGroup}'s thread.
   */
  private final AtomicReference< ScheduledFuture< ? > > nextPongTimeoutFutureReference =
      new AtomicReference<>() ;


  private void schedulePing() {
    final TimeBoundary.ForDownend timeBoundary = timeBoundary() ;
    LOGGER.debug( "Scheduling next ping in " + timeBoundary.pingIntervalMs() + " ms ..." ) ;
    reschedule( nextPongTimeoutFutureReference, null ) ;
    reschedule(
        nextPingFutureReference,
        setup.eventLoopGroup.schedule(
            this::pingNow,
            timeBoundary.pingIntervalMs(),
            TimeUnit.MILLISECONDS
        )
    ) ;
  }

  private long pingCounter = 0 ;

  /**
   * To call only from {@link DownendConnector.Setup#eventLoopGroup}'s thread.
   */
  protected void pingNow() {
    if( readiness.contains( state() ) ) {
      final long currentPingCounter = pingCounter++ ;
      final int pongTimeoutMs = timeBoundary().pongTimeoutMs() ;
      final ByteBuf buffer = Unpooled.buffer() ;
      buffer.writeLong( currentPingCounter ) ;
      final PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame( buffer ) ;
      send( pingWebSocketFrame ).addListener( future -> {
        if( future.isSuccess() ) {
          reschedule(
              nextPongTimeoutFutureReference,
              setup.eventLoopGroup.schedule(
                  () -> pongTimeout( currentPingCounter ),
                  pongTimeoutMs,
                  TimeUnit.MILLISECONDS
              )
          ) ;
          LOGGER.debug( "Ping #" + currentPingCounter + " sent, scheduled pong timeout " +
              "in " + pongTimeoutMs + " ms for ping #" + currentPingCounter + "." ) ;
        } else {
          LOGGER.error( "Ping #" + currentPingCounter + " failed.", future.cause() ) ;
          channel.close() ;
          disconnectionHappened() ;
        }
      } ) ;
    }
  }

  private void pongFrameReceived( final Long pingCounter ) {
    if( readiness.contains( state() ) ) {
      LOGGER.debug( "Received Pong for ping #" + pingCounter + "." ) ;
      schedulePing() ;
    } else {
      reschedule( nextPongTimeoutFutureReference, null ) ;
    }
  }

  private void pongTimeout( final long pingCounter ) {
    LOGGER.debug( "Pong timeout happened for ping #" + pingCounter + "." ) ;
    channel.close() ;
    disconnectionHappened() ;
  }

  public static void main( final String... arguments ) {
    class Stopwatch {
      private Stopwatch( final String message, final Runnable runnable ) {
        LOGGER.info( message ) ;
        final long systemNanosStart = System.nanoTime() ;
        runnable.run() ;
        final long systemNanosEnd = System.nanoTime() ;
        LOGGER.info( "It took " + ( systemNanosEnd - systemNanosStart ) + " ns." ) ;
      }
    }

    new Stopwatch( "Creating a random object ...", () -> new Random().nextInt( 1000 ) ) ;
    new Stopwatch( "Looping for a lot of iterations ...",
        () -> { for( int i = 0 ; i < 2000 ; i ++ ) {} } ) ;
  }
}
