package com.otcdlink.chiron.toolbox.concurrent;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public final class FutureTools {

  private FutureTools() { }


  /**
   * A slightly faster version of {@link #applyToAllAndGatherValues(Collection, Function)}
   * since it doesn't have to keep a list of results.
   */
  public static < SUBJECT > CompletableFuture< Void > applyToAll(
      final Collection< SUBJECT > subjects,
      final Function< SUBJECT, CompletableFuture< Void > > asynchronousOperation
  ) {
    final CompletableFuture[] singleResultFutures = new CompletableFuture[ subjects.size() ] ;

    int index = 0 ;
    for( final SUBJECT subject : subjects ) {
      final CompletableFuture< Void > singleResultFuture = asynchronousOperation.apply( subject ) ;
      singleResultFutures[ index ++ ] = singleResultFuture ;
    }

    return CompletableFuture.allOf( singleResultFutures ) ;
  }

  public static < SUBJECT, RESULT > CompletableFuture< ImmutableList< RESULT > >
  applyToAllAndGatherValues(
      final Collection< SUBJECT > subjects,
      final Function< SUBJECT, CompletableFuture< RESULT > > asynchronousFunction
  ) {
    final Collection< RESULT > results = new ConcurrentLinkedQueue<>() ;
    final CompletableFuture[] singleResultFutures = new CompletableFuture[ subjects.size() ] ;

    int index = 0 ;
    for( final SUBJECT subject : subjects ) {
      final CompletableFuture< RESULT > singleResultFuture = asynchronousFunction.apply( subject ) ;
      singleResultFutures[ index ++ ] = singleResultFuture.whenComplete( ( success, failure ) -> {
        if( failure == null ) {
          results.add( success ) ;
        } else {
          throw rethrow( failure ) ;
        }
      } ) ;
    }

    final CompletableFuture< Void > allCompletionFuture = CompletableFuture.allOf(
        singleResultFutures ) ;

    final CompletableFuture< ImmutableList< RESULT > > resultListFuture =
        new CompletableFuture<>() ;

    allCompletionFuture.whenComplete( ( success, failure ) -> {
      if( failure == null ) {
        resultListFuture.complete( ImmutableList.copyOf( results ) ) ;
      } else {
        resultListFuture.completeExceptionally( failure ) ;
      }
    } ) ;

    return resultListFuture ;
  }

  /**
   * Useful to rethrow a generic exception, methods like
   * {@link CompletableFuture#handle(java.util.function.BiFunction)}
   * need explicit propagation.
   */
  @SuppressWarnings( "UnusedReturnValue" )
  public static CompletionException rethrow( final Throwable failure ) {
    if( failure instanceof CompletionException ) {
      throw ( CompletionException ) failure ;
    } else {
      throw new CompletionException( failure ) ;
    }
  }
}
