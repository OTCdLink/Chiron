package com.otcdlink.chiron.integration.drill.eventloop;

import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore
public class ScheduledFutureTaskHackTest {

  @Test
  public void fastForward() throws InterruptedException {

    final AtomicLong nanotimeHolder = new AtomicLong( 0 ) ;
    ScheduledFutureTaskHack.install( nanotimeHolder::get ) ;
    final long startTime = hackedNanoTime() ;

    final NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup() ;
    final Semaphore scheduledTaskCompleted = new Semaphore( 0 ) ;
    nioEventLoopGroup.schedule(
        () -> {
          scheduledTaskCompleted.release() ;
          LOGGER.info( "Scheduled task completed." ) ;
        },
        1,
        TimeUnit.HOURS
    ) ;
    LOGGER.info( "Scheduled task for in 1 hour, now fast-forwarding Netty's clock ..." ) ;

    // Test fails when disabling fast-forward below.
    nanotimeHolder.set( startTime + TimeUnit.HOURS.toNanos( 1 ) + 1 ) ;
    Thread.sleep( 1000 ) ;
    hackedNanoTime() ;
    // Amazingly Netty detected clock change and ran the task!
    assertThat( scheduledTaskCompleted.tryAcquire( 1, TimeUnit.SECONDS ) )
        .describedAs( "Scheduled task should have completed within 1 second" )
        .isTrue()
    ;

  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger(
      ScheduledFutureTaskHackTest.class ) ;

  static {
    NettyTools.forceNettyClassesToLoad() ;
  }

  private static long hackedNanoTime() {
    final long nanoTime = ScheduledFutureTaskHack.invokeNanoTime() ;
    LOGGER.info(
        ToStringTools.getNiceName( ScheduledFutureTaskHack.StaticMethodDelegate.class ) +
        "#nanoTime(): " + nanoTime + "."
    ) ;
    return nanoTime ;
  }

}
