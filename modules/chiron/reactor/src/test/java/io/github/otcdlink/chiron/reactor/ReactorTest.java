package io.github.otcdlink.chiron.reactor;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.collection.ImmutableKeyHolderMap;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static reactor.core.publisher.Flux.just;

public class ReactorTest {
  
  @Test
  public void justBeHere() throws Exception {
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
  public void window1() throws Exception {
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
   * Some kind of fork-join. See
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

  private static final Logger LOGGER = LoggerFactory.getLogger( ReactorTest.class ) ;

  private static final BiFunction< Integer, Integer, Integer > ADD = ( t1, t2 ) -> t1 + t2 ;
  private static final BiFunction< String, Integer, String > REPEAT = Strings::repeat ;

  private static final Function< String, List< Character > > SPLIT = s -> {
    final ImmutableList.Builder< Character > builder = ImmutableList.builder() ;
    for( final Character c : s.toCharArray() ) {
      builder.add( c ) ;
    }
    return ( List< Character > ) builder.build() ;
  } ;

  private static final class PersistenceDone {
    public PersistenceDone() { }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{}" ;
    }

    public static final PersistenceDone INSTANCE = new PersistenceDone() ;
  }



}