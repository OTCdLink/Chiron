package com.otcdlink.chiron.middle.throttler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.clock.Clock;
import mockit.Expectations;
import mockit.Injectable;
import org.joda.time.Duration;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class NotifyingThrottlerTest {

  /**
   * Unconditionally throttling a {@code Number} for a duration of 0, so we see
   * {@link NotifyingThrottler.Watcher#restrictionRemoved()} happen immediately.
   */
  @Test
  public void simpleRemoval(
      @Injectable final NotifyingThrottler.Watcher< ThrottlerFixture.NumberRestriction > watcher
  ) throws Exception {
    new Expectations() { {
      watcher.blockedBy( new ThrottlerFixture.IntegerPartRestriction( 1 ) ) ;
      /** There may be 0 invocation if first {@link SessionScopedThrottler#cleanup()} caused by
       * {@link SessionScopedThrottler#evaluateAndUpdate(Object)} happens late enough.*/
      minTimes = 0 ;
      result = true ;
      watcher.restrictionRemoved() ;
    } } ;

    addWatcher( watcher ) ;
    applyCommand( 1, SessionScopedThrottler.Throttling.PASSED ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( NotifyingThrottlerTest.class ) ;

  private final Clock clock = Clock.SYSTEM_CLOCK ;

  private final NotifyingThrottler< Number, ThrottlerFixture.NumberRestriction< Number > >
  throttler = new NotifyingThrottler<>(
        clock,
        new ThrottlerFixture.NumberRestrictionFactory(),
        Executors.newSingleThreadScheduledExecutor( new ThreadFactoryBuilder()
            .setNameFormat(
                ToStringTools.getNiceName( NotifyingThrottler.Watcher.class ) + "-notifier" )
            .build()
        ),
        Duration.ZERO,
        0
    )
  ;

  private final Semaphore callHappenedSemaphore = new Semaphore( 0 ) ;

  private void addWatcher(
      final NotifyingThrottler.Watcher< ThrottlerFixture.NumberRestriction > watcher
  ) throws InterruptedException {
    throttler.addWatcher(
        new NotifyingThrottler.Watcher< ThrottlerFixture.NumberRestriction< Number > >() {
          @Override
          public boolean blockedBy(
              final ThrottlerFixture.NumberRestriction< Number > restriction
          ) {
            final boolean blocked = watcher.blockedBy( restriction ) ;
            LOGGER.info( "Evaluating " + this + "#blocked(" + restriction + "), " +
                "delegating to mock, returning " + blocked ) ;
            return blocked ;
          }

          @Override
          public void restrictionRemoved() {
            LOGGER.info( "Evaluating " + this + "#restrictionRemoved()" ) ;
            watcher.restrictionRemoved() ;
            callHappenedSemaphore.release() ;
          }

          @Override
          public String toString() {
            return ToStringTools.nameAndCompactHash( this ) + "{}";
          }
        }
    ) ;
  }

  private void applyCommand(
      final Number command,
      final SessionScopedThrottler.Throttling expectedThrottling
  ) throws InterruptedException {
    final SessionScopedThrottler.Throttling throttling = throttler.evaluateAndUpdate( command ) ;
    assertThat( throttling ).isEqualTo( expectedThrottling ) ;
    callHappenedSemaphore.acquire( 1 ) ;
  }



}