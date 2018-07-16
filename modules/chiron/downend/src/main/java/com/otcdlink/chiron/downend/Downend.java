package com.otcdlink.chiron.downend;

import com.otcdlink.chiron.command.Command;
import io.netty.channel.EventLoopGroup;

import java.util.concurrent.CompletableFuture;

public interface Downend< ENDPOINT_SPECIFIC, UPWARD_DUTY > {

  void send( Command< ENDPOINT_SPECIFIC, UPWARD_DUTY > command ) ;

  DownendConnector.State state() ;

  /**
   * @return a non-null {@code CompletableFuture} that completes once {@link Downend} is ready
   *     to {@link #send(Command)}.
   * @throws IllegalStateException if this method was already called since object creation,
   *     or completion of {@code CompletableFuture} returned by {@link #stop()}.
   */
  CompletableFuture< Void > start() ;

  /**
   * @return a non-null {@code CompletableFuture} that completes once {@link Downend} has
   *     closed the connection, updated its {@link DownendConnector.State}, and performed
   *     appropriate notifications.
   * @throws IllegalStateException if this method is called before the {@code CompletableFuture}
   *     returned by {@link #start()} completed, or if another call to this method happened
   *     before the aforementioned completion.
   */
  CompletableFuture< Void > stop() ;

  /**
   * Tagging interfaces for tasks scheduled in {@link EventLoopGroup}.
   * This is only for tests instrumenting the {@link EventLoopGroup}.
   */
  interface ScheduledInternally extends Runnable {
    interface Ping extends ScheduledInternally { }
    interface PongTimeout extends ScheduledInternally { }
    interface Reconnect extends ScheduledInternally { }
  }


}
