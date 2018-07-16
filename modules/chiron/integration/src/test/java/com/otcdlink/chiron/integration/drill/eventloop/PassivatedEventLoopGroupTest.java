package com.otcdlink.chiron.integration.drill.eventloop;

import com.otcdlink.chiron.downend.Downend;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PassivatedEventLoopGroupTest {

  @Test
  public void captureScheduledTask() throws InterruptedException {
    final BlockingQueue< PassivatedEventLoopGroup.TaskCapture > queue =
        new LinkedBlockingQueue<>() ;
    final NioEventLoopGroup executor = new PassivatedEventLoopGroup(
        ExecutorTools.newThreadFactory( "executor" ), queue::add ) ;
    final Semaphore doneSemaphore = new Semaphore( 0 ) ;
    executor.submit( () -> LOGGER.info( "Plain submission works!" ) ) ;
    final ScheduledFuture< ? > scheduledFuture = executor.schedule(
        ( Downend.ScheduledInternally.Ping ) doneSemaphore::release, 1, TimeUnit.SECONDS ) ;
    LOGGER.info( "Scheduled " + scheduledFuture + "." ) ;

    final PassivatedEventLoopGroup.TaskCapture taskCapture = queue.take() ;
    assertThat( taskCapture ).isNotNull() ;
    assertThat( taskCapture.initialDelay ).isEqualTo( 1 ) ;
    assertThat( taskCapture.delay ).isEqualTo( 0 ) ;
    assertThat( taskCapture.timeUnit ).isEqualTo( TimeUnit.SECONDS ) ;
    assertThat( doneSemaphore.availablePermits() ).isEqualTo( 0 ) ;
    taskCapture.submitNow().sync() ;
    assertThat( doneSemaphore.tryAcquire( 2, TimeUnit.SECONDS ) ).isTrue() ;

  }


// =======
// Fixture
// =======

  private static final Logger LOGGER =
    LoggerFactory.getLogger( PassivatedEventLoopGroupTest.class ) ;

}
