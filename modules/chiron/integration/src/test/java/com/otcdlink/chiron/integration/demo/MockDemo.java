package com.otcdlink.chiron.integration.demo;

import com.google.common.util.concurrent.Uninterruptibles;
import com.otcdlink.chiron.mockster.Mockster;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Verifications;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

import static com.otcdlink.chiron.mockster.Mockster.exactly;
import static com.otcdlink.chiron.mockster.Mockster.withCapture;
import static org.assertj.core.api.Assertions.assertThat;

@Ignore
public class MockDemo {

  @Test
  public void record( @Injectable final Engine engine ) {
    new Expectations() {{
      engine.addSync( 3, 2 ) ;
      result = 5 ;
    }} ;

    // System under test + assertion.
    assertThat( engine.addSync( 3, 2 ) ).isEqualTo( 5 ) ;
  }

  @Test
  public void verify( @Injectable final Engine engine ) {
    engine.addSync( 3, 2 ) ;

    new Verifications() {{
      engine.addSync( 3, 2 ) ;
    }} ;
  }

  @Test
  public void capture( @Injectable final Engine engine ) throws InterruptedException {
    final LongConsumer resultConsumer = l -> { } ;

    // What System Under Test could do.
    final Thread computationRunner = new Thread(
        () -> {
          LOGGER.info( "Sleeping a bit ..." ) ;
          Uninterruptibles.sleepUninterruptibly( 1, TimeUnit.SECONDS ) ;
          LOGGER.info( "Now we perform addition ..." ) ;
          engine.addAsync( 12, 2, resultConsumer ) ;
          LOGGER.info( "Addition complete." ) ;
        },
        "computation-runner"
    ) ;
    computationRunner.start() ;

    new Verifications() {{
      final LongConsumer captured ;
      LOGGER.info( "Verifying ..." ) ;
      engine.addAsync( 12, 2, captured = withCapture() ) ;
      LOGGER.info( "Verification complete." ) ;
      assertThat( captured ).isSameAs( resultConsumer ) ;
    }} ;

    computationRunner.join() ;
  }

  @Test
  public void capture() throws InterruptedException {
    try( final Mockster mockster = new Mockster() ) {
      final Engine engine = mockster.mock( Engine.class ) ;
      final LongConsumer resultConsumer = l -> { } ;

      // What System Under Test could do.
      final Thread computationRunner = new Thread(
          () -> {
            LOGGER.info( "Sleeping a bit ..." ) ;
            Uninterruptibles.sleepUninterruptibly( 1, TimeUnit.SECONDS ) ;
            LOGGER.info( "Now we perform addition ..." ) ;
            engine.addAsync( 12, 2, resultConsumer ) ;
            LOGGER.info( "Addition complete." ) ;
          },
          "computation-runner"
      ) ;
      computationRunner.start() ;

      final LongConsumer captured ;
      LOGGER.info( "Verifying ..." ) ;
      engine.addAsync( exactly( 12 ), exactly( 2 ), captured = withCapture() ) ;
      LOGGER.info( "Verification complete." ) ;
      assertThat( captured ).isSameAs( resultConsumer ) ;

      computationRunner.join() ;
    }
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( MockDemo.class ) ;

  public interface Engine {
    long addSync( final int term1, final int term2 ) ;

    void addAsync(
        final int term1,
        final int term2,
        final LongConsumer resultConsumer
    ) ;
  }

  private static class Fibonacci {
    private final Engine engine ;

    public Fibonacci( final Engine engine ) {
      this.engine = engine ;
    }

    public long valueAt( final int value ) {
      throw new UnsupportedOperationException( "TODO" ) ;
    }
  }

}
