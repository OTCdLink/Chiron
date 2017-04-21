package io.github.otcdlink.chiron.downend;

import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.codec.CommandBodyDecoder;
import io.github.otcdlink.chiron.command.AbstractDownwardFailure;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.Command.Tag;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.command.codec.Codec;
import io.github.otcdlink.chiron.middle.tier.CommandInterceptor;
import io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import io.github.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import io.github.otcdlink.chiron.toolbox.clock.Clock;
import io.github.otcdlink.chiron.toolbox.internet.InternetProxyAccess;
import io.github.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Timer;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Supports {@link Command}s with a {@link Tracker}.
 */
public class CommandTransceiver< DOWNWARD_DUTY, UPWARD_DUTY >
    implements Downend< Tracker, UPWARD_DUTY >
{

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( CommandTransceiver.class ) ;


  public interface ChangeWatcher extends DownendConnector.ChangeWatcher {
    void inFlightStatusChange( CommandInFlightStatus commandInFlightStatus ) ;
  }

  public static class Setup< DOWNWARD_DUTY >
      extends DownendConnector.Setup< Tracker, DOWNWARD_DUTY >
  {

    public Setup(
        final Clock clock,
        final EventLoopGroup eventLoopGroup,
        final URL url,
        final InternetProxyAccess internetProxyAccess,
        final SslEngineFactory.ForClient sslEngineFactory,
        final TimeBoundary.PrimingForDownend initialTimeBoundary,
        final SignonMaterializer signonMaterializer,
        final ChangeWatcher stateWatcher,
        final CommandBodyDecoder< Tracker, DOWNWARD_DUTY > commandDecoder,
        final CommandConsumer< Command< Tracker, DOWNWARD_DUTY > > commandReceiver,
        final CommandInterceptor.Factory commandInterceptorFactory,
        final WebsocketFrameSizer websocketFrameSizer
    ) {
      super(
          eventLoopGroup,
          url,
          internetProxyAccess,
          sslEngineFactory,
          initialTimeBoundary,
          signonMaterializer,
          new ChangeWatcherInterceptor( stateWatcher ),
          new TrackerCodec( clock ),
          commandDecoder,
          new CommandConsumerInterceptor<>( commandReceiver ),
          commandInterceptorFactory,
          websocketFrameSizer
      ) ;
    }

  }

  private final DownendConnector< Tracker, DOWNWARD_DUTY, UPWARD_DUTY > downendConnector ;
  private final TrackerCurator trackerCurator ;

  public CommandTransceiver( final Setup< DOWNWARD_DUTY > setup ) {
    this.downendConnector = new DownendConnector<>( setup ) ;

    // Transtypings below relies on instantiation tweaks in a nested class, so it's OK.

    final TrackerCodec trackerCodec = ( TrackerCodec )
        downendConnector.setup.endpointSpecificCodec ;
    trackerCodec.claimDelegate = ( ( ChangeWatcher ) setup.changeWatcher )::inFlightStatusChange ;

    this.trackerCurator = trackerCodec.trackerCurator ;

    ( ( ChangeWatcherInterceptor ) setup.changeWatcher ).hook = changeWatcherAdapter() ;

    ( ( CommandConsumerInterceptor< DOWNWARD_DUTY > ) setup.commandReceiver ).hook =
        CommandTransceiver::dispatchCommand ;
  }

  private static < DOWNWARD_DUTY > void dispatchCommand(
      final Command< Tracker, DOWNWARD_DUTY > command
  ) {
    if( command instanceof AbstractDownwardFailure ) {
      command.endpointSpecific.afterRemoteFailure(
          ( ( AbstractDownwardFailure ) command ).notice ) ;
    } else {
      command.endpointSpecific.afterResponseHandled() ;
    }
  }

  public Setup setup() {
    return ( Setup ) downendConnector.setup ;
}

  @Override
  public CompletableFuture< ? > start() {
    return downendConnector.start() ;
  }

  @Override
  public CompletableFuture< ? > stop() {
    return downendConnector.stop() ;
  }

  @Override
  public DownendConnector.State state() {
    return downendConnector.state() ;
  }

  @Override
  public void send( final Command< Tracker, UPWARD_DUTY > command ) {
    downendConnector.send( command ) ;
  }

  private DownendStateTools.ChangeWatcherAdapter changeWatcherAdapter() {
    return new DownendStateTools.ChangeWatcherAdapter() {
      private boolean authenticationRequired = false ;
      private boolean firstTimeConnecting = true ;

      @Override
      protected void connecting() {
        if( firstTimeConnecting ) {
          firstTimeConnecting = false ;
        } else {
          trackerCurator.notifyConnectionBroken() ;
        }
      }

      @Override
      protected void connected( final ConnectionDescriptor connectionDescriptor ) {
        authenticationRequired = connectionDescriptor.authenticationRequired ;
        trackerCurator.trackerLifetimeMs( connectionDescriptor.timeBoundary.pongTimeoutMs() );
        if( ! authenticationRequired ) {
          trackerCurator.notifyReconnection() ;
        }
      }

      @Override
      protected void signedIn() {
        trackerCurator.notifyReconnection() ;
      }

      @Override
      public void failedConnectionAttempt() {
        trackerCurator.notifyConnectionBroken() ;
      }
    } ;
  }


  /**
   * Maps {@link Tracker} into {@link Tag} when encoding, and back.
   */
  private static final class TrackerCodec implements Codec< Tracker > {

    private final TrackerCurator trackerCurator ;

    /**
     * Mutable field, needed to resolve a reference cycle at construction time.
     */
    private TrackerCurator.Claim claimDelegate = null ;

    public TrackerCodec( final Clock clock ) {
      trackerCurator = new TrackerCurator(
          sharedCommandStatus -> {
            checkState( claimDelegate != null, "Initialization incomplete" ) ;
            claimDelegate.commandStatusChanged( sharedCommandStatus ) ;
          },
          clock
      ) ;
    }

    @Override
    public Tracker decodeFrom( final PositionalFieldReader positionalFieldReader )
        throws IOException
    {
      final String tagAsString = positionalFieldReader.readNullableString() ;
      if( tagAsString == null ) {
        return Tracker.NULL ;
      } else {
        final Tag tag = new Tag( tagAsString ) ;
        final Tracker tracker = trackerCurator.get( tag ) ;
        return tracker == null ? Tracker.NULL : tracker ;
      }
    }

    @Override
    public void encodeTo(
        final Tracker tracker,
        final PositionalFieldWriter positionalFieldWriter
    ) throws IOException {
      final Tag tag ;
      if( tracker == null ) {
        tag = trackerCurator.generateTag() ;
      } else {
        tag = trackerCurator.add( tracker ) ;
      }
      positionalFieldWriter.writeDelimitedString( tag.asString() ) ;
    }
  }

  private static class ChangeWatcherInterceptor implements ChangeWatcher {

    private final ChangeWatcher delegate ;

    /**
     * Mutable field, needed to resolve a reference cycle at construction time.
     */
    private ChangeWatcher hook = null ;

    private ChangeWatcherInterceptor( final ChangeWatcher delegate ) {
      this.delegate = checkNotNull( delegate ) ;
    }

    private void checkInitialized() {
      checkState( hook != null, "Not properly initialized" ) ;
    }

    // ==========
    // Delegation
    // ==========

    @Override
    public void inFlightStatusChange( final CommandInFlightStatus commandInFlightStatus ) {
      checkInitialized() ;
      hook.inFlightStatusChange( commandInFlightStatus ) ;
      delegate.inFlightStatusChange( commandInFlightStatus ) ;
    }

    @Override
    public void stateChanged( final DownendConnector.Change change ) {
      checkInitialized() ;
      hook.stateChanged( change ) ;
      delegate.stateChanged( change ) ;
    }

    @Override
    public void failedConnectionAttempt() {
      checkInitialized() ;
      hook.failedConnectionAttempt() ;
      delegate.failedConnectionAttempt() ;
    }

    @Override
    public void noSignon() {
      checkInitialized() ;
      hook.noSignon() ;
      delegate.noSignon() ;
    }
  }

  private static class CommandConsumerInterceptor< DOWNWARD_DUTY >
      implements CommandConsumer< Command< Tracker, DOWNWARD_DUTY > >
  {
    private final CommandConsumer< Command< Tracker, DOWNWARD_DUTY > > delegate ;
    private CommandConsumer< Command< Tracker, DOWNWARD_DUTY > > hook = null ;

    private CommandConsumerInterceptor(
        final CommandConsumer< Command< Tracker, DOWNWARD_DUTY > > delegate
    ) {
      this.delegate = checkNotNull( delegate ) ;
    }

    @Override
    public void accept( final Command< Tracker, DOWNWARD_DUTY > command ) {
      checkState( hook != null, "Not properly initialized" ) ;
      try {
        if( ! ( command instanceof AbstractDownwardFailure ) ) {
          delegate.accept( command ) ;
        }
      } finally {
        try {
          hook.accept( command ) ;
        } catch( final Exception e ) {
          LOGGER.error(
              "Exception caught while notifying of complete consumption of " + command + ".", e ) ;
        }
      }
    }
  }

  public static abstract class ScavengeActivator {

    protected final TrackerCurator trackerCurator ;

    public abstract void stop() ;

    protected ScavengeActivator( final CommandTransceiver commandTransceiver ) {
      this.trackerCurator = checkNotNull( commandTransceiver.trackerCurator ) ;
    }

    public static ScavengeActivator startFromTimer(
        final CommandTransceiver commandTransceiver,
        final long periodMs
    ) {
      return new WithTimer( commandTransceiver, periodMs ) ;
    }

    public static Voluntary voluntary( final CommandTransceiver commandTransceiver ) {
      return new Voluntary( commandTransceiver ) ;
    }

    private static class WithTimer extends ScavengeActivator {

      private final Timer timer ;

      private WithTimer( final CommandTransceiver commandTransceiver, final long periodMs ) {
        super( commandTransceiver ) ;
        timer = new Timer( ( int ) periodMs, e -> trackerCurator.scavengeTimeouts() ) ;
        timer.start() ;
      }

      @Override
      public void stop() {
        timer.stop() ;
      }
    }

    /**
     * Only for tests.
     */
    public static class Voluntary extends ScavengeActivator {

      public Voluntary( final CommandTransceiver commandTransceiver ) {
        super( commandTransceiver ); ;
      }

      public void scavengeNow() {
        trackerCurator.scavengeTimeouts() ;
      }

      @Override
      public void stop() { }
    }
  }
}
