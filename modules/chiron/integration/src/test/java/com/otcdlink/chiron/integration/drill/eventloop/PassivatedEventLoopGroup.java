package com.otcdlink.chiron.integration.drill.eventloop;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.downend.Downend;
import com.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class PassivatedEventLoopGroup extends NioEventLoopGroup {

  public final class TaskCapture {
    private final StackTraceElement[] creationPoint ;
    public final long initialDelay ;
    public final long delay ;
    public final boolean fixedDelay ;
    public final TimeUnit timeUnit ;
    public final Runnable runnable ;
    public final Callable callable ;
    private final ScheduledFuture future ;

    public TaskCapture(
        final long initialDelay,
        final long delay,
        final TimeUnit timeUnit,
        final Callable callable
    ) {
      this( initialDelay, delay, timeUnit, false, null, callable ) ;
    }

    public TaskCapture(
        final long initialDelay,
        final long delay,
        final TimeUnit timeUnit,
        final Runnable runnable
    ) {
      this( initialDelay, delay, timeUnit, false, runnable, null ) ;
    }

    public TaskCapture(
        final long initialDelay,
        final long delay,
        final TimeUnit unit,
        final boolean fixedDelay,
        final Runnable runnable
    ) {
      this( initialDelay, delay, unit, fixedDelay, runnable, null ) ;
    }

    private TaskCapture(
        final long initialDelay,
        final long delay,
        final TimeUnit timeUnit,
        final boolean fixedDelay,
        final Runnable runnable,
        final Callable callable
    ) {
      this.creationPoint = new Exception().getStackTrace() ;
      this.timeUnit = checkNotNull( timeUnit ) ;
      this.initialDelay = initialDelay ;
      this.delay = delay ;
      this.fixedDelay = fixedDelay ;
      if( runnable == null ) {
        checkNotNull( callable ) ;
      } else {
        checkArgument( runnable instanceof Downend.ScheduledInternally ) ;
        checkArgument( callable == null ) ;
      }
      this.runnable = runnable ;
      this.callable = callable ;
      future = new PassivatedScheduledFuture( this.toString() ) ;
    }

    public < T > ScheduledFuture< T > scheduledFuture() {
      return ( ScheduledFuture<T> ) future ;
    }

    public Future submitNow() {
      if( callable != null ) {
        return PassivatedEventLoopGroup.this.submit( callable ) ;
      } else {
        return PassivatedEventLoopGroup.this.submit( runnable ) ;
      }
    }

    @Override
    public String toString() {
      final StringBuilder stringBuilder = new StringBuilder( getClass().getSimpleName() ) ;
      stringBuilder.append( ToStringTools.compactHashForNonNull( this ) ) ;
      stringBuilder.append( '{' ) ;
      if( initialDelay > 0 ) {
        stringBuilder.append( "initialDelay=" ) ;
        stringBuilder.append( initialDelay ) ;
        stringBuilder.append( ';' ) ;
      }
      if( delay > 0 ) {
        stringBuilder.append( "delay=" ) ;
        stringBuilder.append( delay ) ;
        stringBuilder.append( ';' ) ;
      }
      if( runnable instanceof Downend.ScheduledInternally ) {
        final Class runnableClass = runnable.getClass() ;
        boolean first = true ;
        for( final Class someInterface : runnableClass.getInterfaces() ) {
          if( Downend.ScheduledInternally.class.isAssignableFrom( someInterface ) ) {
            stringBuilder.append( someInterface.getSimpleName() ) ;
            if( first ) {
              first = false ;
            } else {
              stringBuilder.append( '|' ) ;
            }
          }
        }
      } else {
        final Object actualTask = runnable == null ? callable : runnable ;
        stringBuilder.append( actualTask.toString() ) ;
      }
      stringBuilder.append( '}' ) ;
      return stringBuilder.toString() ;
    }

    public ImmutableList< StackTraceElement > creationPoint() {
      return ImmutableList.copyOf( creationPoint ) ;
    }
  }

  /**
   * Consume the value obtained from
   * {@link io.netty.util.concurrent.ScheduledFutureTask#deadlineNanos()}.
   * This is hardly mappable to an exact {@link Instant} (even if the Java flavor retains
   * nanoseconds) but this is enough to compare with {@link System#nanoTime()}.
   */
  private final Consumer< TaskCapture  > taskCaptureConsumer ;

  public PassivatedEventLoopGroup(
      final ThreadFactory threadFactory,
      final Consumer< TaskCapture  > taskCaptureConsumer
  ) {
    super( 1, threadFactory ) ;
    this.taskCaptureConsumer = checkNotNull( taskCaptureConsumer ) ;
  }

  @Nonnull
  @Override
  public ScheduledFuture< ? > schedule(
      final Runnable command,
      final long delay,
      final TimeUnit unit
  ) {
    final TaskCapture taskCapture = new TaskCapture( delay, 0, unit, command ) ;
    taskCaptureConsumer.accept( taskCapture ) ;
    return taskCapture.scheduledFuture() ;
  }

  @Nonnull
  @Override
  public < V > ScheduledFuture< V > schedule(
      final Callable< V > callable,
      final long delay,
      final TimeUnit unit
  ) {
    final TaskCapture taskCapture = new TaskCapture( delay, 0, unit, callable ) ;
    taskCaptureConsumer.accept( taskCapture ) ;
    return taskCapture.scheduledFuture() ;
  }

  @Nonnull
  @Override
  public ScheduledFuture< ? > scheduleAtFixedRate(
      final Runnable command,
      final long initialDelay,
      final long period,
      final TimeUnit unit
  ) {
    final TaskCapture taskCapture = new TaskCapture( initialDelay, period, unit, false, command ) ;
    taskCaptureConsumer.accept( taskCapture ) ;
    return taskCapture.scheduledFuture() ;
  }

  @Nonnull
  @Override
  public ScheduledFuture< ? > scheduleWithFixedDelay(
      final Runnable command,
      final long initialDelay,
      final long delay,
      final TimeUnit unit
  ) {
    final TaskCapture taskCapture = new TaskCapture( initialDelay, delay, unit, true, command ) ;
    taskCaptureConsumer.accept( taskCapture ) ;
    return taskCapture.scheduledFuture() ;
  }

}
