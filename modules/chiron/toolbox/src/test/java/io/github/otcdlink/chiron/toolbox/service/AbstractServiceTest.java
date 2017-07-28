package io.github.otcdlink.chiron.toolbox.service;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.toolbox.StringWrapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static io.github.otcdlink.chiron.toolbox.service.Service.State.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractServiceTest {

  @Test
  public void startAndStop() throws Exception {
    final SimpleService service = new SimpleService( LOGGER ) ;
    service.setup( SimpleSetup.simple() ) ;
    service.start().join() ;
    assertThat( service.stop().get() ).isEqualTo( SimpleCompletion.OK ) ;
  }

  @Test
  public void restart() throws Exception {
    final SimpleService service = new SimpleService( LOGGER ) ;
    service.setup( SimpleSetup.simple() ) ;
    service.setup( SimpleSetup.simple() ) ; // Yes, twice.
    service.start().join() ;
    service.stop().join() ;
    service.start().join() ;
    assertThat( service.stop().get() ).isEqualTo( SimpleCompletion.OK ) ;
  }

  @Test
  public void run() throws Exception {
    final SimpleService service = new SimpleService( LOGGER ) ;
    service.setup( SimpleSetup.automaticCompletion() ) ;
    assertThat( service.run().get() ).isEqualTo( SimpleCompletion.OK ) ;
  }

  @Test
  public void badStateTransition() throws Exception {
    final SimpleService service = new SimpleService( LOGGER ) ;
    assertThatThrownBy( service::start ).isInstanceOf( IllegalStateException.class ) ;
    assertThatThrownBy( service::stop ).isInstanceOf( IllegalStateException.class ) ;
    service.setup( SimpleSetup.simple() ) ;
    service.start().join() ;
    assertThatThrownBy( service::start ).isInstanceOf( IllegalStateException.class ) ;
    service.startFuture().join() ;
    service.stop().join() ;
    service.stop().join() ;
  }

  @Test
  public void runWithOwnFuture() throws Exception {
    final ExtendedService service = new ExtendedService( LOGGER ) ;
    service.start().join() ;
    final Semaphore runHasStarted = new Semaphore( 0 ) ;
    final CompletableFuture< SimpleCompletion > completionFuture = new CompletableFuture<>() ;
    service.justRunWithItsOwnFuture( completionFuture, runHasStarted ) ;
//    runHasStarted.acquire() ;
    assertThat( completionFuture.get() ).isEqualTo( SimpleCompletion.OK ) ;
  }

  @Test
  public void runWithExtendedState() throws Exception {
    LOGGER.info( "Using " + ExtendedState.RUNNING + " as custom state." ) ;
    ExtendedService extendedService = new ExtendedService( LOGGER ) ;
    final Semaphore runHasStarted = new Semaphore( 0 ) ;
    final Semaphore runCanComplete = new Semaphore( 0 ) ;

    extendedService.start().join() ;
    final CompletableFuture< SimpleCompletion > runCompletion =
        extendedService.justRun( runHasStarted, runCanComplete ) ;

    runHasStarted.acquire() ;
    assertThat( runCompletion.isDone() ).isFalse() ;
    runCanComplete.release() ;
    assertThat( runCompletion.get() ).isEqualTo( SimpleCompletion.OK ) ;
    extendedService.stop().join() ;
  }

  @Test
  public void exceptionalRun() throws Exception {
    LOGGER.info( "Using " + ExtendedState.RUNNING + " as custom state." ) ;
    ExtendedService extendedService = new ExtendedService( LOGGER ) ;
    final Semaphore runHasStarted = new Semaphore( 0 ) ;
    final Semaphore runCanComplete = new Semaphore( 0 ) ;

    extendedService.start().join() ;
    final CompletableFuture< SimpleCompletion > runCompletion =
        extendedService.runExceptionally( runHasStarted, runCanComplete ) ;

    runHasStarted.acquire() ;
    assertThat( runCompletion.isDone() ).isFalse() ;
    runCanComplete.release() ;
    assertThatThrownBy( runCompletion::get ).hasMessageContaining( "Boom" ) ;
    extendedService.stop().join() ;
  }


  @Test
  public void cancellableRun() throws Exception {
    LOGGER.info( "Using " + ExtendedState.RUNNING + " as custom state." ) ;
    final ExtendedService extendedService = new ExtendedService( LOGGER ) ;
    final Semaphore runHasStarted = new Semaphore( 0 ) ;

    extendedService.start().join() ;
    final CompletableFuture< SimpleCompletion > runCompletion =
        extendedService.loopUntilInterrupted( runHasStarted ) ;

    runHasStarted.acquire() ;
    runCompletion.cancel( false ) ;  // No need to require thread interruption.
    extendedService.stop().join() ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( AbstractServiceTest.class ) ;


// ======
// Simple
// ======

  private static final class SimpleSetup {
    public final boolean automaticCompletion ;

    private SimpleSetup( final boolean automaticCompletion ) {
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

  private static class SimpleService
      extends AbstractService< SimpleSetup, SimpleCompletion >
  {

    public SimpleService( Logger logger ) {
      super( logger, "simple" ) ;
    }

    @Override
    protected void customStart() throws Exception {
      super.customStart() ;
      if( setup().automaticCompletion ) {
        stop() ;
      }
    }

    @Override
    protected void customEffectiveStop() throws Exception {
      completion( SimpleCompletion.OK ) ;
      super.customEffectiveStop() ;
    }
  }

// ========
// Extended
// ========

  private static class NoSetup {
    private NoSetup() { }

    private static final NoSetup INSTANCE = new NoSetup() ;
  }

  private static class ExtendedService extends AbstractService< NoSetup, Void > {

    public ExtendedService( Logger logger ) {
      super( logger, "extended" ) ;
      setup( NoSetup.INSTANCE ) ;
    }

    private final ExecutorService runExecutorService = Executors.newSingleThreadExecutor(
        threadFactory( "run", null ) ) ;

    public CompletableFuture< SimpleCompletion > justRun(
        final Semaphore runHasStarted,
        final Semaphore runCanComplete
    ) {
      return compute(
          runExecutorService,
          () -> {
            runHasStarted.release() ;
            runCanComplete.acquire() ;
            return SimpleCompletion.OK ;
          },
          state -> state == STARTED
      ) ;
    }

    public void justRunWithItsOwnFuture(
        final CompletableFuture< SimpleCompletion > completionFuture,
        final Semaphore runHasStarted
    ) {
      compute(
          runExecutorService,
          IS_STARTED,
          () -> {
            completionFuture.complete( SimpleCompletion.OK ) ;
            runHasStarted.release() ;
          },
          completionFuture
      ) ;
    }

    public CompletableFuture< SimpleCompletion > runExceptionally(
        final Semaphore runHasStarted,
        final Semaphore runCanComplete
    ) {
      return compute(
          runExecutorService,
          () -> {
            runHasStarted.release() ;
            runCanComplete.acquire() ;
            throw new Exception( "Boom" ) ;
          },
          state -> state == STARTED
      ) ;
    }


    public CompletableFuture< SimpleCompletion > loopUntilInterrupted(
        final Semaphore runHasStarted
    ) {
      return compute(
          runExecutorService,
          () -> {
            while( ! Thread.currentThread().isInterrupted() ) {
              LOGGER.info( "Looping until current thread gets interrupted ..." ) ;
              runHasStarted.release() ;
            }
            return SimpleCompletion.OK ;
          },
          state -> state == STARTED
      ) ;
    }
  }

  public static class ExtendedState extends Service.State {

    public static final ExtendedState RUNNING = new ExtendedState() ;

    @SuppressWarnings( "unused" )
    public static final ImmutableMap< String, ExtendedState > MAP =
        valueMap( ExtendedState.class ) ;
  }



}