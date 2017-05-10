package io.github.otcdlink.chiron.reactor;

import com.google.common.base.Strings;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;

import java.util.function.BiFunction;

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

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( ReactorTest.class ) ;

  private static final BiFunction< Integer, Integer, Integer > ADD = ( t1, t2 ) -> t1 + t2 ;
  private static final BiFunction< String, Integer, String > REPEAT = Strings::repeat ;

}