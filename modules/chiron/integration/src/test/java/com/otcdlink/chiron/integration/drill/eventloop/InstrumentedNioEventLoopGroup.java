package com.otcdlink.chiron.integration.drill.eventloop;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

class InstrumentedNioEventLoopGroup extends NioEventLoopGroup {

  /**
   * Consume the value obtained from
   * {@link io.netty.util.concurrent.ScheduledFutureTask#deadlineNanos()}.
   * This is hardly mappable to an exact {@link Instant} (even if the Java flavor retains
   * nanoseconds) but this is enough to compare with {@link System#nanoTime()}.
   */
  private final Consumer< Long > scheduledTaskMomentConsumer ;

  public InstrumentedNioEventLoopGroup(
      final ThreadFactory threadFactory,
      final Consumer< Long > scheduledTaskMomentConsumer
  ) {
    // Need 2 threads because one will block on Socket Selector if there is no IO,
    // so we add one to poll Tasks.
    super( 2, threadFactory ) ;
    this.scheduledTaskMomentConsumer = checkNotNull( scheduledTaskMomentConsumer ) ;
  }

  private < FUTURE extends Future > FUTURE recordDeadlineNanos( final FUTURE future ) {
    final Long deadlineNanos = ScheduledFutureTaskHack.invokeDeadlineNanos( future ) ;
    if( deadlineNanos != null ) {
      scheduledTaskMomentConsumer.accept( deadlineNanos ) ;
    }
    return future ;
  }


  @Nonnull
  @Override
  public Future< ? > submit( final Runnable task ) {
    return recordDeadlineNanos( super.submit( task ) ) ;
  }

  @Nonnull
  @Override
  public < T > Future< T > submit(
      final Runnable task,
      final T result
  ) {
    return recordDeadlineNanos( super.submit( task, result ) ) ;
  }

  @Nonnull
  @Override
  public < T > Future< T > submit( final Callable< T > task ) {
    return recordDeadlineNanos( super.submit( task ) ) ;
  }

  @Nonnull
  @Override
  public ScheduledFuture< ? > schedule(
      final Runnable command,
      final long delay,
      final TimeUnit unit
  ) {
    return recordDeadlineNanos( super.schedule( command, delay, unit ) ) ;
  }

  @Nonnull
  @Override
  public < V > ScheduledFuture< V > schedule(
      final Callable< V > callable,
      final long delay,
      final TimeUnit unit
  ) {
    return recordDeadlineNanos( super.schedule( callable, delay, unit ) ) ;
  }

  @Nonnull
  @Override
  public ScheduledFuture< ? > scheduleAtFixedRate(
      final Runnable command,
      final long initialDelay,
      final long period,
      final TimeUnit unit
  ) {
    return recordDeadlineNanos(
        super.scheduleAtFixedRate( command, initialDelay, period, unit ) ) ;
  }

  @Nonnull
  @Override
  public ScheduledFuture< ? > scheduleWithFixedDelay(
      final Runnable command,
      final long initialDelay,
      final long delay,
      final TimeUnit unit
  ) {
    return recordDeadlineNanos(
        super.scheduleWithFixedDelay( command, initialDelay, delay, unit ) ) ;
  }
}
