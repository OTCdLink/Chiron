package io.github.otcdlink.chiron.upend;

import com.google.common.base.Charsets;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.integration.echo.EchoCodecFixture;
import io.github.otcdlink.chiron.integration.echo.EchoDownwardDuty;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import io.github.otcdlink.chiron.integration.twoend.EndToEndFixture;
import io.github.otcdlink.chiron.middle.ChannelTools;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import io.github.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import io.github.otcdlink.chiron.toolbox.TcpPortBooker;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.clock.UpdateableClock;
import io.github.otcdlink.chiron.toolbox.internet.LocalAddressTools;
import io.github.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.github.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer;
import io.github.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class UpendConnectorFixture {

  private static final Logger LOGGER = LoggerFactory.getLogger( UpendConnectorFixture.class ) ;
  public static final String APPLICATION_VERSION = "SomeVersion" ;
  private final InitializationGround initializationGround ;
  public final int port ;

  public final NettyHttpClient.Recorder httpResponseWatcher = new NettyHttpClient.Recorder() ;

  private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup() ;

// ============
// Constructors
// ============

  public UpendConnectorFixture() {
    this(
        new InitializationGround( TimeKit.instrumentedTimeKit( Stamp.FLOOR ), null ),
        TcpPortBooker.THIS.find()
    ) ;
  }

  public UpendConnectorFixture( final Designator.Factory designatorFactory, final int port ) {
    this( new InitializationGround( null, designatorFactory ), port ) ;
  }

  private UpendConnectorFixture(
      final InitializationGround initializationGround,
      final int port
  ) {
    this.initializationGround = checkNotNull( initializationGround ) ;
    checkArgument( port > 0 ) ;
    this.port = port ;
    httpRequestRelayer = initializationGround.happyCommandRecognizer() ;
  }

  /**
   * Capture every constructor parameter in one single object for reusing some logic bits in
   * {@link EndToEndFixture}.
   */
  public static final class InitializationGround {
    public final TimeKit< UpdateableClock > timeKit ;
    public final Designator.Factory designatorFactory ;

    public InitializationGround(
        final TimeKit< UpdateableClock > timeKit,
        final Designator.Factory designatorFactory
    ) {
      checkArgument( ( timeKit == null && designatorFactory != null ) ||
          ( timeKit != null && designatorFactory == null ) ) ;
      this.timeKit = timeKit ;
      this.designatorFactory = designatorFactory == null ?
          timeKit.designatorFactory : designatorFactory ;
    }

    public HttpRequestRelayer happyCommandRecognizer() {
      return ( httpRequest, channelHandlerContext ) -> {
        if( httpRequest.method().equals( HttpMethod.GET ) ) {
          if( httpRequest.uri().matches( ".*" ) ) {
            new UsualHttpCommands.Html(
                "<h2>OK (200) response for " + httpRequest.uri() + "</h2>" +
                "<p>" + httpRequest.content().toString( Charsets.UTF_8 ) + "</p>"
            ).feed( channelHandlerContext ) ;
            return true ;
          }
        }
        return false ;
      } ;

    }
  }


// ==============
// Initialization
// ==============

  private NettyHttpClient httpClient = null ;

  private InitializationResult initializationResult = null ;

  private void checkInitialized() {
    checkState( initializationResult != null, "Not initialized" ) ;
  }


  /**
   * Keeps all {@link UpendConnector}-related post-initialization stuff together for reuse
   * in {@link EndToEndFixture}.
   */
  public static class InitializationResult {
    public final UpendConnector.Setup< EchoUpwardDuty<Designator> > setup ;
    public final UpendConnector<
        EchoUpwardDuty<Designator>,
        EchoDownwardDuty< Designator >
    > upendConnector ;
    public final UrlPrebuilder urlPrebuilder;
    public final SessionIdentifier sessionIdentifierNoSession ;

    private InitializationResult(
        final UpendConnector.Setup< EchoUpwardDuty<Designator> > setup,
        final UpendConnector<
            EchoUpwardDuty<Designator>,
            EchoDownwardDuty< Designator>
        > upendConnector,
        final UrlPrebuilder urlPrebuilder,
        final SessionIdentifier sessionIdentifierNoSession
    ) {
      this.setup = setup ;
      this.upendConnector = upendConnector ;
      this.urlPrebuilder = urlPrebuilder;
      this.sessionIdentifierNoSession = sessionIdentifierNoSession ;
    }

    public static InitializationResult from(
        final UpendConnector.Setup< EchoUpwardDuty<Designator> > upendSetup
    ) {
      final UpendConnector<
          EchoUpwardDuty<Designator>,
          EchoDownwardDuty< Designator >
      > upendConnector ;
      final SessionIdentifier sessionIdentifierNoSession ;
      if( upendSetup.sessionSupervisor == null ) {
        sessionIdentifierNoSession = new SessionIdentifier( "Xxxxx" );
        /**
         * There is no authentication therefore no {@link SessionIdentifier} but
         * {@link Designator.Factory} needs one. So we tweak the {@link ChannelPipeline}
         * to force a {@link SessionIdentifier}, and force {@link Channel} association
         * with a {@link SessionIdentifier.
         */
        upendConnector = new UpendConnector<>(
            upendSetup,
            ( channel, registrar ) -> {
              channel.attr( ChannelTools.SESSION_KEY ).set( sessionIdentifierNoSession ) ;
              registrar.registerChannel( sessionIdentifierNoSession, channel ) ;
            }
        ) ;
      } else {
        upendConnector = new UpendConnector<>( upendSetup ) ;
        sessionIdentifierNoSession = null ;
      }
      return new InitializationResult(
          upendSetup,
          upendConnector,
          new UrlPrebuilder( upendSetup ),
          sessionIdentifierNoSession
      ) ;

    }

    public void bootSynchronously() {
      upendConnector
          .start()
          .join()
      ;
    }
  }

  public void initializeAndStart(
      final Supplier< UpendConnector.Setup< EchoUpwardDuty< Designator > > > setupSupplier
  ) {
    checkState( initializationResult == null ) ;
    final UpendConnector.Setup< EchoUpwardDuty< Designator > > setup = setupSupplier.get() ;
    initializationResult = InitializationResult.from( setup ) ;
    initializationResult.bootSynchronously() ;
  }

  public void stopAll() {
    eventLoopGroup.shutdownGracefully() ;
  }


// ========
// Features
// ========


  public TimeKit< UpdateableClock > timeKit() {
    checkState( initializationGround.timeKit != null, "Not properly instantiated" ) ;
    return initializationGround.timeKit ;
  }

  public NettyHttpClient httpClient() {
    if( httpClient == null ) {
      httpClient = new NettyHttpClient( eventLoopGroup, 1000 ) ;
      httpClient.start().join() ;
    }
    return httpClient ;
  }

  public UrlPrebuilder urlPrebuilder() {
    checkInitialized() ;
    return initializationResult.urlPrebuilder ;
  }

  public UpendConnector.Setup< EchoUpwardDuty< Designator > > upendConnectorSetup() {
    checkInitialized() ;
    return initializationResult.setup ;
  }

  public UpendConnector<
      EchoUpwardDuty<Designator>,
      EchoDownwardDuty< Designator >
  > upendConnector() {
    checkInitialized() ;
    return initializationResult.upendConnector ;
  }


  public static final class UrlPrebuilder {
    private final boolean useTls ;
    private final InetSocketAddress socketAddress ;

    public UrlPrebuilder( final UpendConnector.Setup setup ) {
      this( setup.tlsEnabled(), setup.listenAddress ) ;
    }

    public UrlPrebuilder( final boolean useTls, final InetSocketAddress socketAddress ) {
      this.useTls = useTls ;
      this.socketAddress = checkNotNull( socketAddress ) ;
    }

    public URL home() {
      return url(
          ( useTls ? "https://" : "http://" ) +
          socketAddress.getHostString() + ":" + socketAddress.getPort()
      ) ;
    }

    public URL malformed() {
      return UrxTools.derive( homeSlash(), "~/passwords.txt" ) ;
    }

    public URL homeSlash() {
      return UrxTools.derive( home(), "/" ) ;
    }

    private static URL url( final String string ) {
      return UrxTools.parseUrlQuiet( string ) ;
    }

  }


  public HttpRequestRelayer httpRequestRelayer ;




// ======  
// Setups
// ======  


  public UpendConnector.Setup< EchoUpwardDuty< Designator > > httpEchoSetup() {

    return new UpendConnector.Setup<>(
        this.eventLoopGroup,
        new InetSocketAddress( LocalAddressTools.LOCAL_ADDRESS, this.port ),
        null,
        null,//WEBSOCKET_PATH,
        APPLICATION_VERSION,
        null,
        null,
        null,
        null,
        this.httpRequestRelayer,
        null,
        null,
        null,
        null, //HttpRenderableCommand.AutomaticRenderer.INSTANCE,
        null,
        null
    ) ;
  }
  public UpendConnector.Setup< EchoUpwardDuty< Designator > > pingResponderSetup(
      final CommandConsumer< Command< Designator, EchoUpwardDuty< Designator > > > commandConsumer,
      final TimeBoundary.ForAll timeBoundary
  ) {

    return new UpendConnector.Setup<>(
        this.eventLoopGroup,
        new InetSocketAddress( LocalAddressTools.LOCAL_ADDRESS, this.port ),
        null,
        WEBSOCKET_PATH,
        APPLICATION_VERSION,
        null,
        commandConsumer,
        timeKit().designatorFactory,
        new EchoCodecFixture.PartialUpendDecoder(),
        this.httpRequestRelayer,
        null,
        null,
        timeBoundary,
        null, //HttpRenderableCommand.AutomaticRenderer.INSTANCE,
        null,
        WebsocketFrameSizer.tightSizer( 8192 )
    ) ;
  }

  private static final String WEBSOCKET_PATH = "/websocket" ;



}