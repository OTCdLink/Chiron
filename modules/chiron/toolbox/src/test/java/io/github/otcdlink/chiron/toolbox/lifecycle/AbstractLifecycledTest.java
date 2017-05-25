package io.github.otcdlink.chiron.toolbox.lifecycle;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.toolbox.StringWrapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractLifecycledTest {

  @Test
  public void startAndStop() throws Exception {
    final SimpleLifecycled lifecycled = new SimpleLifecycled( LOGGER ) ;
    lifecycled.initialize( SimpleSetup.simple() ) ;
    lifecycled.start().join() ;
    assertThat( lifecycled.stop().get() ).isEqualTo( SimpleCompletion.OK ) ;
  }

  @Test
  public void restart() throws Exception {
    final SimpleLifecycled lifecycled = new SimpleLifecycled( LOGGER ) ;
    lifecycled.initialize( SimpleSetup.simple() ) ;
    lifecycled.initialize( SimpleSetup.simple() ) ; // Yes, twice.
    lifecycled.start().join() ;
    lifecycled.stop().join() ;
    lifecycled.start().join() ;
    assertThat( lifecycled.stop().get() ).isEqualTo( SimpleCompletion.OK ) ;
  }

  @Test
  public void run() throws Exception {
    final SimpleLifecycled lifecycled = new SimpleLifecycled( LOGGER ) ;
    lifecycled.initialize( SimpleSetup.automaticCompletion() ) ;
    assertThat( lifecycled.run().get() ).isEqualTo( SimpleCompletion.OK ) ;
  }

  @Test
  public void badStateTransition() throws Exception {
    final SimpleLifecycled lifecycled = new SimpleLifecycled( LOGGER ) ;
    assertThatThrownBy( lifecycled::start ).isInstanceOf( IllegalStateException.class ) ;
    assertThatThrownBy( lifecycled::stop ).isInstanceOf( IllegalStateException.class ) ;
    lifecycled.initialize( SimpleSetup.simple() ) ;
    lifecycled.start().join() ;
    assertThatThrownBy( lifecycled::start ).isInstanceOf( IllegalStateException.class ) ;
    lifecycled.startFuture().join() ;
    lifecycled.stop().join() ;
    assertThatThrownBy( lifecycled::stop ).isInstanceOf( IllegalStateException.class ) ;
  }

  @Test
  public void runWithExtendedState() throws Exception {
    LOGGER.info( "Using " + ExtendedState.RUNNING + " as custom state." ) ;
    ExtendedLifecycled extendedLifecycled = new ExtendedLifecycled( LOGGER ) ;
    final Semaphore runHasStarted = new Semaphore( 0 ) ;
    final Semaphore runCanComplete = new Semaphore( 0 ) ;

    extendedLifecycled.start().join() ;
    final CompletableFuture< SimpleCompletion > runCompletion =
        extendedLifecycled.justRun( runHasStarted, runCanComplete ) ;

    runHasStarted.acquire() ;
    assertThat( runCompletion.isDone() ).isFalse() ;
    runCanComplete.release() ;
    assertThat( runCompletion.get() ).isEqualTo( SimpleCompletion.OK ) ;
    extendedLifecycled.stop().join() ;
  }

  @Test
  public void exceptionalRun() throws Exception {
    LOGGER.info( "Using " + ExtendedState.RUNNING + " as custom state." ) ;
    ExtendedLifecycled extendedLifecycled = new ExtendedLifecycled( LOGGER ) ;
    final Semaphore runHasStarted = new Semaphore( 0 ) ;
    final Semaphore runCanComplete = new Semaphore( 0 ) ;

    extendedLifecycled.start().join() ;
    final CompletableFuture< SimpleCompletion > runCompletion =
        extendedLifecycled.runExceptionally( runHasStarted, runCanComplete ) ;

    runHasStarted.acquire() ;
    assertThat( runCompletion.isDone() ).isFalse() ;
    runCanComplete.release() ;
    assertThatThrownBy( runCompletion::get ).hasMessageContaining( "Boom" ) ;
    extendedLifecycled.stop().join() ;
  }


  @Test
  public void cancellableRun() throws Exception {
    LOGGER.info( "Using " + ExtendedState.RUNNING + " as custom state." ) ;
    final ExtendedLifecycled extendedLifecycled = new ExtendedLifecycled( LOGGER ) ;
    final Semaphore runHasStarted = new Semaphore( 0 ) ;

    extendedLifecycled.start().join() ;
    final CompletableFuture< SimpleCompletion > runCompletion =
        extendedLifecycled.loopUntilInterrupted( runHasStarted ) ;

    runHasStarted.acquire() ;
    runCompletion.cancel( false ) ;  // No need to require thread interruption.
    extendedLifecycled.stop().join() ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( AbstractLifecycledTest.class ) ;


// ======
// Simple
// ======

  private static final class SimpleSetup {
    public final boolean automaticCompletion ;

    private SimpleSetup( boolean automaticCompletion ) {
      this.automaticCompletion = automaticCompletion ;
    }

    public static SimpleSetup automaticCompletion() {
      return new SimpleSetup( true ) ;
    }

    public static SimpleSetup simple() {
      return new SimpleSetup( false ) ;
    }
  }

  private static final class SimpleCompletion extends StringWrapper< SimpleCompletion > {
    protected SimpleCompletion( String wrapped ) {
      super( wrapped ) ;
    }

    public static final SimpleCompletion OK = new SimpleCompletion( "OK" ) ;
  }

  private static class SimpleLifecycled
      extends AbstractLifecycled< SimpleSetup, SimpleCompletion >
  {

    public SimpleLifecycled( Logger logger ) {
      super( logger ) ;
    }

    @Override
    protected void customStart() throws Exception {
      super.customStart() ;
      if( setup().automaticCompletion ) {
        stop() ;
      }
    }

    @Override
    protected void customStop() throws Exception {
      completion( SimpleCompletion.OK ) ;
      super.customStop() ;
    }
  }

// ========
// Extended
// ========

  private static class NoSetup {
    private NoSetup() { }

    private static final NoSetup INSTANCE = new NoSetup() ;
  }

  private static class ExtendedLifecycled extends AbstractLifecycled< NoSetup, Void > {

    public ExtendedLifecycled( Logger logger ) {
      super( logger ) ;
      initialize( NoSetup.INSTANCE ) ;
    }

    private final ExecutorService runExecutorService = Executors.newSingleThreadExecutor(
        threadFactory( getClass().getSimpleName(), "run", null ) ) ;

    public CompletableFuture< SimpleCompletion > justRun(
        final Semaphore runHasStarted,
        final Semaphore runCanComplete
    ) {
      return execute(
          runExecutorService,
          () -> {
            runHasStarted.release() ;
            runCanComplete.acquire() ;
            return SimpleCompletion.OK ;
          },
          state -> state == State.STARTED,
          ExtendedState.RUNNING
      ) ;
    }

    public CompletableFuture< SimpleCompletion > runExceptionally(
        final Semaphore runHasStarted,
        final Semaphore runCanComplete
    ) {
      return execute(
          runExecutorService,
          () -> {
            runHasStarted.release() ;
            runCanComplete.acquire() ;
            throw new Exception( "Boom" ) ;
          },
          state -> state == State.STARTED,
          ExtendedState.RUNNING
      ) ;
    }


    public CompletableFuture< SimpleCompletion > loopUntilInterrupted(
        final Semaphore runHasStarted
    ) {
      return execute(
          runExecutorService,
          () -> {
            while( ! Thread.currentThread().isInterrupted() ) {
              LOGGER.info( "Looping until current thread gets interrupted ..." ) ;
              runHasStarted.release() ;
            }
            return SimpleCompletion.OK ;
          },
          state -> state == State.STARTED,
          ExtendedState.RUNNING
      ) ;
    }
  }

  public static class ExtendedState extends Lifecycled.State {

    public static final ExtendedState RUNNING = new ExtendedState() ;

    @SuppressWarnings( "unused" )
    public static final ImmutableMap< String, ExtendedState > MAP =
        valueMap( ExtendedState.class ) ;
  }



}