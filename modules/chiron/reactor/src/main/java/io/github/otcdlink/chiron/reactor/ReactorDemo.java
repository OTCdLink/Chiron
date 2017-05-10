package io.github.otcdlink.chiron.reactor;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.util.function.BiFunction;

/**
 *
 */
public class ReactorDemo {

  private static final Logger LOGGER = LoggerFactory.getLogger( ReactorDemo.class ) ;

  public static void main( String[] args ) {
    generateAndSubscribe() ;
    join() ;

  }

  private static void generateAndSubscribe() {
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

  private static void join() {
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

  private static final Scheduler scheduler( final String name ) {
    throw new UnsupportedOperationException( "TODO" ) ;
//    return Schedulers.fromExecutor()
  }

}
