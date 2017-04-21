package io.github.otcdlink.chiron.reactor;

import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.toolbox.MultiplexingException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.fn.Function;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.broadcast.Broadcaster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.reactor.FlowgraphBuilderTools.createProcessor;
import static io.github.otcdlink.chiron.reactor.FlowgraphBuilderTools.dispatcherShutdown;
import static io.github.otcdlink.chiron.reactor.FlowgraphBuilderTools.semaphoreAcquisition;
import static io.github.otcdlink.chiron.reactor.FlowgraphBuilderTools.stratumShutdown;

/**
 * <pre>
 *             Pipeline#inject( COMMAND )
 *                        |
 *                        v
 *                insertionBroadcaster  <------------------\
 *                  throttlerStream                         \
 *               /        |          \                       |
 *              /         |           \                      |
 * logicProcessor  persisterProcessor  replicatorProcessor   |
 *   logicStream     persisterStream    replicatorStream     |
 *              \          |          /                      |
 *               \         |         /                 (not reactive)
 *                v        v        v                        |
 *                   joinerStream    /----set throttling---->|
 *                 linearizerStream /                        |
 *                   rerouteAction -----> passwordHasherProcessor
 *                         |     |         passwordHasherStream
 *                         |      \
 *                         |       \----> emailSenderProcessor
 *                         |               emailSenderStream
 *                         |
 *                         v
 *                httpDownwardProcessor
 * </pre>
 */
class UpgradedFlowgraph< COMMAND > extends AbstractCanonicalFlowgraph< COMMAND > {

  private static final Logger LOGGER = LoggerFactory.getLogger( UpgradedFlowgraph.class ) ;

  private final Object lock ;
  private final Broadcaster< COMMAND > insertionBroadcaster;
  private final Stream< COMMAND > httpDownwardStream ;
  private final Stream< COMMAND > passwordHasherStream ;
  private final Stream< COMMAND > emailSenderStream ;
  private final Stream< COMMAND > sessionSupervisorStream ;
  private final Stream< COMMAND > reinjecterStream ;
  private final SubscriberAwareFilterAction< COMMAND > rerouteAction ;

  /**
   * Blocking stuff here but it's OK, only using it for password change and
   * {@link #stop(long, TimeUnit)}.
   */
  private final FlowgraphBuilderTools.SignallingCounter reinjectionCounter =
      new FlowgraphBuilderTools.SignallingCounter() ;

  public UpgradedFlowgraph(
      final Logger logger,
      final Object lock,
      final StagePack< COMMAND > stagePack,
      final int backlogSize
  ) {
    super( logger, stagePack, backlogSize ) ;
    this.lock = checkNotNull( lock ) ;

    insertionBroadcaster = Broadcaster.create( dispatcher ) ;

    final Processor< COMMAND, COMMAND > logicProcessor = createProcessor( "logic", backlogSize ) ;
    final Processor< COMMAND, COMMAND >  persisterProcessor =
        createProcessor( "persister", backlogSize ) ;
    final Processor< COMMAND, COMMAND >  passwordHasherProcessor =
        createProcessor( "passwordHasher", backlogSize ) ;
    final Processor< COMMAND, COMMAND >  httpDownwardProcessor =
        createProcessor( "httpDownward", backlogSize ) ;
    final Processor< COMMAND, COMMAND >  emailSenderProcessor =
        createProcessor( "emailSender", backlogSize ) ;
    final Processor< COMMAND, COMMAND >  sessionSupervisorProcessor =
        createProcessor( "sessionSupervisor", backlogSize ) ;
    final Broadcaster< COMMAND > reinjecterBroadcaster = Broadcaster.create() ;

    final Stream< COMMAND > throttlerStream = insertionBroadcaster
        .dispatchOn( dispatcher )
        //.observe( c -> logger.debug( "throttlerStream#observe " + c ) )
        .observeError( Throwable.class, errorLogger( "throttlerStream#observeError" ) )
        .observeComplete( Ø -> logger.debug( "throttlerStream#observeComplete" ) )
        .map( c -> stagePack.throttler().apply( c ) )
        .broadcast()
    ;

    final Logger logicLogger = LoggerFactory.getLogger( stagePack.logic().getClass() ) ;

    final Stream< ImmutableList< COMMAND > > logicStream = Streams
        .wrap( logicProcessor )
        //.observe( c -> logger.debug( "logicStream#observe " + c ) )
//        .observeComplete(
//            Ø -> logger.debug( "logicStream#observeComplete" ) )
        .map( c -> {
          /** Avoids logging in {@link ReplayingFlowgraph}. */
          logicLogger.info( "Processing "+ c + " ..." ) ;
          try {
            return failurePassthrough( stagePack.logic(), stagePack::isFailureCommand, c ) ;
          } catch( final Exception e ) {
            stagePack.catcher.processThrowable( c, e ) ;
            final COMMAND translated = stagePack.errorTranslator.apply( e, c ) ;
            return translated == null ? null : ImmutableList.of( translated ) ;
          }
        } )
    ;

    final Stream< ImmutableList< COMMAND > > persisterStream = Streams
        .wrap( persisterProcessor )
        //.observe( c -> logger.debug( "persisterStream#observe " + c ) )
        // .observeComplete(
        //    Ø -> logger.debug( "persisterStream#observeComplete." ) )
        .observeError( Throwable.class, errorLogger( "persisterStream#observeError" ) )
        .map( c -> failurePassthrough( stagePack.persister(), stagePack::isFailureCommand, c ) )
//        .map( c -> ImmutableList.of() )
    ;

    final List< Publisher< ? extends ImmutableList< COMMAND > > > joined = new ArrayList<>( 3 ) ;
    joined.add( logicStream ) ;
    joined.add( persisterStream ) ;

    final Stream< List< COMMAND > > joinStream = Streams
        .join( joined )
        //.observe( listOfLists ->
        //    logger.debug( "joinStream#observe " + listOfLists + "." ) )
        // .observeComplete( Ø -> logger.debug( "joinStream#observeComplete" ) )
        .observeError( Throwable.class, errorLogger( "joinStream#observeError" ) )
            /** An exception causes {@link StagePack#logic} to return null, which doesn't propagate. */
            // TODO: ensure there is at least one persister working.
        .map( listOfLists -> {
          final ImmutableList< COMMAND > commands = listOfLists.get( 0 ) ;
          // logger.debug( "joinStream#map extracted " + commands + "." ) ;
          return listOfLists == null ? null : commands;
        } )
    ;

    final Stream< COMMAND > linearizerStream = joinStream
        .dispatchOn( dispatcher )
        // .observe( c -> logger.debug( "linearizerStream#observe " + c + "." ) )
        .flatMap( Streams::from )
    ;

    httpDownwardStream = Streams.wrap( httpDownwardProcessor ) ;
    reinjecterStream = reinjecterBroadcaster.broadcast() ;

    rerouteAction = SubscriberAwareFilterAction.route(
        httpDownwardProcessor, stagePack::isDownwardCommand,
        passwordHasherProcessor, stagePack::isInternalPasswordStuff,
        emailSenderProcessor, stagePack::isInternalEmailStuff,
        reinjecterBroadcaster, stagePack::isInternalThrottlerDelay,
        sessionSupervisorProcessor, stagePack::isInternalSessionStuff
    ) ;

    passwordHasherStream = Streams.wrap( passwordHasherProcessor ) ;

    emailSenderStream = Streams.wrap( emailSenderProcessor ) ;

    sessionSupervisorStream = Streams.wrap( sessionSupervisorProcessor ) ;

    throttlerStream.subscribe( logicProcessor ) ;
    throttlerStream.subscribe( persisterProcessor ) ;
    linearizerStream.subscribe( rerouteAction ) ;
    rerouteAction.subscribe( passwordHasherProcessor ) ;
    rerouteAction.subscribe( httpDownwardProcessor ) ;
    rerouteAction.subscribe( emailSenderProcessor ) ;
    rerouteAction.subscribe( sessionSupervisorProcessor ) ;
    rerouteAction.subscribe( reinjecterBroadcaster ) ;
  }

  private ImmutableList< COMMAND > failurePassthrough(
      final Stage.Spreader< COMMAND > spreader,
      final Function< COMMAND, Boolean > failureCommandPredicate,
      final COMMAND c
  ) {
    final ImmutableList< COMMAND > result ;
    if( failureCommandPredicate.apply( c ) ) {
      result = ImmutableList.of( c ) ;
      logger.debug( "Detected an error, bypassing normal processing into " + spreader +
          " for " + c + "." ) ;
    } else {
      result = spreader.apply( c ) ;
      // logger.debug( "Ran normal processing for " + c + " into " + spreader +
      //        " giving " + result + "." ) ;
    }
    return result ;
  }

  @Override
  public void start() throws Exception {

    FlowgraphBuilderTools.startAll( stagePack.stratumsHttpOnly ) ;

    FlowgraphBuilderTools.WrapConsumption.forStream(
        httpDownwardStream,
        logger,
        "httpDownwardStream",
        command -> {
          try {
            //logger.debug( "Sending downward to " + stratumPack.httpDownward() + ": " +
            //  command + "." ) ;
            stagePack.httpDownward().accept( command ) ;
          } catch( Exception e ) {
            stagePack.catcher.processThrowable( e );
          }
        },
        terminationSemaphore
    ) ;
    FlowgraphBuilderTools.WrapConsumption.forStream(
        passwordHasherStream,
        logger,
        "passwordHasherStream",
        command -> {
          try {
            final COMMAND result = stagePack.passwordHasher().apply( command ) ;
            if( result != null ) {
              /** When using {@link Hatch} there is no apparent result. */
              injectAtEntry( result ) ;
            }
          } catch( final Exception e ) {
            stagePack.catcher.processThrowable( e ) ;
          }
        },
        terminationSemaphore
    ) ;
    FlowgraphBuilderTools.WrapConsumption.forStream(
        emailSenderStream,
        logger,
        "emailSenderStream",
        command -> {
          try {
            stagePack.emailSender().accept( command ) ;
          } catch( final Exception e ) {
            stagePack.catcher.processThrowable( e ) ;
          }
        },
        terminationSemaphore
    ) ;
    FlowgraphBuilderTools.WrapConsumption.forStream(
        sessionSupervisorStream,
        logger,
        "sessionSupervisorStream",
        command -> {
          try {
            stagePack.sessionSupervisor().accept( command ) ;
          } catch( final Exception e ) {
            stagePack.catcher.processThrowable( e ) ;
          }
        },
        terminationSemaphore
    ) ;
    FlowgraphBuilderTools.WrapConsumption.forStream(
        reinjecterStream,
        logger,
        "reinjecterStream",
        command -> {
          try {
//            synchronized( lock ) {
              // logger.debug( "Reinjecting into " + UpgradedFlowgraph.this + ": " + command + "." ) ;
              injectAtEntry( command ) ;
//            }
          } catch( final Exception e ) {
            if( ! stagePack.isInternalThrottlingDuration( command ) ) {
              logger.error( "Cound not reinject at the top of " + this +
                  " (could a shutdown have occured?):" + command + ".", e ) ;
            }
          }
        },
        terminationSemaphore
    ) ;
  }

  @Override
  public void stop( final long timeout, final TimeUnit timeUnit ) throws Exception {
    logger.debug( "Waiting for reinjected stuff to get into " + this +
        ", so we don't leave pending events ..." ) ;
    reinjectionCounter.waitForZero( timeout, timeUnit ) ; // TODO: respect total timeout.
    insertionBroadcaster.onComplete() ;
    final MultiplexingException.Collector collector = MultiplexingException.newCollector() ;
    final int permits = 4 ; // Matches what we are consuming in #start().

    FlowgraphBuilderTools.shutdown(
        timeout,
        timeUnit,
        collector,
        semaphoreAcquisition( terminationSemaphore, permits ),
        dispatcherShutdown( dispatcher ),
        stratumShutdown( stagePack.stratums )
    ) ;

    collector.throwIfAny( "Could not stop " + this + " propertly" );
  }

  @Override
  public void injectAtEntry( final COMMAND command ) {
//    LOGGER.debug( "Injecting at entry: " + command + " ..." ) ;
    /** This breaks the Reactive contract, so throttling will happen. */
    insertionBroadcaster.onNext( command ) ;
  }

}
