package com.otcdlink.chiron.reactor;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static reactor.core.publisher.Flux.just;

public class ReactorSamples {

  @Test
  public void join() throws Exception {
    final Flux< String > left = Flux.just( "A", "B", "C" ) ;
    final Flux< Integer > right = Flux.just( 1, 2, 3 ) ;
    final BiFunction< String, Integer, String > combine = Strings::repeat ;
    final Flux< String > combined = left.join(
        right,
        Ø -> Flux.< String >never(),
        Ø -> Flux.< Integer >never(),
        combine
    ) ;
    combined.subscribe( s -> LOGGER.info( "Joined: " + s ) ) ;
  }

  @Test
  public void generateAndSubscribe() throws Exception {
    Flux
        .generate(
            () -> 0,
            ( state, sink ) -> {
                sink.next( "Counter=" + state ) ;
                if( state >= 9 ) {
                  sink.complete() ;
                }
                return state + 1 ;
            }
        )
        .subscribe( s -> LOGGER.info( "Next: " + s ) )
    ;
  }

  /**
   * Inspired from
   * <a href="https://github.com/reactor/reactor-core/blob/master/src/test/java/reactor/core/publisher/FluxJoinTest.java" >test case</a>,
   * trying to understand the role of the "end" parameter.
   */
  @Test
  public void normal1WithDuration() {
    DirectProcessor< String > source1 = DirectProcessor.create() ;
    DirectProcessor< Integer > source2 = DirectProcessor.create() ;

    DirectProcessor< String > duration1 = DirectProcessor.create() ;

    Flux< String > m = source1.join( source2, Ø -> just( duration1 ), Ø -> Flux.never(), REPEAT ) ;
    m.subscribe( i -> LOGGER.info( "Next: " + i ) ) ;

    duration1.onNext( "X" ) ; // Does nothing.
    source2.onNext( 1 ) ;
    source1.onNext( "A" ) ;
    source1.onNext( "B" ) ;

    source2.onNext( 2 ) ;
    duration1.onNext( "Y" ) ; // Does nothing.
    source2.onNext( 3 ) ;

    source1.onNext( "C" ) ;

    source2.onNext( 2 ) ;
    duration1.onNext( "Z" ) ; // Does nothing.

    source1.onComplete() ;
    source2.onComplete() ;

  }

  @Test
  public void windowAndMergeSequential() throws Exception {
    DirectProcessor< String > source = DirectProcessor.create() ;
    DirectProcessor< PersistenceDone > delimiter = DirectProcessor.create() ;
    final Flux< Flux< String > > burstSource = source.window( delimiter ) ;
    burstSource.subscribe( flux -> {
      LOGGER.info( "Starting new flux " + flux + " ..." ) ;
      flux.subscribe( s -> LOGGER.info( "From burstSource: " + s ) ) ;
    } ) ;
    final Flux< String > merged = Flux.mergeSequential( burstSource ) ;
    merged.subscribe( s -> LOGGER.info( "From merged: " + s ) ) ;
    source.onNext( "A" ) ;
    source.onNext( "B" ) ;
    delimiter.onNext( PersistenceDone.INSTANCE ) ;
    source.onNext( "C" ) ;
    source.onComplete() ;
  }

  /**
   * Some kind of fork-join with simulated Logic generating a {@code Flux< Character >} for
   * each {@code String} in input. The {@code Flux< Character >}s flow down only after
   * simulated Persistence completed. They are linearized with {@link Flux#flatMap(Function)}.
   * This is the most promising approac because it keeps the "bursts" delimited, so we could
   * defer flushing.
   */
  @Test
  public void zipAndFlatMap() throws Exception {
    final DirectProcessor< String > inserter = DirectProcessor.create() ;

    final Flux< PersistenceDone > persister = inserter
        .map( any -> PersistenceDone.INSTANCE )
        //.doOnNext( any -> LOGGER.info( "persister: " + any ) )
    ;

    final Flux< Flux< Character > > logic = inserter
//        .map( STRING_TO_CHARACTER_FLUX )
        .map( stringToCharacterFlux( '>' ) )
        //.doOnNext( c -> LOGGER.info( "logic: " + c ) )
    ;

    final Flux< Flux< Character > > join = Flux
        .zip( logic, persister, ( flux, nil ) -> flux )
        .doOnNext( characterFlux -> LOGGER.info( "join: " + characterFlux ) )
    ;

    final Flux< Character > linear = join
        .publishOn( scheduler( "linear" ) )
        .flatMap( characterFlux -> characterFlux )
    ;

    linear
        .doOnNext( c -> LOGGER.info( "linear: " + c ) )
        .subscribe()
    ;

    inserter.onNext( "Aa" ) ;
    inserter.onNext( "B" ) ;
    inserter.onNext( "Cc[" ) ;
    inserter.onComplete() ;
  }

  /**
   * Some kind of fork-join. Simulated Logic creates {@code List< Character >}. See
   * <a href="https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#zip-org.reactivestreams.Publisher-org.reactivestreams.Publisher-java.util.function.BiFunction-" >javadoc</a>.
   */
  @Test
  public void zip() throws Exception {
    final DirectProcessor< String > inserter = DirectProcessor.create() ;

    final Flux< List< Character > > logic = inserter
        .map( SPLIT )
        .doOnNext( list -> LOGGER.info( "logic: mapped " + list + "." ) )
    ;

    final Flux< PersistenceDone > persister = inserter
        .map( any -> PersistenceDone.INSTANCE )
        .doOnNext( any -> LOGGER.info( "persister: " + any ) )
    ;

    final Flux< List< Character > > join = Flux.zip( logic, persister, ( list, any ) -> list ) ;

    join.subscribe( list -> LOGGER.info( "join: " + list + "." ) ) ;

    inserter.onNext( "A" ) ;
    inserter.onNext( "Bb" ) ;
    inserter.onNext( "Cc<" ) ;
    inserter.onComplete() ;
  }

  /**
   * Creates some kind of cartesian product.
   * See
   * <a href="https://github.com/reactor/reactor-core/blob/master/reactor-core/src/test/java/reactor/core/publisher/FluxJoinTest.java">test sources</a>,
   * <a href="https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#join-org.reactivestreams.Publisher-java.util.function.Function-java.util.function.Function-java.util.function.BiFunction-">Javadoc</a>.
   */
  @Test
  public void forkJoin() throws Exception {
    DirectProcessor< String > inserter = DirectProcessor.create() ;

    final Flux< List< Character > > logic = inserter
        .map( SPLIT )
        .doOnNext( list -> LOGGER.info( "logic: mapped " + list + "." ) )
    ;

    final Flux< PersistenceDone > persister = inserter
        .map( any -> PersistenceDone.INSTANCE )
        .doOnNext( any -> LOGGER.info( "persister: " + any ) )
    ;

    final BiFunction< List< Character >, PersistenceDone, List< Character > > retainList =
        ( list, o ) -> list ;

    final Flux< List< Character > > joiner = logic
        .join(
            persister,
            o -> Flux.never(),
            o -> Flux.never(),
            retainList
        )
        .doOnNext( list -> LOGGER.info( "joiner: " + list + "." ) )
    ;

    joiner.subscribe( list -> LOGGER.info( "Joined " + list + "." ) ) ;

    inserter.onNext( "Hello" ) ;
    inserter.onNext( "World" ) ;
    inserter.onComplete() ;

  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( ReactorSamples.class ) ;

  private static final BiFunction< Integer, Integer, Integer > ADD = ( t1, t2 ) -> t1 + t2 ;
  private static final BiFunction< String, Integer, String > REPEAT = Strings::repeat ;

  private static final Function< String, List< Character > > SPLIT = s -> {
    final ImmutableList.Builder< Character > builder = ImmutableList.builder() ;
    for( final Character c : s.toCharArray() ) {
      builder.add( c ) ;
    }
    return ( List< Character > ) builder.build() ;
  } ;

  private static final BiConsumer< String, SynchronousSink< Character > >
      STRING_TO_CHARACTER_TO_SYNCHRONOUSSINK = ( string, sink ) -> {
        for( int i = 0 ; i < string.length() ; i++ ) {
          sink.next( string.charAt( i ) ) ;
        }
//        sink.complete() ;
      }
  ;

  private static final Function< String, Flux< Character > > STRING_TO_CHARACTER_FLUX =
      string -> {
        final Character[] characters = new Character[ string.length() ] ;
        for( int i = 0 ; i < string.length() ; i++ ) {
          characters[ i ] = string.charAt( i ) ;
        }
        return Flux.just( characters ) ;
      }
  ;

  private static Function< String, Flux< Character > > stringToCharacterFlux( final char heading ) {
    return string -> {
      final Character[] characters = new Character[ string.length() + 1 ] ;
      characters[ 0 ] = heading ;
      for( int i = 0 ; i < string.length() ; i++ ) {
        characters[ i + 1 ] = string.charAt( i ) ;
      }
      return Flux.just( characters ) ;
    } ;
  }


  private static final class PersistenceDone {
    public PersistenceDone() { }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{}" ;
    }

    public static final PersistenceDone INSTANCE = new PersistenceDone() ;
  }

  private static Scheduler scheduler( final String threadpoolRadix ) {
    return Schedulers.fromExecutor(
        ExecutorTools.singleThreadedExecutorServiceFactory( threadpoolRadix ).create() ) ;
  }




}