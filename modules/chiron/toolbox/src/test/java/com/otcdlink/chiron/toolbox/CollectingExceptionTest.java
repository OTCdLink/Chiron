package com.otcdlink.chiron.toolbox;


import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class CollectingExceptionTest {

  @Test
  public void collectNothing() throws Exception {
    final CollectingException.Collector collector = CollectingException.newCollector() ;
    collector.throwIfAny( "Should not throw anything" ) ;
  }

  @Test
  public void throwMyExceptions() {
    final CollectingException.Collector collector
        = new CollectingException.Collector( MyCollectingException::new ) ;
    collector.collect( new IOException( "io" ) ) ;
    collector.collect( new RuntimeException( "runtime" ) ) ;
    try {
      collector.throwIfAny( "io+runtime" ) ;
      fail( "Should have thrown " + MyCollectingException.class.getSimpleName() ) ;
    } catch( final CollectingException exceptions ) {
      assertThat( exceptions.exceptions ).hasSize( 2 ) ;
      assertThat( exceptions.getMessage() ).isEqualTo(
            "io+runtime (2 exceptions)\n"
          + "    java.io.IOException - io\n"
          + "    java.lang.RuntimeException - runtime"
      ) ;
    }
  }

// =======
// Fixture
// =======

  public static class MyCollectingException extends CollectingException {
    public MyCollectingException(
        final String message,
        final ImmutableList<Throwable> exceptions
    ) {
      super( message, exceptions ) ;
    }
  }



}