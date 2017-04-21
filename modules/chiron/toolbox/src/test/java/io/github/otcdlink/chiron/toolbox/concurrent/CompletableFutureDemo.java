package io.github.otcdlink.chiron.toolbox.concurrent;

import com.google.common.util.concurrent.Uninterruptibles;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Study the behavior of {@link CompletableFuture}.
 */
public final class CompletableFutureDemo {

  private CompletableFutureDemo() { }

  public static void main( final String... arguments ) throws Exception {
    print( "Running samples ..." ) ;

    chainingWithThenRun() ;
    chainingWithThenRunAsync() ;
    chainingSameThread() ;
    chainingWithThenRunAndNoGet() ;

  }

  private static void chainingSameThread() throws Exception {
    printTitle( "=== Chaining supplyAsunc->thenApply+thenApply, demonstrating Executor shutdown ===\n" +
        "From http://www.nurkiewicz.com/2015/11/which-thread-executes.html" ) ;
    final ExecutorService pool = Executors.newSingleThreadExecutor(
        new DefaultThreadFactory( "ChainingThenRun", false ) ) ;
    final CompletableFuture< String > future = CompletableFuture.supplyAsync(
        () -> {
          Uninterruptibles.sleepUninterruptibly( 1, TimeUnit.SECONDS ) ;
          return "Result here" ;
        },
        pool
    ) ;

    future.thenApply( s -> {
      print( "First transformation" ) ;
      return s.length() ;
    } ) ;

    print( "Obtaining future's result: " + future.get() );
    pool.shutdownNow() ;
    pool.awaitTermination( 1, TimeUnit.MINUTES ) ;
    print( "Shutdown happened." );

    future.thenApply(s -> {
      print( "Second transformation (after shutdown)" ) ;
      return s.length() ;
    } ) ;
  }

  private static void chainingWithThenRun() throws Exception {
    printTitle( "=== Chaining with runAsync->thenRun->, using explicit Executor just once ===" ) ;

    final ExecutorService executor = Executors.newSingleThreadExecutor(
        new DefaultThreadFactory( "ChainingThenRun", false ) ) ;
    CompletableFuture
        .runAsync(
            () -> print( "#runAsync().1" ),
            executor
        )
        .thenRun( () -> print( "#thenRun().2" ) )
        .thenRun( () -> print( "#thenRun().3" ) )
        .get()
    ;
  }
  private static void chainingWithThenRunAsync() throws Exception {
    printTitle( "=== Chaining runAsync->thenRunAsync->thenRunAsync, " +
        "using explict Executor just once ===" ) ;

    final ExecutorService executor = Executors.newSingleThreadExecutor(
        new DefaultThreadFactory( "ChainingThenRun", false ) ) ;
    CompletableFuture
        .runAsync(
            () -> print( "#runAsync().1" ),
            executor
        )
        .thenRunAsync( () -> print( "#thenRunAsync().2" ) )
        .thenRunAsync( () -> print( "#thenRunAsync().3" ) )
        .get()
    ;
  }

  private static void chainingWithThenRunAndNoGet() throws Exception {
    printTitle( "=== Chaining runAsync->thenRun->thenRun, using explict Executor just once ===" ) ;

    final ExecutorService executor = Executors.newSingleThreadExecutor(
        new DefaultThreadFactory( "ChainingThenRun", false ) ) ;
    final CompletableFuture< Void > completableFuture = CompletableFuture
        .runAsync(
            () -> {
              print( "#runAsync().1 (a small delay forces subsequent tasks to use same thread)" ) ;
              Uninterruptibles.sleepUninterruptibly( 100, TimeUnit.MILLISECONDS ) ;
            },
            executor
        )
        .thenRun( () -> print( "#thenRun().2" ) )
        .thenRun( () -> print( "#thenRun().3" ) )
    ;
    Uninterruptibles.sleepUninterruptibly( 1, TimeUnit.SECONDS ) ;
    print( "Everything should have completed by now. Forcing completion to be sure." ) ;
    completableFuture.join() ;
  }




// ======
// Boring
// ======

  private static void printTitle( final String message ) {
    System.out.println( "\n" + message ) ;
  }
  private static void print( final String message ) {
    System.out.println( Thread.currentThread() + " | " + message ) ;
  }
}
