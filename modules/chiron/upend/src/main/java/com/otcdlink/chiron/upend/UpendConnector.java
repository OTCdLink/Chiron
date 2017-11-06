package com.otcdlink.chiron.upend;

import com.google.common.base.Joiner;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.RenderingAwareDesignator;
import com.otcdlink.chiron.designator.SendingAware;
import com.otcdlink.chiron.middle.ChannelTools;
import com.otcdlink.chiron.middle.CommandFailureDuty;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.middle.tier.WebsocketFragmenterTier;
import com.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import com.otcdlink.chiron.toolbox.StateHolder;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.toolbox.catcher.Catcher;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import com.otcdlink.chiron.toolbox.security.SslEngineFactory;
import com.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer;
import com.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import com.otcdlink.chiron.upend.session.SessionSupervisor;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;
import com.otcdlink.chiron.upend.session.command.RenderingAwareCommand;
import com.otcdlink.chiron.upend.tier.CatcherTier;
import com.otcdlink.chiron.upend.tier.CommandReceiverTier;
import com.otcdlink.chiron.upend.tier.CommandWebsocketCodecUpendTier;
import com.otcdlink.chiron.upend.tier.HttpRequestRelayerTier;
import com.otcdlink.chiron.upend.tier.PongTier;
import com.otcdlink.chiron.upend.tier.SessionEnforcerTier;
import com.otcdlink.chiron.upend.tier.SessionPhaseWebsocketCodecUpendTier;
import com.otcdlink.chiron.upend.tier.UpendCommandInterceptorTier;
import com.otcdlink.chiron.upend.tier.UpendTierName;
import com.otcdlink.chiron.upend.tier.UpendUpgradeTier;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.otcdlink.chiron.upend.UpendConnector.State.CREATED;
import static com.otcdlink.chiron.upend.UpendConnector.State.STARTED;
import static com.otcdlink.chiron.upend.UpendConnector.State.STARTING;
import static com.otcdlink.chiron.upend.UpendConnector.State.STOPPED;
import static com.otcdlink.chiron.upend.UpendConnector.State.STOPPING;

/**
 * Creates a Netty-based HTTP(S) server listening on one given TCP port.
 * It can act as a pure HTTP(S) server for serving HTML pages, or upgrade to WebSockets,
 * depending on given {@link Setup}.
 * In both case, it decodes {@link FullHttpRequest} or {@link WebSocketFrame} into {@link Command}
 * objects.
 * Websockets and HTTP requests may be sessionless. Sessions are enabled through an
 * {@link OutwardSessionSupervisor}.
 *
 * @param <DOWNWARD_DUTY> triggers special behavior if implements {@link CommandFailureDuty}.
 */
public class UpendConnector<
    UPWARD_DUTY,
    DOWNWARD_DUTY
> {
  private static final Logger LOGGER = LoggerFactory.getLogger( UpendConnector.class ) ;

  /**
   * TODO more features:
   *   - Throttling. No need to run a special Reactor stage.
   *   - {@link Catcher} to handle all unexpected situations.
   * We can do that through custom {@link ChannelHandler}s (we could narrow this contract),
   * with a special {@link Setup} flavors that exposes higher-level contracts and generates
   * appropriate {@link ChannelHandler}s.
   */
  public static class Setup< UPWARD_DUTY > {

    public final EventLoopGroup eventLoopGroup ;

    public final InetSocketAddress listenAddress ;

    public final SslEngineFactory.ForServer sslEngineFactory ;

    public final URL websocketUrl ;

    public final OutwardSessionSupervisor< Channel, InetAddress > sessionSupervisor ;

    /**
     * Should forward {@link Command} objects to some execution queue (not process them
     * synchronously, except inside tests).
     */
    public final CommandConsumer< Command<Designator, UPWARD_DUTY > > commandConsumer ;

    public final Designator.Factory designatorFactory ;

    public final String applicationVersion ;

    public final TimeBoundary.ForAll initialTimeBoundary ;

    public final CommandBodyDecoder< Designator, UPWARD_DUTY > websocketCommandDecoder ;

    public final HttpRequestRelayer authenticatedHttpRequestRelayer ;

    public final HttpRequestRelayer immediateHttpRequestRelayer ;

    public final CommandInterceptor.Factory commandInterceptorFactory ;

    public final WebsocketFrameSizer websocketFrameSizer ;


    private static final Pattern WEBSOCKET_PATH_PATTERN =
        Pattern.compile( "(/([a-zA-Z0-9\\-_]+))*/?" ) ;



    /**
     * This constructor supports the {@link #channelRegistrationHacker} for hacking a {@link Channel}
     * from tests; production code should not call it directly.
     * This constructor performs every check needed, other constructors should call it.
     */
    public Setup(
        final EventLoopGroup eventLoopGroup,
        final InetSocketAddress listenAddress,
        final SslEngineFactory.ForServer sslEngineFactory,
        final String websocketUrlPath,
        final String applicationVersion,
        final OutwardSessionSupervisor<Channel, InetAddress> sessionSupervisor,
        final CommandConsumer<Command<Designator, UPWARD_DUTY>> commandConsumer,
        final Designator.Factory designatorFactory,
        final CommandBodyDecoder<Designator, UPWARD_DUTY> websocketCommandDecoder,
        final HttpRequestRelayer immediateHttpRequestRelayer,
        final HttpRequestRelayer authenticatedHttpRequestRelayer,
        final CommandInterceptor.Factory commandInterceptorFactory,
        final TimeBoundary.ForAll initialTimeBoundary,
        final WebsocketFrameSizer websocketFrameSizer
    ) {

      // Socket stuff
      this.eventLoopGroup = checkNotNull( eventLoopGroup ) ;
      this.listenAddress = checkNotNull( listenAddress ) ;
      this.sslEngineFactory = sslEngineFactory ;

      // Authentication
      if( commandConsumer == null ) {
        checkArgument(
            sessionSupervisor == null,
            "If there is a null " + CommandConsumer.class.getSimpleName() +
                " then " + SessionSupervisor.class.getSimpleName() + " must be null, too"
        ) ;
      }
      this.sessionSupervisor = sessionSupervisor ;

      // Other
      if( commandConsumer == null ) {
        checkArgument( designatorFactory == null ) ;
        checkArgument( websocketCommandDecoder == null ) ;
        checkArgument( authenticatedHttpRequestRelayer == null ) ;
        checkArgument( commandInterceptorFactory == null ) ;
      }
      this.commandInterceptorFactory = commandInterceptorFactory;
      this.commandConsumer = commandConsumer ;
      this.designatorFactory = designatorFactory ;
      this.applicationVersion = checkNotNull( applicationVersion ) ;
      this.initialTimeBoundary = initialTimeBoundary ;

      // Websocket
      if( websocketUrlPath == null ) {
        checkArgument( websocketCommandDecoder == null ) ;
        checkArgument( initialTimeBoundary == null ) ;
        checkArgument( websocketFrameSizer == null ) ;
        websocketUrl = null ;
        this.websocketFrameSizer = null ;
      } else {
        checkArgument(
            WEBSOCKET_PATH_PATTERN.matcher( websocketUrlPath ).matches(),
            "Bad path: '" + websocketUrlPath + "', should match " +
                WEBSOCKET_PATH_PATTERN.pattern()
        ) ;
        this.websocketUrl = UrxTools.websocketUrlQuiet(
            sslEngineFactory != null,
            listenAddress.getHostName(),
            listenAddress.getPort(),
            websocketUrlPath
        ) ;
        this.websocketFrameSizer = checkNotNull( websocketFrameSizer ) ;
      }
      this.websocketCommandDecoder = websocketCommandDecoder ;



      // HTTP
      if( sessionSupervisor == null ) {
        checkArgument( authenticatedHttpRequestRelayer == null ) ;
      }
      this.immediateHttpRequestRelayer = immediateHttpRequestRelayer ;
      this.authenticatedHttpRequestRelayer = authenticatedHttpRequestRelayer;
    }

    public boolean websocketEnabled() {
      return websocketUrl != null ;
    }

    public boolean httpEnabled() {
      return immediateHttpRequestRelayer != null || authenticatedHttpRequestRelayer != null ;
    }

    public boolean tlsEnabled() {
      return sslEngineFactory != null ;
    }

    public String compositeScheme() {
      final StringBuilder scheme = new StringBuilder() ;
      if( httpEnabled() ) {
        scheme.append( "http" ) ;
        if( tlsEnabled() ) {
          scheme.append( 's' ) ;
        }
        if( websocketEnabled() ) {
          scheme.append( '+' ) ;
        }
      }
      if( websocketEnabled() ) {
        scheme.append( "ws" ) ;
        if( tlsEnabled() ) {
          scheme.append( 's' ) ;
        }
      }
      return scheme.toString() ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + '{' + Joiner.on( ';' ).skipNulls().join(
          "eventLoop=" + eventLoopGroup,
          "listenAddress=" + listenAddress,
          websocketUrl == null ? null : "websocketUrl=" + websocketUrl.toExternalForm(),
          "applicationVersion=" + applicationVersion,
          "initialTimeBoundary=" + initialTimeBoundary
      ) + '}' ;
    }

    public interface SignonInboundDutyCommandCrafter< UPWARD_DUTY > {
      SignonInwardDuty create(
          final CommandConsumer< Command< Designator, UPWARD_DUTY > > commandConsumer
      ) ;
    }

  }


// ============
// Construction
// ============

  private final Setup< UPWARD_DUTY > setup ;
  private final ChannelRegistrationHacker channelRegistrationHacker ;
  private final ChannelGroup channels ;


  public UpendConnector( final Setup< UPWARD_DUTY > setup ) {
    this( setup, null ) ;
  }

  /**
   * Only for tests.
   */
  public UpendConnector(
      final Setup< UPWARD_DUTY > setup,
      final ChannelRegistrationHacker channelRegistrationHacker
  ) {
    this.setup = checkNotNull( setup ) ;
    this.currentTimeBoundary = setup.initialTimeBoundary ;
    this.channelRegistrationHacker = channelRegistrationHacker ;
    LOGGER.info( "Created " + this + " using " + setup + "." ) ;
    channels = new DefaultChannelGroup( setup.eventLoopGroup.next() ) ;
  }

  @Override
  public String toString() {
    final String compositeScheme = setup.compositeScheme() ;
    return getClass().getSimpleName() + '{' +
        ( compositeScheme.isEmpty() ? "" : compositeScheme + "://" ) +
        setup.listenAddress.getHostString() + ':' +
        Integer.toString( setup.listenAddress.getPort() ) +
        ( setup.websocketUrl == null ? "" : setup.websocketUrl.getPath() ) +
        '}'
    ;
  }

// =========
// Lifecycle
// =========

  public enum State {
    CREATED,

    STARTING,

    /**
     * Only this step allows inbound queries/messages.
     */
    STARTED,

    STOPPING,

    STOPPED,

    ERROR,
    ;
  }

  private final StateHolder< State > state = new StateHolder<>( State.CREATED ) ;

  public State state() {
    return state.get() ;
  }

  public CompletableFuture< ? > start() {
    state.updateOrFail( STARTING, CREATED, STOPPED ) ;
    final ServerBootstrap serverBootstrap = new ServerBootstrap() ;

    // Need to separate boss/worker groups only if workers keep busy for too long.
    // http://stackoverflow.com/a/28342821/1923328
    serverBootstrap
        .group( setup.eventLoopGroup, setup.eventLoopGroup )
        .channel( NioServerSocketChannel.class )
        .handler( new LoggingHandler( LogLevel.DEBUG ) )
        .childHandler(
            new ChannelInitializer< SocketChannel >() {
              @Override
              public void initChannel( final SocketChannel socketChannel ) throws Exception {
                buildPipeline( socketChannel ) ;
              }
            }
        )
    ;

    final ChannelFuture channelFuture = serverBootstrap.bind( setup.listenAddress ) ;
    channelFuture.addListener( future -> {
      if( future.isSuccess() ) {
        channels.add( channelFuture.channel() ) ;
        state.updateOrFail( STARTED, STARTING ) ;
      }
    } ) ;
    return ChannelTools.concluderFrom( channelFuture ) ;

  }

  private void buildPipeline( final SocketChannel socketChannel ) {
    channels.add( socketChannel ) ;
    final ChannelPipeline pipeline = socketChannel.pipeline() ;
    if( setup.tlsEnabled() ) {
      pipeline.addLast( UpendTierName.TLS.tierName(),
          new SslHandler( setup.sslEngineFactory.newSslEngine() ) ) ;
    }

    pipeline.addLast( UpendTierName.HTTP_SERVER_CODEC.tierName(), new HttpServerCodec() ) ;

    pipeline.addLast( UpendTierName.HTTP_SERVER_AGGREGATOR.tierName(),
        new HttpObjectAggregator( 65536 ) ) ;

    pipeline.addLast( UpendTierName.CHUNKED_WRITER.tierName(), new ChunkedWriteHandler() ) ;

    if( setup.websocketEnabled() ) {
      pipeline.addLast(
          UpendTierName.WEBSOCKET_UPGRADER.tierName(),
          new UpendUpgradeTier(
              UpendConnector.this::afterWebsocketHandshake,
              connectionDescriptor(),
              setup.websocketUrl,
              setup.websocketFrameSizer.maximumPayloadSize
          )
      ) ;
    }

//    pipeline.addLast( UpendTierName.HTTP_COMMAND_RENDERER.tierName(), new CommandRendererTier() ) ;

    if( setup.immediateHttpRequestRelayer != null ) {
      pipeline.addLast(
          UpendTierName.HTTP_IMMEDIATE_COMMAND_RECOGNIZER_HTTP.tierName(),
          new HttpRequestRelayerTier(
              setup.immediateHttpRequestRelayer )
      ) ;
    }

    if( setup.sessionSupervisor != null ) {
      pipeline.addLast(
          UpendTierName.SESSION_ENFORCER.tierName(),
          new SessionEnforcerTier<>( setup.sessionSupervisor, channelRegistrar )
      ) ;
    }

    if( setup.commandConsumer != null ) {
      pipeline.addLast(
          UpendTierName.COMMAND_RECEIVER.tierName(),
          new CommandReceiverTier<>( state, setup.commandConsumer )
      ) ;
      addCommandInterceptorIfNeeded( setup, pipeline ) ;
      ChannelTools.decorateWithLogging(
          pipeline, UpendTierName.COMMAND_RECEIVER.tierName(), false, false ) ;
    }

    pipeline.addLast( UpendTierName.CATCHER.tierName(), new CatcherTier() ) ;



    // ChannelTools.dumpPipelineAsynchronously( pipeline ) ;

    // LOGGER.debug( "Built " + pipeline + " for " + socketChannel + "." ) ;
  }

  private static < UPWARD_DUTY > void addCommandInterceptorIfNeeded(
      final Setup< UPWARD_DUTY > setup,
      final ChannelPipeline pipeline
  ) {
    if( setup.commandInterceptorFactory != null ) {
      if( pipeline.get( UpendTierName.COMMAND_INTERCEPTOR.tierName() ) == null ) {
        if( pipeline.get( UpendTierName.COMMAND_RECEIVER.tierName() ) != null ) {
          pipeline.addBefore(
              UpendTierName.COMMAND_RECEIVER.tierName(),
              UpendTierName.COMMAND_INTERCEPTOR.tierName(),
              new UpendCommandInterceptorTier( setup.commandInterceptorFactory.createNew() )
          ) ;
        }
      }
    }
  }

  /**
   * We want to keep the {@link Setup#eventLoopGroup} untouched, but refuse inbound messages,
   * so we have plenty of time to answer politely to in-flight {@link Command}s, and send
   * {@link CloseWebSocketFrame}s.
   *
   * <h1>Discussion: how close when there are in-flight {@link Command}s?</h1>
   * <p>
   * We need two things.
   * <p>
   * 1. A way to refuse new Upward {@link Command}.
   * <p>
   * 1.1 Shutting down the {@link ServerChannel} prevents from establishing new connections.
   * See {@link ChannelGroup}'s documentation.
   * <p>
   * 1.2 We need to keep connections open until all {@link Command} gets processed, but
   * not accepting any new {@link Command}.
   * <p>
   * 1.2.1 A way to achieve this is a dedicated Level. There would be an {@code HttpValveLevel}
   * sending 503 ("Service Unavailable") to every HTTP request. There would be a
   * {@code WebsocketValveLevel} discarding all inbound {@link WebSocketFrame}.
   * For performance and clarity, it is possible to add this Level when shutting down, instead
   * of adding it from start.
   * <p>
   * 1.2.2 It would be simpler to just use a {@code KickoutAllCommand} that would be queued.
   * This helps to solve the problem of {@link Command} queuing described below.
   * <p>
   * 2. Something that flushes {@link Command}s in the Reactor.
   * <p>
   * 2.1 This could be a {@code BroomCommand} that goes through persistence and Upend Logic
   * and back down.
   * <p>
   * 2.2 This could be a session-terminating {@link Command} (but this would mess the already
   * complicated, multi-cause session ending).
   * <p>
   * 2.3 With Reactor 3 a {@code Processor} may have its own {@code ExecutorService} so we can
   * run some kind of shutdown. There would be the problem of cyclic dependencies between
   * {@code Stages} but this would be a lesser problem. (A possible solution would be to freeze
   * each {@code ExecutorService} the time we check for completion.)
   * <p>
   */
  public CompletableFuture< ? > stop() {
    try {
      state.updateOrFail( STOPPING, STARTED ) ;

      // This discards every existing session.
      // FIXME: we might to that to soon, there will be no chance to send pending Commands.
      sessionChannelMap.clear() ;

      final ChannelGroupFuture channelGroupFuture = channels.flush().close() ;
      final CompletableFuture< ? > concluder = new CompletableFuture<>() ;
      channelGroupFuture.addListener( future -> {
        state.set( STOPPED ) ;
        if( future.isSuccess() ) {
          concluder.complete( null ) ;
          LOGGER.info( "Stopped " + UpendConnector.this + "." ) ;
        } else {
          final Throwable e = future.cause() ;
          concluder.completeExceptionally( e ) ;
          LOGGER.error( "Exception while stopping " + UpendConnector.this + ":" , e ) ;
        }
      } ) ;
      return concluder ;
    } catch( final Exception e ) {
      LOGGER.error( "Exception while stopping " + UpendConnector.this + ":" , e ) ;
      final CompletableFuture< ? > failed = new CompletableFuture<>() ;
      failed.completeExceptionally( e ) ;
      return failed ;
    }
  }

// ========================
// Pipeline reconfiguration
// ========================

  private void afterWebsocketHandshake( final ChannelPipeline pipeline ) {

//    if( pipeline.get( UpendTierName.HTTP_COMMAND_RENDERER.tierName() ) != null ) {
//      pipeline.remove( UpendTierName.HTTP_COMMAND_RENDERER.tierName() ) ;
//    }
    if( pipeline.get( UpendTierName.CHUNKED_WRITER.tierName() ) != null ) {
      pipeline.remove( UpendTierName.CHUNKED_WRITER.tierName() ) ;
    }
    pipeline.remove( UpendTierName.WEBSOCKET_UPGRADER.tierName() ) ;
    if( pipeline.get( UpendTierName.HTTP_IMMEDIATE_COMMAND_RECOGNIZER_HTTP.tierName() ) != null ) {
      pipeline.remove( UpendTierName.HTTP_IMMEDIATE_COMMAND_RECOGNIZER_HTTP.tierName() ) ;
    }

    if( setup.websocketCommandDecoder != null ) {
      pipeline.addAfter(
          UpendTierName.WSDECODER.tierName(),
          UpendTierName.WEBSOCKET_FRAME_AGGREGATOR.tierName(),
          new WebSocketFrameAggregator( setup.websocketFrameSizer.maximumAggregatedSize )
      ) ;
      pipeline.addAfter(
          UpendTierName.WSENCODER.tierName(),
          UpendTierName.WEBSOCKET_FRAME_FRAGMENTER.tierName(),
          new WebsocketFragmenterTier(
              setup.websocketFrameSizer.fragmentSize
          )
      ) ;
      final CommandWebsocketCodecUpendTier< UPWARD_DUTY, DOWNWARD_DUTY >
          websocketCodecUpendTier = new CommandWebsocketCodecUpendTier<>(
              setup.websocketCommandDecoder,
              setup.designatorFactory
          )
      ;
      if( pipeline.get( UpendTierName.COMMAND_RECEIVER.tierName() ) == null ) {
        pipeline.addLast(
            UpendTierName.WEBSOCKET_COMMAND_CODEC.tierName(),
            websocketCodecUpendTier
        ) ;
      } else {

        final UpendTierName tierToAddBefore =
            pipeline.get( UpendTierName.COMMAND_INTERCEPTOR.tierName() ) != null ?
            UpendTierName.COMMAND_INTERCEPTOR :
            UpendTierName.COMMAND_RECEIVER
        ;
        pipeline.addBefore(
            tierToAddBefore.tierName(),
            UpendTierName.WEBSOCKET_COMMAND_CODEC.tierName(),
            websocketCodecUpendTier
        ) ;
      }

      ChannelTools.decorateWithLogging(
          pipeline, UpendTierName.WEBSOCKET_COMMAND_CODEC.tierName(), false, false ) ;


      if( setup.sessionSupervisor != null &&
          pipeline.get( UpendTierName.SESSION_ENFORCER.tierName() ) != null
      ) {
        pipeline.addBefore(
            UpendTierName.SESSION_ENFORCER.tierName(),
            UpendTierName.WEBSOCKET_PHASE_CODEC.tierName(),
            new SessionPhaseWebsocketCodecUpendTier()
        ) ;

        ChannelTools.decorateWithLogging(
            pipeline, UpendTierName.WEBSOCKET_PHASE_CODEC.tierName(), true, false ) ;

      }

    }

    final ConnectionDescriptor currentConnectionDescriptor = connectionDescriptor() ;
    if( currentConnectionDescriptor != null ) {
      pipeline.addAfter(
          UpendTierName.WSENCODER.tierName(),
          UpendTierName.WEBSOCKET_PONG.tierName(),
          new PongTier( currentConnectionDescriptor.timeBoundary.pingTimeoutMs )
      ) ;
    }

    if( channelRegistrationHacker != null ) {
      channelRegistrationHacker.afterChannelCreated( pipeline.channel(), channelRegistrar ) ;
    }
    // ChannelTools.dumpPipelineAsynchronously( pipeline, "after WebSocket handshake sent" ) ;

  }

  private volatile TimeBoundary.ForAll currentTimeBoundary ;

  public void timeBoundary( final TimeBoundary.ForAll newTimeBoundary ) {
    checkNotNull( newTimeBoundary ) ;
    currentTimeBoundary = newTimeBoundary ;
    LOGGER.info( "Using now " + newTimeBoundary + ", will affect only new connections." ) ;
  }

  public TimeBoundary.ForAll currentTimeBoundary() {
    return currentTimeBoundary;
  }

  // ===============
// Session-related
// ===============

  public ConnectionDescriptor connectionDescriptor() {
    return new ConnectionDescriptor(
        setup.applicationVersion, setup.sessionSupervisor != null, currentTimeBoundary ) ;
  }


  /**
   * Send a {@link Command} to the Downend.
   * <p>
   * The {@link Channel} is chosen according to {@link Designator#sessionIdentifier} if this
   * method's caller did set one.
   * If there is no {@link SessionIdentifier} the {@link Designator} can be a
   * {@link RenderingAwareDesignator} with a direct reference to a {@link Channel} because it relates
   * to a sessionless HTTP request.
   * Otherwise the {@link Command} is invalid.
   *
   * <h1>Design discussion</h1>
   * While this looks a bit hacky it's hard to do better. Since Upend may emit downward
   * {@link Command}s at any time, the {@link SessionIdentifier} may be unrelated to any upward
   * {@link Command}. Declaring a dummy {@link SessionIdentifier} wouldn't help to chose the
   * {@link Channel} into which we send responses to sessionless HTTP requests.
   *
   * @throws IllegalArgumentException if the {@link Command} is invalid.
   */
  public void sendDownward( final Command< Designator, ? > command ) {
    final Channel channel ;
    final SessionIdentifier sessionIdentifier = command.endpointSpecific.sessionIdentifier ;
    final Object outbound ;
    final boolean keepAlive ;
    if( sessionIdentifier == null ) {
      if( command instanceof RenderingAwareCommand &&
          command.endpointSpecific instanceof RenderingAwareDesignator
      ) {
        final RenderingAwareDesignator renderingAwareDesignator = ( RenderingAwareDesignator ) command.endpointSpecific ;
        final Channel maybeChannel = renderingAwareDesignator.richHttpRequest.channel() ;
        if( maybeChannel == NettyTools.NULL_CHANNEL ) {
          channel = null ;
          outbound = null ;
          keepAlive = false ;
          LOGGER.warn(
              "Not sending anything because hitting a " + maybeChannel + " in " + command + ". " +
              "TODO: find a less magic approach to handle this case."
          ) ;
        } else {
          channel = maybeChannel ;
          try {
            outbound = ( ( RenderingAwareCommand ) command ).runComputation(
                renderingAwareDesignator.richHttpRequest ) ;
          } finally {
            /** We incremented {@link RichHttpRequest#refCnt()} at the time we
             * obtained the request to keep it usable until now. */
            renderingAwareDesignator.richHttpRequest.release() ;
          }
          keepAlive = HttpUtil.isKeepAlive( renderingAwareDesignator.richHttpRequest ) ;
        }
      } else {
        channel = null ;
        outbound = null ;
        keepAlive = true ;
//        throw new IllegalArgumentException(
//            "Invalid " + command.endpointSpecific + ": " +
//            "no " + SessionIdentifier.class.getSimpleName() + " and " +
//            "not a " + PhasingDesignator.class.getSimpleName()
//        ) ;
      }
    } else {
      channel = sessionChannelMap.get( sessionIdentifier ) ;
      outbound = command ;
      keepAlive = true ;
      if( channel == null ) {
        // Rider causes this a lot when stopping Injectors while there are unsent Downward Commands.
        LOGGER.info(
            "No " + Channel.class.getSimpleName() + " found for " + sessionIdentifier + ". " +
                "This might have been caused by " + SessionIdentifier.class.getSimpleName() +
                " outdated before sending a deferred " + Command.class.getSimpleName() + ". " +
                "So " + command + " remains unsent."
        ) ;
      }
    }
    if( channel != null ) {
      if( outbound instanceof FullHttpResponse ) {
        NettyTools.setHeadersForKeepAliveIfNeeded( ( FullHttpResponse ) outbound, keepAlive ) ;
      }
      final ChannelFuture channelFuture = channel.writeAndFlush( outbound ) ;
      if( ! keepAlive ) {
        channelFuture.addListener( ChannelFutureListener.CLOSE ) ;
      }
      if( command.endpointSpecific instanceof SendingAware ) {
        channelFuture.addListener( future -> {
          if( future.isSuccess() ) {
            ( ( SendingAware ) command.endpointSpecific ).sent() ;
          }
        } ) ;
      }
    }
  }

  private final ConcurrentMap< SessionIdentifier, Channel > sessionChannelMap =
      new ConcurrentHashMap<>() ;

  private final ChannelRegistrar channelRegistrar = new ChannelRegistrar() {
    @Override
    public void registerChannel(
        final SessionIdentifier sessionIdentifier,
        final Channel channel
    ) {
      checkNotNull( sessionIdentifier ) ;
      if( channel == null ) {
        channel.attr( ChannelTools.SESSION_KEY ).set( null ) ;
        sessionChannelMap.remove( sessionIdentifier ) ;
      } else {
        channel.attr( ChannelTools.SESSION_KEY ).set( sessionIdentifier ) ;
        sessionChannelMap.put( sessionIdentifier, channel ) ;
      }
    }

    @Override
    public SessionIdentifier unregisterChannel( final Channel channel ) {
      final SessionIdentifier sessionIdentifier =
          channel.attr( ChannelTools.SESSION_KEY ).getAndRemove() ;
      final Iterator< Map.Entry< SessionIdentifier, Channel > > iterator =
          sessionChannelMap.entrySet().iterator() ;
      while( iterator.hasNext() ) {
        final Map.Entry< SessionIdentifier, Channel > entry = iterator.next() ;
        if( entry.getValue() == channel ) {
          iterator.remove() ;
        }
      }
      return sessionIdentifier ;
    }

    @Override
    public String toString() {
      return ToStringTools.nameAndCompactHash( this ) + "{}" ;
    }
  } ;

  /**
   * The callback to make a {@link SessionEnforcerTier} update the association
   * between {@link SessionIdentifier} and {@link Channel} so we can find where to send
   * a {@link Command} given its {@link Designator#sessionIdentifier}.
   * The {@link #registerChannel(SessionIdentifier, Channel)} method also updates the
   * {@link ChannelTools#SESSION_KEY} in the {@link Channel}.
   * Deregistration happens by passing a {@code null} {@link Channel}, or calling
   * {@link #unregisterChannel(Channel)} (both have the same effect, calling one or the
   * other depends on what the calling code knows).
   */
  public interface ChannelRegistrar {
    void registerChannel( final SessionIdentifier sessionIdentifier, final Channel channel ) ;

    SessionIdentifier unregisterChannel( Channel channel ) ;
  }

  /**
   * Tests without the authentication stuff can force the association of a {@link Channel}
   * with a {@link SessionIdentifier} by passing a {@link ChannelRegistrationHacker}
   * to {@link #UpendConnector(Setup, ChannelRegistrationHacker)}.
   */
  public interface ChannelRegistrationHacker {
    void afterChannelCreated( Channel channel, ChannelRegistrar channelRegistrar ) ;
  }
}
