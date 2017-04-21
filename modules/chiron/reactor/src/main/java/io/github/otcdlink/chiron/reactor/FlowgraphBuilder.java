package io.github.otcdlink.chiron.reactor;

import io.github.otcdlink.chiron.toolbox.StateHolder;
import io.github.otcdlink.chiron.toolbox.ThrowableTools;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.Environment;

import java.util.concurrent.TimeUnit;

/**
 * Wires up {@link Stage}s in a Reactive Stream-friendly {@link Flowgraph}.
 * Upon a call to {@link Flowgraph#upgrade()}, built {@link Flowgraph} switches from
 * {@link ReplayingFlowgraph} to {@link UpgradedFlowgraph}.
 */
public class FlowgraphBuilder {

  private static final Logger PIPELINE_LOGGER = LoggerFactory.getLogger( Flowgraph.class ) ;

  /**
   * Must be greater than maximum number of expected users.
   * Otherwise the Reactor silently chokes when flattening a list of Commands.
   */
  private static final int BACKLOG_SIZE = 256 ;

  public < COMMAND > Flowgraph< COMMAND > build(
      final StagePack< COMMAND > stagePack
  ) {
    return build( stagePack, BACKLOG_SIZE ) ;
  }

  public < COMMAND > Flowgraph< COMMAND > build(
      final StagePack< COMMAND > stagePack,
      final int backlogSize
  ) {
    return buildWithProcessors( stagePack, backlogSize ) ;
  }

  private < COMMAND > Flowgraph< COMMAND > buildWithProcessors(
      final StagePack< COMMAND > stagePack,
      final int backlogSize
  ) {

    class DefaultBackdoor implements Flowgraph.Backdoor< COMMAND > {
      @Override
      public void justProcessImmediately( final COMMAND command ) {
        stagePack.logic().apply( command ) ;
        stagePack.persister().apply( command ) ;
      }

      @Override
      public Stage.Transformer< COMMAND > throttler() {
        return stagePack.throttler() ;
      }
    }

    final Flowgraph.Backdoor< COMMAND > backdoor = new DefaultBackdoor() ;

    final Object lock = ToStringTools.createLockWithNiceToString( Flowgraph.class ) ;

    class DefaultFlowgraph implements Flowgraph< COMMAND > {
      private CanonicalFlowgraph< COMMAND > currentPipeline = null ;

      @Override
      public void start() throws Exception {
        PIPELINE_LOGGER.info( "Starting " + this + " ..." ) ;
        synchronized( lock ) {
          state.updateOrFail( State.STARTING, State.STOPPED ) ;
          currentPipeline = new ReplayingFlowgraph<>( PIPELINE_LOGGER, stagePack, backlogSize ) ;
          operate( currentPipeline::start, "start" ) ;
          state.updateOrFail( State.STARTED, State.STARTING ) ;
        }
        PIPELINE_LOGGER.info( "Started " + this + "." ) ;
      }

      @Override
      public void upgrade() throws Exception {
        PIPELINE_LOGGER.info( "Upgrading " + this + " ..." ) ;
        synchronized( lock ) {
          state.updateOrFail( State.UPGRADING, State.STARTED ) ;
          operate( () -> currentPipeline.stop( 1, TimeUnit.HOURS ), "stop (for upgrade)" ) ;
          currentPipeline =
              new UpgradedFlowgraph<>( PIPELINE_LOGGER, lock, stagePack, backlogSize ) ;
          operate( currentPipeline::start, "start (for upgrade)" ) ;
          state.updateOrFail( State.UPGRADED, State.UPGRADING ) ;
        }
        PIPELINE_LOGGER.info( "Upgraded " + this + "." ) ;
      }

      @Override
      public void stop( final long timeout, final TimeUnit timeUnit ) throws Exception {
        PIPELINE_LOGGER.info( "Stopping " + this + " ..." ) ;
        synchronized( lock ) {
          final StateHolder.StateUpdate< State > stateUpdate =
              state.update( State.STOPPING, State.UPGRADED, State.STARTED ) ;
          if( stateUpdate.happened() ) {
            if( currentPipeline instanceof ReplayingFlowgraph ) {
              ( ( ReplayingFlowgraph ) currentPipeline ).stopOperatesOnAllStratums( true ) ;
            }
            operate( () -> currentPipeline.stop( timeout, timeUnit ), "stop" ) ;
            state.updateOrFail( State.STOPPED, State.STOPPING ) ;
          } else {
            PIPELINE_LOGGER.warn(
                "Was in " + stateUpdate.previous + " state, not stopping " + this + "." ) ;
          }
        }
        PIPELINE_LOGGER.info( "Stopped " + this + "." ) ;
      }

      @Override
      public void injectAtEntry( final COMMAND command ) {
        try {
          FlowgraphBuilder.this.state.checkIn( this, State.STARTED, State.UPGRADED ) ;
          currentPipeline.injectAtEntry( command ) ;
        } catch( final RuntimeException e ) {
          ThrowableTools.appendMessage( e, "; was processing " + command ) ;
          throw e ;
        }
      }

      @Override
      public void injectAtExit( final COMMAND command ) {
        throw new UnsupportedOperationException( "Don't call on " + this ) ;
      }

      @Override
      public String toString() {
        return getClass().getSimpleName() + ToStringTools.compactHashForNonNull( this ) +
            '{' + state.get() + '}' ;
      }

      @Override
      public Backdoor< COMMAND > backdoor() {
        return backdoor ;
      }

      private void operate(
          final LifecycleOperation operation,
          final String operationName
      ) throws Exception {
        try {
          operation.operate() ;
        } catch( final Exception e ) {
          state.set( State.ERROR ) ;
          PIPELINE_LOGGER.error( "Could not " + operationName + " " + currentPipeline + ".", e ) ;
          currentPipeline = null ;
          throw e ;
        }
      }

    }
    return new DefaultFlowgraph() ;
  }

  private interface LifecycleOperation {
    void operate() throws Exception ;
  }


  private final StateHolder< State > state =
      new StateHolder<>( State.STOPPED, PIPELINE_LOGGER, this::toString ) ;

  public enum State {
    STOPPED, STARTING, STARTED, UPGRADING, UPGRADED, STOPPING, ERROR, ;
  }


  static {
    Environment.initializeIfEmpty().assignErrorJournal() ;
  }


}
