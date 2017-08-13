package com.otcdlink.chiron.toolbox.concurrent;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class ListenableFutureDemo {

  private static final Logger LOGGER = LoggerFactory.getLogger( ListenableFutureDemo.class ) ;

  private ListenableFutureDemo() { }

  public static void main( final String... arguments )
      throws ExecutionException, InterruptedException
  {

    final ThreadFactory threadFactory =
        ExecutorTools.newCountingDaemonThreadFactory( ListenableFutureDemo.class ) ;

    final ListeningExecutorService listeningExecutorService = MoreExecutors.listeningDecorator(
        Executors.newFixedThreadPool( 2, threadFactory ) ) ;

    final ListenableFuture< ? > future = listeningExecutorService.submit( () -> {
      listeningExecutorService.shutdown() ;
      LOGGER.info( "Shutdown requested." ) ;
    } ) ;

    future.get() ;

    future.addListener(
        () -> LOGGER.info( "Listener still working with direct Executor." ),
        MoreExecutors.directExecutor()
    ) ;

    // Of course this would cause a RejectedExecutionException.
    // future.addListener(
    //     () -> LOGGER.info( "Listener still working with asynchronous Executor." ),
    //     listeningExecutorService
    // ) ;

    LOGGER.info( "State of " + listeningExecutorService + ": " +
        listeningExecutorService.isShutdown() ) ;

  }

}