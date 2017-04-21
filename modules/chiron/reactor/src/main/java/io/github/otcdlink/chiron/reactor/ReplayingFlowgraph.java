package io.github.otcdlink.chiron.reactor;

import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.toolbox.MultiplexingException;
import org.reactivestreams.Processor;
import org.slf4j.Logger;
import reactor.fn.Consumer;
import reactor.fn.tuple.Tuple2;
import reactor.fn.tuple.Tuple4;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.broadcast.Broadcaster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.github.otcdlink.chiron.reactor.FlowgraphBuilderTools.createProcessor;
import static io.github.otcdlink.chiron.reactor.FlowgraphBuilderTools.dispatcherShutdown;
import static io.github.otcdlink.chiron.reactor.FlowgraphBuilderTools.semaphoreAcquisition;
import static io.github.otcdlink.chiron.reactor.FlowgraphBuilderTools.stratumShutdown;

public class ReplayingFlowgraph< COMMAND > extends AbstractCanonicalFlowgraph< COMMAND > {

  private final Broadcaster< COMMAND > insertionBroadcaster;
  private final Stream< COMMAND > passwordHasherStream ;
  private final Stream< COMMAND > insertionStream ;
  final Stream< ImmutableList< COMMAND > > logicStream ;
  final Stream< ImmutableList< COMMAND > > persisterStream ;

  public ReplayingFlowgraph(
      final Logger logger,
      final StagePack< COMMAND > stagePack,
      final int backlogSize
  ) {
    super( logger, stagePack, backlogSize ) ;

    insertionBroadcaster = Broadcaster.create( dispatcher ) ;
    insertionStream = insertionBroadcaster.broadcast() ;

    final Processor< COMMAND, COMMAND > logicProcessor = createProcessor( "logic", backlogSize ) ;
    final Processor< COMMAND, COMMAND >  persisterProcessor =
        createProcessor( "persister", backlogSize ) ;
    final Processor< COMMAND, COMMAND >  passwordHasherProcessor =
        createProcessor( "passwordHasher", backlogSize ) ;


    logicStream = Streams
        .wrap( logicProcessor )
        // .observe( c -> logger.debug( "logicStream#observe " + c ) )
        .observeComplete(
            Ø -> logger.debug( "logicStream#observeComplete" ) )
        .map( c -> {
          // logger.debug( "logicStream#map " + c ) ;
          return stagePack.logic().apply( c ) ;
        } )
        .broadcast()
    ;

    persisterStream = Streams
        .wrap( persisterProcessor )
        // .observe( c -> PIPELINE_LOGGER.debug( "persisterStream#observe " + c ) )
        .observeComplete(
            Ø -> logger.debug( "persisterStream#observeComplete." ) )
        .observeError( Throwable.class, errorLogger( "persisterStream#observeError" ) )
        .map( stagePack.persister() )
    ;

    passwordHasherStream = Streams.wrap( passwordHasherProcessor ) ;

    insertionStream.subscribe( logicProcessor ) ;
    insertionStream.subscribe( persisterProcessor ) ;
    logicStream
        .filter( l -> l.size() == 1 )
        .filter( l -> stagePack.isInternalPasswordStuff( l.get( 0 ) ) )
        .map( l -> l.get( 0 ) )
        .subscribe( passwordHasherProcessor )
    ;
  }



  @Override
  public void start() throws Exception {
    /*** Start'em all because {@link UpgradedFlowgraph#stop(long, TimeUnit)} stops them all. */
    FlowgraphBuilderTools.startAll( stagePack.stratumsNoHttp ) ;

    FlowgraphBuilderTools.WrapConsumption.forStream(
        logicStream,
        logger,
        "logicStream",
        command -> {
        },
        terminationSemaphore
    ) ;
    FlowgraphBuilderTools.WrapConsumption.forStream(
        persisterStream,
        logger,
        "persisterStream",
        command -> {
        },
        terminationSemaphore
    ) ;
    FlowgraphBuilderTools.WrapConsumption.forStream(
        passwordHasherStream,
        logger,
        "passwordHasherStream",
        command -> {
          try {
            final COMMAND result = stagePack.passwordHasher().apply( command );
            injectAtEntry( result );
          } catch( final Exception e ) {
            stagePack.catcher.processThrowable( e );
          }
        },
        terminationSemaphore
    ) ;
  }

  @Override
  public void stop( final long timeout, final TimeUnit timeUnit ) throws Exception {
    insertionBroadcaster.onComplete() ;
    final MultiplexingException.Collector collector = MultiplexingException.newCollector() ;
    final int permits = 3 ;

    final List< Tuple2<
        Consumer< Tuple4< Object, MultiplexingException.Collector, Long, TimeUnit > >,
        ImmutableList< Object >
    > > shuttableTuples = new ArrayList<>( 3 ) ;
    shuttableTuples.add( semaphoreAcquisition( terminationSemaphore, permits ) ) ;
    shuttableTuples.add( dispatcherShutdown( dispatcher ) ) ;
    if( stopOperatesOnAllStratums ) {
      shuttableTuples.add( stratumShutdown( stagePack.stratumsNoHttp ) ) ;
    }

    FlowgraphBuilderTools.shutdown(
        timeout,
        timeUnit,
        collector,
        ImmutableList.copyOf( shuttableTuples )
    ) ;
  }

  @Override
  public void injectAtEntry( final COMMAND command ) {
    /** This breaks the Reactive contract, so throttling will happen. */
    insertionBroadcaster.onNext( command ) ;
  }


  private boolean stopOperatesOnAllStratums = false ;
  public void stopOperatesOnAllStratums( final boolean all ) {
    stopOperatesOnAllStratums = all ;
  }
}
