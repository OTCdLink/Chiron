package io.github.otcdlink.chiron.downend;

import com.google.common.util.concurrent.MoreExecutors;
import io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import io.github.otcdlink.chiron.toolbox.catcher.Catcher;
import org.slf4j.Logger;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public final class DownendStateTools {

  private DownendStateTools() { }

  public static class ChangeWatcherAdapter implements CommandTransceiver.ChangeWatcher {

    @Override
    public void stateChanged( final DownendConnector.Change change ) {
      switch( ( DownendConnector.State ) change.kind ) {
        case CONNECTING :
          connecting() ;
          break ;
        case CONNECTED :
          final DownendConnector.Change.SuccessfulConnection successfulConnection =
              ( DownendConnector.Change.SuccessfulConnection ) change ;
          connected( successfulConnection.connectionDescriptor ) ;
          break ;
        case SIGNED_IN :
          signedIn() ;
          break ;
        case STOPPING :
          stopping() ;
          break ;
        case STOPPED :
          stopped() ;
          break ;
        case PROBLEM :
          final DownendConnector.Change.Problem problem =
              ( DownendConnector.Change.Problem ) change;
          problem( problem.cause ) ;
          break ;
        default :
          throw new IllegalArgumentException( "Unsupported: " + change ) ;
      }
    }

    protected void connecting() { }

    protected void connected(
        @SuppressWarnings( "UnusedParameters" )
        final ConnectionDescriptor connectionDescriptor
    ) { }

    protected void signedIn() { }

    protected void stopping() { }

    protected void stopped() { }

    protected void problem(
        @SuppressWarnings( "UnusedParameters" )
        final Throwable cause
    ) { }


    @Override
    public void failedConnectionAttempt() { }

    @Override
    public void noSignon() { }

    @Override
    public void inFlightStatusChange( final CommandInFlightStatus commandInFlightStatus ) { }
  }

  public static class StateWatcherLoggingAdapter extends ChangeWatcherAdapter {

    private final Logger logger ;
    final Supplier< String > tostringSupplier;

    @SuppressWarnings( "unused" )
    public StateWatcherLoggingAdapter( final Logger logger ) {
      this( logger, null ) ;
    }

    public StateWatcherLoggingAdapter(
        final Logger logger,
        final Supplier< String > tostringSupplier
    ) {
      this.logger = checkNotNull( logger ) ;
      this.tostringSupplier = tostringSupplier ;
    }

    @Override
    public final void stateChanged( final DownendConnector.Change change ) {
      logger.debug( "Called #stateChanged( " + change + " for " + downendToString() + "." ) ;
      super.stateChanged( change ) ;
      doStateChanged( change ) ;
    }

    private String downendToString() {
      final String downend = tostringSupplier == null ? null : tostringSupplier.get() ;
      return downend == null ? "<undefined>" : downend ;
    }

    protected void doStateChanged( final DownendConnector.Change change ) { }

    /**
     * Made final to prevent subclasses from not calling {@code super}.
     *
     * @see #doFailedConnectionAttempt()
     */
    @Override
    public final void failedConnectionAttempt() {
      logger.debug( "Failed connection attempt for " + downendToString() + "." ) ;
      super.failedConnectionAttempt() ;
      doFailedConnectionAttempt() ;
    }

    @SuppressWarnings( "WeakerAccess" )
    protected void doFailedConnectionAttempt() { }

    /**
     * Made final to prevent subclasses from not calling {@code super}.
     *
     * @see #doNoSignon()
     */
    @Override
    public final void noSignon() {
      logger.debug( "Failed connection attempt for " + downendToString() + "." ) ;
      super.noSignon() ;
      doNoSignon() ;
    }

    @SuppressWarnings( "WeakerAccess" )
    protected void doNoSignon() { }

    @Override
    public final void inFlightStatusChange( final CommandInFlightStatus commandInFlightStatus ) {
      logger.debug( "In-flight status changed to " + commandInFlightStatus + " for " +
          downendToString() + "." ) ;
      super.inFlightStatusChange( commandInFlightStatus ) ;
      doInFlightStatusChange( commandInFlightStatus ) ;
    }

    @SuppressWarnings( "WeakerAccess" )
    protected void doInFlightStatusChange(
        @SuppressWarnings( "UnusedParameters" ) final CommandInFlightStatus commandInFlightStatus
    ) { }
  }

  /**
   * A simplified version of
   * {@link io.github.otcdlink.chiron.downend.CommandTransceiver.ChangeWatcher}
   * useful for a graphical user interface.
   */
  public interface ConnectionAvailabilityWatcher {

    /**
     * Notifies that networking is usable again (implies a previous call to
     * {@link #onSignonSuccess(String)}.
     * This method is never called when {@link ConnectionDescriptor#authenticationRequired}
     * is {@code true}.
     *
     * @deprecated use {@link #onSignonSuccess(String)} passing {@code null}.
     */
    void onConnectionAvailableAgain() ;

    /** Notifies that networking is no longer available, but may be available again.
     */
    void onConnectionUnavailable() ;

    /**
     * Notifies that signon happened with all authentication needed.
     * TODO: rename into {@code connectionAvailable} after removing
     *     {@link #onConnectionAvailableAgain()} which is redundant.
     */
    void onSignonSuccess( String loginName ) ;

    /**
     * Notifies that user cancelled signon. If there are too many failure, there should be
     * an explicit message discouraging the User from further attempts.
     */
    void noSignon() ;

  }

  /**
   *
   * @param executor a single-threaded {@code Executor} to avoid concurrency.
   */
  public static CommandTransceiver.ChangeWatcher asChangeWatcher(
      final Catcher catcher,
      final Executor executor,
      final ConnectionAvailabilityWatcher connectionAvailabilityWatcher
  ) {
    final Executor actualExecutor = executor == null ? MoreExecutors.directExecutor() : executor ;

    final CommandTransceiver.ChangeWatcher changeWatcher = new CommandTransceiver.ChangeWatcher() {

      private boolean firstConnection = true ;
      private boolean connectionActive = false ;
      private ConnectionDescriptor connectionDescriptor = null ;

      private void switchAvailability( final boolean active ) {
        final boolean wasActive = this.connectionActive ;
        this.connectionActive = active ;
        if( wasActive != active ) {
          if( active ) {
            connectionAvailabilityWatcher.onConnectionAvailableAgain() ;
          } else {
            connectionAvailabilityWatcher.onConnectionUnavailable() ;
          }
        }
      }

      @Override
      public void stateChanged( final DownendConnector.Change change ) {
        actualExecutor.execute ( () -> {
          if( change instanceof DownendConnector.Change.SuccessfulConnection ) {
            if( firstConnection ) {
              firstConnection = false ;
            }
            final DownendConnector.Change.SuccessfulConnection successfulConnection =
                ( DownendConnector.Change.SuccessfulConnection ) change ;
            connectionDescriptor = successfulConnection.connectionDescriptor ;
            if( ! connectionDescriptor.authenticationRequired ) {
              switchAvailability( true ) ;
            }
          } else if( change instanceof DownendConnector.Change.Problem ) {
            catcher.processThrowable( ( ( DownendConnector.Change.Problem ) change ).cause ) ;
          } else {
            switch( ( DownendConnector.State ) change.kind ) {
              case CONNECTING :
                if( ! firstConnection ) {
                  switchAvailability( false ) ;
                }
                break ;
              case SIGNED_IN :
                connectionActive = true ;
                connectionAvailabilityWatcher.onSignonSuccess( "" ) ;
                break ;
              case STOPPED :
              case CONNECTED :
              case STOPPING :
              case PROBLEM :
                break ;
              default :
                catcher.processThrowable(
                    new IllegalArgumentException( "Unsupported: " + change.kind ) ) ;
            }
          }
        } ) ;
      }

      @Override
      public void failedConnectionAttempt() { }

      @Override
      public void noSignon() {
        actualExecutor.execute( connectionAvailabilityWatcher::noSignon ) ;
      }

      @Override
      public void inFlightStatusChange( final CommandInFlightStatus commandInFlightStatus ) {
        actualExecutor.execute( () -> {
          switch( commandInFlightStatus ) {
            case QUIET :
              break ;
            case IN_FLIGHT :
              break ;
            case SOME_COMMAND_FAILED :
              break ;
            default :
              catcher.processThrowable(
                  new IllegalArgumentException( "Unsupported: " + commandInFlightStatus ) ) ;
          }
        } ) ;

      }
    } ;

    return changeWatcher ;
  }

}
