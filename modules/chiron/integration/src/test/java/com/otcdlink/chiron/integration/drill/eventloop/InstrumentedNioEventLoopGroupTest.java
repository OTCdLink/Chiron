package com.otcdlink.chiron.integration.drill.eventloop;

import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore
public class InstrumentedNioEventLoopGroupTest {

  @Test
  public void recordAndAdjust() throws InterruptedException {

    final int delay = 10 ;
    final TimeUnit timeUnit = TimeUnit.SECONDS ;

    final AtomicLong nanoInstantSupplier = new AtomicLong() ;
    ScheduledFutureTaskHack.install( nanoInstantSupplier::get ) ;

    final List< Long > taskDeadlineRecorder = Collections.synchronizedList( new ArrayList<>() ) ;
    final InstrumentedNioEventLoopGroup executor = new InstrumentedNioEventLoopGroup(
        ExecutorTools.newThreadFactory( "executor" ), taskDeadlineRecorder::add ) ;
    executor.setIoRatio( 1 ) ;  // Silly but worth trying to see what can get wrong.

    final Semaphore doneSemaphore = new Semaphore( 0 ) ;
    final ScheduledFuture< ? > scheduledFuture1 =
        executor.schedule( ( Runnable ) doneSemaphore::release, delay, timeUnit ) ;
    LOGGER.info( "Scheduled " + scheduledFuture1 + "." ) ;

    assertThat( taskDeadlineRecorder ).hasSize( 1 ) ;
    final Long nanoTime = taskDeadlineRecorder.get( 0 ) - ScheduledFutureTaskHack.START_TIME ;
    LOGGER.info( "Recorded " + nanoTime + " as nanoTime deadline for next task." ) ;

    assertThat( nanoTime ).isEqualTo( timeUnit.toNanos( delay ) ) ;
    final long pastDeadline = nanoTime + 1 ;
    nanoInstantSupplier.set( pastDeadline ) ;
    LOGGER.info(
        "Did set nanoTime to " + pastDeadline + ", past to Task's deadline. " +
        "Invocation of hacked nanoTime() returns " +
        ScheduledFutureTaskHack.invokeNanoTime() + "."
    ) ;
    LOGGER.info( "Now waiting for task completion ..." ) ;
    assertThat( doneSemaphore.tryAcquire( 3, TimeUnit.SECONDS ) ).isTrue() ;
  }

  /**
   * Fails when ran after {@link #recordAndAdjust()} because JUnit doesn't reload classes for
   * each method inside a test class.
   */
  @Test
  public void noInstrumentation() throws InterruptedException {
    final NioEventLoopGroup executor =
        new NioEventLoopGroup( 1, ExecutorTools.newThreadFactory( "executor" ) ) ;
    final Semaphore doneSemaphore = new Semaphore( 0 ) ;
    executor.submit( () -> LOGGER.info( "Plain submission works!" ) ) ;
    final ScheduledFuture< ? > scheduledFuture =
        executor.schedule( ( Runnable ) doneSemaphore::release, 1, TimeUnit.SECONDS ) ;
    LOGGER.info( "Scheduled " + scheduledFuture + "." ) ;
    assertThat( doneSemaphore.tryAcquire( 3, TimeUnit.SECONDS ) ).isTrue() ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( InstrumentedNioEventLoopGroupTest.class ) ;

  static {
    NettyTools.forceNettyClassesToLoad() ;
  }

}