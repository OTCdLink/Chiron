package io.github.otcdlink.chiron;

import io.github.otcdlink.chiron.downend.CommandInFlightStatus;
import io.github.otcdlink.chiron.downend.CommandTransceiver;
import io.github.otcdlink.chiron.downend.DownendConnector;
import io.github.otcdlink.chiron.downend.DownendStateTools;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

import static org.assertj.core.util.Preconditions.checkNotNull;

/**
 * Represents a method call to {@link DownendConnector.ChangeWatcher} or
 * {@link CommandTransceiver.ChangeWatcher}.
 * The base principle is to pass the new {@link DownendConnector.State}, but sometimes things
 * happen outside of a {@link DownendConnector.State} change, so we wrap them in a
 * {@link DownendConnector.Change} which is subclassed by the present
 * {@link ExtendedChange}.
 *
 */
public class ExtendedChange extends DownendConnector.Change {

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( ExtendedChange.class ) ;

  private ExtendedChange( final ExtendedKind extendedKind ) {
    super( extendedKind ) ;
  }

  public enum ExtendedKind implements DownendConnector.ChangeDescriptor {
    FAILED_CONNECTION_ATTEMPT,
    NO_SIGNON,
    COMMAND_INFLIGHT_STATUS,
    ;

  }

  public static class CommandInFlightStatusChange extends ExtendedChange {

    public final CommandInFlightStatus commandInFlightStatus ;

    private CommandInFlightStatusChange( final CommandInFlightStatus commandInFlightStatus ) {
      super( ExtendedKind.COMMAND_INFLIGHT_STATUS ) ;
      this.commandInFlightStatus = checkNotNull( commandInFlightStatus ) ;
    }

    @Override
    protected String tostringBody() {
      return commandInFlightStatus.name() ;
    }
  }

  static CommandTransceiver.ChangeWatcher recordingDownendWatcher(
      final Logger logger,
      final Supplier< String > downendTostringSupplier,
      final BlockingQueue<DownendConnector.Change> stateChangeQueue
  ) {
    return new DownendStateTools.StateWatcherLoggingAdapter( logger, downendTostringSupplier ) {

      private void add( final DownendConnector.Change change ) {
        stateChangeQueue.add( change ) ;
      }

      @Override
      protected void doStateChanged( final DownendConnector.Change change ) {
        add( change ) ;
      }

      @Override
      protected void doFailedConnectionAttempt() {
        add( new DownendConnector.Change<>( ExtendedKind.FAILED_CONNECTION_ATTEMPT ) ) ;
      }

      @Override
      protected void doNoSignon() {
        add( new DownendConnector.Change<>( ExtendedKind.NO_SIGNON ) ) ;
      }


      @Override
      protected void doInFlightStatusChange(
          @SuppressWarnings( "UnusedParameters" )
          final CommandInFlightStatus commandInFlightStatus
      ) {
        add( new CommandInFlightStatusChange( commandInFlightStatus ) ) ;
      }

      @Override
      public String toString() {
        return ToStringTools.nameAndCompactHash( this ) + "{}" ;
      }
    } ;
  }
  }
