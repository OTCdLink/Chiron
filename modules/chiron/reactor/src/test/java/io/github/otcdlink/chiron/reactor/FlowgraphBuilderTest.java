package io.github.otcdlink.chiron.reactor;


import io.github.otcdlink.chiron.fixture.CatcherFixture;
import io.github.otcdlink.chiron.toolbox.LatencyEvaluator;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.otcdlink.chiron.reactor.FakeCommand.downwardChangePassword;
import static io.github.otcdlink.chiron.reactor.FakeCommand.downwardFailure;
import static io.github.otcdlink.chiron.reactor.FakeCommand.downwardMonitoring;
import static io.github.otcdlink.chiron.reactor.FakeCommand.downwardProposal;
import static io.github.otcdlink.chiron.reactor.FakeCommand.internalChangePassword;
import static io.github.otcdlink.chiron.reactor.FakeCommand.internalHashPassword;
import static io.github.otcdlink.chiron.reactor.FakeCommand.internalSendEmail;
import static io.github.otcdlink.chiron.reactor.FakeCommand.internalSessionCreate;
import static io.github.otcdlink.chiron.reactor.FakeCommand.internalSessionCreated;
import static io.github.otcdlink.chiron.reactor.FakeCommand.internalSetThrottling;
import static io.github.otcdlink.chiron.reactor.FakeCommand.internalThrottlerDelay;
import static io.github.otcdlink.chiron.reactor.FakeCommand.upwardChangePassword;
import static io.github.otcdlink.chiron.reactor.FakeCommand.upwardMonitoring;
import static io.github.otcdlink.chiron.reactor.FakeCommand.upwardPreconfirmation;
import static io.github.otcdlink.chiron.reactor.FakeCommand.upwardProposal;
import static org.assertj.core.api.Assertions.assertThat;

//@Ignore( "Ignoring for release since we switched to old official Reactor version")
public class FlowgraphBuilderTest {

  @Test( timeout = TIMEOUT )
  public void stopWithBrokenPipeline() throws Exception {

    final FakeStage.Pack casting = FakeStage.newCasting( catcher, FakeStage.Setup.FAILABLE ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    final FakeStage.EmailSender emailSender = casting.emailSender() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;
    final FakeStage.PasswordHasher passwordHasher = casting.passwordHasher() ;
    final FakeStage.Logic logic = casting.logic() ;
    passwordHasher.fail = true ;

    flowgraph.start() ;
    flowgraph.upgrade() ;
    flowgraph.injectAtEntry( upwardChangePassword( 1 ) ) ;
    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

  }


  @Test( timeout = TIMEOUT )
  public void catcher() throws Exception {

    final FakeStage.Pack casting = FakeStage.newCasting( catcher, FakeStage.Setup.FAILABLE ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    final FakeStage.EmailSender emailSender = casting.emailSender() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;
    final FakeStage.PasswordHasher passwordHasher = casting.passwordHasher() ;
    final FakeStage.Logic logic = casting.logic() ;
    logic.fail = true ;

    flowgraph.start() ;
    flowgraph.upgrade() ;
    flowgraph.injectAtEntry( upwardPreconfirmation( 1 ) ) ;
    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

    assertThat( logic.nextRecordedCommand() ).isEqualTo( upwardPreconfirmation( 1 ) ) ;

    assertThat( emailSender.hasNextRecordedCommand() ).isFalse() ;
    assertThat( logic.hasNextRecordedCommand() ).isFalse() ;
    assertThat( passwordHasher.hasNextRecordedCommand() ).isFalse() ;

    assertThat( httpDownward.nextRecordedCommand() ).isEqualTo( downwardFailure() ) ;
    assertThat( httpDownward.hasNextRecordedCommand() ).isFalse() ;

    assertThat( catcher.records() ).hasSize( 1 ) ;
    assertThat( catcher.records().get( 0 ).throwable )
        .isInstanceOf( FakeStage.PlannedException.class ) ;
  }

  @Test( timeout = TIMEOUT )
  public void sendEmail() throws Exception {

    final FakeStage.Pack casting = FakeStage.newCasting( catcher, FakeStage.Setup.DEFAULT ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    flowgraph.start() ;
    flowgraph.upgrade() ;
    flowgraph.injectAtEntry( upwardPreconfirmation( 1 ) ) ;
    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

    final FakeStage.Logic logic = casting.logic() ;
    final FakeStage.EmailSender emailSender = casting.emailSender() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;
    final FakeStage.PasswordHasher passwordHasher = casting.passwordHasher() ;

    assertThat( logic.nextRecordedCommand() ).isEqualTo( upwardPreconfirmation( 1 ) ) ;
    assertThat( emailSender.nextRecordedCommand() ).isEqualTo( internalSendEmail( 1 ) ) ;

    assertThat( emailSender.hasNextRecordedCommand() ).isFalse() ;
    assertThat( logic.hasNextRecordedCommand() ).isFalse() ;
    assertThat( passwordHasher.hasNextRecordedCommand() ).isFalse() ;
    assertThat( httpDownward.hasNextRecordedCommand() ).isFalse() ;

    catcher.assertEmpty() ;
  }


  @Test( timeout = TIMEOUT )
  public void createSession() throws Exception {

    final FakeStage.Pack casting = FakeStage.newCasting( catcher, FakeStage.Setup.DEFAULT ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    flowgraph.start() ;
    flowgraph.upgrade() ;
    flowgraph.injectAtEntry( internalSessionCreate( 1 ) ) ;
    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

    final FakeStage.Logic logic = casting.logic() ;
    final FakeStage.SessionSupervisor sessionSupervisor = casting.sessionSupervisor() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;
    final FakeStage.PasswordHasher passwordHasher = casting.passwordHasher() ;

    assertThat( logic.nextRecordedCommand() ).isEqualTo( internalSessionCreate( 1 ) ) ;
    assertThat( sessionSupervisor.nextRecordedCommand() ).isEqualTo( internalSessionCreated( 1 ) ) ;

    assertThat( sessionSupervisor.hasNextRecordedCommand() ).isFalse() ;
    assertThat( logic.hasNextRecordedCommand() ).isFalse() ;
    assertThat( passwordHasher.hasNextRecordedCommand() ).isFalse() ;
    assertThat( httpDownward.hasNextRecordedCommand() ).isFalse() ;

    catcher.assertEmpty() ;
  }


  @Test( timeout = TIMEOUT )
  public void setThrottling() throws Exception {

    final FakeStage.Pack casting = FakeStage.newCasting( catcher, FakeStage.Setup.DEFAULT ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    flowgraph.start() ;
    flowgraph.upgrade() ;
    flowgraph.injectAtEntry( internalSetThrottling( 1 ) ) ;

    final FakeStage.Logic logic = casting.logic() ;
    final FakeStage.EmailSender emailSender = casting.emailSender() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;
    final FakeStage.PasswordHasher passwordHasher = casting.passwordHasher() ;
    final FakeStage.Throttler throttler = casting.throttler() ;

    assertThat( throttler.nextRecordedCommand() ).isEqualTo( internalSetThrottling( 1 ) ) ;
    assertThat( logic.nextRecordedCommand() ).isEqualTo( internalSetThrottling( 1 ) ) ;
    assertThat( throttler.nextRecordedCommand() ).isEqualTo( internalThrottlerDelay( 1 ) ) ;

    assertThat( throttler.hasNextRecordedCommand() ).isFalse() ;
    assertThat( logic.hasNextRecordedCommand() ).isFalse() ;
    assertThat( emailSender.hasNextRecordedCommand() ).isFalse() ;
    assertThat( passwordHasher.hasNextRecordedCommand() ).isFalse() ;
    assertThat( httpDownward.hasNextRecordedCommand() ).isFalse() ;

    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

    catcher.assertEmpty() ;
  }


  @Test( timeout = TIMEOUT )
  public void throttlingSendsFailure() throws Exception {

    final FakeStage.Pack casting = FakeStage.newCasting( catcher, FakeStage.Setup.DEFAULT ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    flowgraph.start() ;
    flowgraph.upgrade() ;

    final FakeStage.Logic logic = casting.logic() ;
    final FakeStage.EmailSender emailSender = casting.emailSender() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;
    final FakeStage.PasswordHasher passwordHasher = casting.passwordHasher() ;
    final FakeStage.Throttler throttler = casting.throttler() ;

    throttler.refuse = true ;
    flowgraph.injectAtEntry( internalSetThrottling( 1 ) ) ;

    assertThat( throttler.nextRecordedCommand() ).isEqualTo( internalSetThrottling( 1 ) ) ;
    assertThat( httpDownward.nextRecordedCommand() ).isEqualTo( FakeCommand.downwardFailure() ) ;

    assertThat( throttler.hasNextRecordedCommand() ).isFalse() ;
    assertThat( logic.hasNextRecordedCommand() ).isFalse() ;
    assertThat( emailSender.hasNextRecordedCommand() ).isFalse() ;
    assertThat( passwordHasher.hasNextRecordedCommand() ).isFalse() ;
    assertThat( httpDownward.hasNextRecordedCommand() ).isFalse() ;

    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

    catcher.assertEmpty() ;
  }


  @Test( timeout = TIMEOUT )
  public void hashPassword() throws Exception {

    final FakeStage.Pack casting = FakeStage.newCasting( catcher, FakeStage.Setup.DEFAULT ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    flowgraph.start() ;
    flowgraph.upgrade() ;
    flowgraph.injectAtEntry( upwardChangePassword( 1 ) ) ;

    final FakeStage.Logic logic = casting.logic() ;
    final FakeStage.PasswordHasher passwordHasher = casting.passwordHasher() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;

    assertThat( logic.nextRecordedCommand() ).isEqualTo( upwardChangePassword( 1 ) ) ;
    assertThat( passwordHasher.nextRecordedCommand() ).isEqualTo( internalHashPassword( 1 ) ) ;
    assertThat( logic.nextRecordedCommand() ).isEqualTo( internalChangePassword( 1000 ) ) ;
    assertThat( httpDownward.nextRecordedCommand() ).isEqualTo( downwardChangePassword( 1000 ) ) ;

    /** Don't stop before getting {@link FakeCommand}s, as {@link Flowgraph#injectAtTop(Object)} breaks
     * the Reactive contract, so {@link Flowgraph#stop(long, TimeUnit)} can occur before the
     * {@link io.github.otcdlink.chiron.reactor.FakeCommand.Kind#INTERNAL_CHANGE_PASSWORD}
     * reaches the {@link FakeStage.Logic}. */
    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

    assertThat( passwordHasher.hasNextRecordedCommand() ).isFalse() ;
    assertThat( logic.hasNextRecordedCommand() ).isFalse() ;
    assertThat( httpDownward.hasNextRecordedCommand() ).isFalse() ;

    catcher.assertEmpty() ;
  }

  @Test( timeout = TIMEOUT )
  public void simpleForkJoin() throws Exception {

    final FakeStage.Pack casting = FakeStage.newCasting( catcher, FakeStage.Setup.DEFAULT ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    flowgraph.start() ;
    flowgraph.upgrade() ;
    flowgraph.injectAtEntry( FakeCommand.upwardProposal( 2 ) ) ;
    flowgraph.injectAtEntry( FakeCommand.upwardMonitoring() ) ;
    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

    final FakeStage.Throttler throttler = casting.throttler() ;
    final FakeStage.Logic logic = casting.logic() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;

    assertThat( throttler.callCount() ).isEqualTo( 2 ) ;

    assertThat( logic.nextRecordedCommand() ).isEqualTo( upwardProposal( 2 ) ) ;
    assertThat( logic.nextRecordedCommand() ).isEqualTo( upwardMonitoring() ) ;
    assertThat( logic.hasNextRecordedCommand() ).isFalse() ;
    assertThat( logic.callCount() ).isEqualTo( 2 ) ;

    assertThat( httpDownward.nextRecordedCommand() ).isEqualTo( downwardProposal( 0 ) ) ;
    assertThat( httpDownward.nextRecordedCommand() ).isEqualTo( downwardProposal( 1 ) ) ;
    assertThat( httpDownward.nextRecordedCommand() ).isEqualTo( downwardMonitoring() ) ;
    assertThat( httpDownward.hasNextRecordedCommand() ).isFalse() ;
    assertThat( httpDownward.callCount() ).isEqualTo( 3 ) ;

    catcher.assertEmpty() ;
  }

  @Test( timeout = TIMEOUT )
  public void replaying() throws Exception {

    final FakeStage.Pack casting = FakeStage.newCasting( catcher, FakeStage.Setup.DEFAULT ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    flowgraph.start() ;
    flowgraph.injectAtEntry( FakeCommand.upwardProposal( 2 ) ) ;
    flowgraph.injectAtEntry( FakeCommand.upwardMonitoring() ) ;
    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

    final FakeStage.Throttler throttler = casting.throttler() ;
    final FakeStage.Logic logic = casting.logic() ;
    final FakeStage.Persister persister = casting.persister() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;

    assertThat( persister.callCount() ).isEqualTo( 2 ) ;

    assertThat( throttler.callCount() ).isEqualTo( 0 ) ;

    assertThat( httpDownward.callCount() ).isEqualTo( 0 ) ;

    assertThat( logic.nextRecordedCommand() ).isEqualTo( upwardProposal( 2 ) ) ;
    assertThat( logic.nextRecordedCommand() ).isEqualTo( upwardMonitoring() ) ;
    assertThat( logic.hasNextRecordedCommand() ).isFalse() ;
    assertThat( logic.callCount() ).isEqualTo( 2 ) ;


    catcher.assertEmpty() ;
  }

  @Test( timeout = TIMEOUT )
  public void replicator() throws Exception {

    final FakeStage.Pack casting =
        FakeStage.newCastingWithReplicator( catcher, FakeStage.Setup.DEFAULT ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting ) ;

    flowgraph.start() ;
    flowgraph.upgrade() ;
    flowgraph.injectAtEntry( FakeCommand.upwardMonitoring() ) ;
    flowgraph.stop( 10, TimeUnit.SECONDS ) ;

    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;

    assertThat( httpDownward.callCount() ).isEqualTo( 1 ) ;
    catcher.assertEmpty() ;

  }

  @Test( timeout = TIMEOUT )
  public void fastForkJoin() throws Exception {
    final LatencyEvaluator< Integer > latencyEvaluator = new LatencyEvaluator<>() ;
    fastForkJoin(
        latencyEvaluator,
        0,
        2,
        1,
        1,
        FakeStage.newCasting( catcher, FakeStage.Setup.DEFAULT ),
        1024,
        1,
        TimeUnit.SECONDS
//        TimeUnit.HOURS
    ) ;
  }

  @Test( timeout = TIMEOUT )
  public void miniLoadTest() throws Exception {

    final LatencyEvaluator< Integer > latencyEvaluator = new LatencyEvaluator<>() ;

    final int testRunCount = 3 ;
    final int spreadFactor = 10 ;
    final int insertions = 12 ;
    final int threadCount = 3 ;
//    final int testRunCount = 1 ;
//    final int spreadFactor = 10 ;
//    final int insertions = 9900 ;
//    final int threadCount = 99 ;


    final int backlogSize = 16 ;
    final int timeout = 1 ;
    final TimeUnit timeUnit = TimeUnit.HOURS ;

    LOGGER.info(
        "\nRunning test " +
        "\n  " + testRunCount + " time(s), " +
        "\n  with a spread of " + spreadFactor + " (one single inserted " +
            FakeCommand.class.getSimpleName() + " causing " + spreadFactor + " ones)," +
        "\n  inserting " + insertions + " " + FakeCommand.class.getSimpleName() + "s at all, " +
        "\n  using a backlog size of " + backlogSize + " ... "
    ) ;

    for( int i = 0 ; i < testRunCount ; i ++ ) {
      fastForkJoin(
          latencyEvaluator,
          i,
          insertions,
          spreadFactor,
          threadCount,
          FakeStage.newCasting( catcher, FakeStage.Setup.FASTEST ),
          backlogSize,
          timeout,
          timeUnit
      ) ;
      LOGGER.debug( "*** Single test occurence " + i + " complete. *** " ) ;
    }
    catcher.assertEmpty() ;

    LOGGER.info( "\nReport: \n" +
        report( latencyEvaluator.combinedLatency(), insertions, spreadFactor ) ) ;

  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( FlowgraphBuilderTest.class ) ;

  private final ReactiveAwareRecordingCatcher< FakeCommand > catcher =
      new ReactiveAwareRecordingCatcher<>() ;

  private static < TOPIC > void fastForkJoin(
      final LatencyEvaluator< TOPIC > latencyEvaluator,
      final TOPIC latencyEvaluatorTopic,
      final int insertionCount,
      final int spread,
      final int threadCount,
      final FakeStage.Pack casting,
      final int backlogSize,
      final long timeout,
      final TimeUnit timeUnit
  ) throws Exception {
    checkArgument( threadCount > 0 ) ;
    checkArgument( insertionCount % threadCount == 0, "insertionCount (" + insertionCount +
        ") should be a multiple of threadCount (" + threadCount + ")" ) ;

    final Semaphore doneSemaphore = new Semaphore( 0 ) ;
    final Flowgraph< FakeCommand > flowgraph = new FlowgraphBuilder().build( casting, backlogSize ) ;
    final FakeStage.Logic logic = casting.logic() ;
    final FakeStage.HttpDownward httpDownward = casting.httpDownward() ;

    flowgraph.start() ;
    flowgraph.upgrade() ;
    latencyEvaluator.begin( latencyEvaluatorTopic ) ;

    for( int j = 0 ; j < threadCount ; j ++ ) {
      new Thread(
          () -> {
            for( int i = 0 ; i < insertionCount / threadCount ; i++ ) {
              flowgraph.injectAtEntry( FakeCommand.upwardProposal( spread ) ) ;
            }
            doneSemaphore.release() ;
          },
          "injector-" + j
      ).start() ;
    }
    doneSemaphore.acquire( threadCount ) ;

    flowgraph.stop( timeout, timeUnit ) ;
    latencyEvaluator.end( latencyEvaluatorTopic ) ;

    assertThat( logic.callCount() )
        .describedAs( "Calls to " + logic.getClass().getSimpleName() )
        .isEqualTo( insertionCount )
    ;
    assertThat( httpDownward.callCount() )
        .describedAs( "Calls to " + httpDownward.getClass().getSimpleName() )
        .isEqualTo( spread * insertionCount )
    ;
  }

  private static String report(
      final LatencyEvaluator.CombinedLatency combinedLatency,
      final int insertionCount,
      final int spreadFactor
  ) {
    final long insertedCommandCount = combinedLatency.occurenceCount() * insertionCount ;
    final long exertedCommandCount = insertedCommandCount * spreadFactor ;
    final double insertionThroughputPerS =
        ( ( double ) insertedCommandCount ) /
        ( ( double ) combinedLatency.cumulatedDelayMilliseconds() )
        * 1000d
    ;
    final double exertionThroughputPerS =
        ( ( double ) exertedCommandCount ) /
        ( ( double ) combinedLatency.cumulatedDelayMilliseconds() )
        * 1000d
    ;
    return
        "     Measurement duration: " + combinedLatency.overallMeasurementDuration() + " ms \n" +
        "   Inserted command count: " + insertedCommandCount + "\n" +
        "    Exerted command count: " + exertedCommandCount + "\n" +
        "     Insertion throughput: " + String.format( "%.01f", insertionThroughputPerS ) +
            " command/s \n" +
        "      Exertion throughput: " + String.format( "%.01f", exertionThroughputPerS ) +
            " command/s \n"
    ;
  }


  public static class ReactiveAwareRecordingCatcher< COMMAND >
      extends CatcherFixture.RecordingCatcher< ReactiveAwareRecordingCatcher.Record< COMMAND > >
      implements ReactiveAwareCatcher< COMMAND >
  {

    @Override
    public void processThrowable( final Throwable throwable ) {
      processThrowable( null, throwable ) ;
    }

    @Override
    public void processThrowable( final COMMAND command, final Throwable throwable ) {
      super.processThrowable( throwable ) ;
      addRecord( new Record<>( command, throwable ) ) ;
    }

    public static final class Record< COMMAND > extends CatcherFixture.Record{
      public final COMMAND command ;

      public Record( final COMMAND command, final Throwable throwable ) {
        super( throwable ) ;
        this.command = command ;
      }
    }
  }



//  private static final long TIMEOUT = 5_000 ;
  private static final long TIMEOUT = 1_000_000 ;
}