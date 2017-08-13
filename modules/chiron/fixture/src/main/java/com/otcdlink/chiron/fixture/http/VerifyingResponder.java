package com.otcdlink.chiron.fixture.http;

import com.google.common.util.concurrent.Monitor;
import com.otcdlink.chiron.toolbox.netty.Hypermessage;
import com.otcdlink.chiron.toolbox.text.Plural;

import java.util.LinkedList;
import java.util.Queue;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Send planned {@link Hypermessage.Response} for expected {@link Hypermessage.Request},
 * test code knows when all planned {@link Exchange}s are exhausted, when
 * {@link #waitForCompletion()} returns; a {@link ResponderException} may also signal
 * that something unexpected happened.
 */
public class VerifyingResponder implements TinyHttpServer.Responder {

  /**
   * Access synchronized on {@link #monitor}.
   */
  private final Queue< Exchange > plannedExchanges = new LinkedList<>() ;

  /**
   * Access synchronized on {@link #monitor}.
   */
  private int exchangeCount = 0 ;

  /**
   * Access synchronized on {@link #monitor}.
   */
  private ResponderException firstFailure = null ;

  /**
   * Access synchronized on {@link #monitor}.
   */
  private Progress progress = Progress.CREATED ;

  private enum Progress {
    CREATED, FIRST_PLANNING_HAPPENED, FIRST_EXCHANGE_HAPPENED
  }

  private final Monitor monitor = new Monitor() ;

  private final Monitor.Guard completed = new Monitor.Guard( monitor ) {
    @Override
    public boolean isSatisfied() {
      return firstFailure != null ||
          ( progress == Progress.FIRST_EXCHANGE_HAPPENED && plannedExchanges.isEmpty() ) ;
    }
  } ;


  public static class Exchange {
    public final Hypermessage.Request request ;
    public final Hypermessage.Response response ;

    public Exchange( final Hypermessage.Request request, final Hypermessage.Response response ) {
      this.request = checkNotNull( request ) ;
      this.response = checkNotNull( response ) ;
    }
  }

  public void plan( final Hypermessage.Request request, final Hypermessage.Response response ) {
    monitor.enter() ;
    try {
      if( progress == Progress.CREATED ) {
        progress = Progress.FIRST_PLANNING_HAPPENED ;
      }
      plannedExchanges.add( new Exchange( request, response ) ) ;
    } finally {
      monitor.leave() ;
    }
  }

  public void waitForCompletion() throws ResponderException {
    monitor.enter() ;
    try {
      monitor.waitFor( completed ) ;
      if( firstFailure != null ) {
        throw firstFailure ;
      }
    } catch( final InterruptedException e ) {
      throw new RuntimeException( "Should not happen", e ) ;
    } finally {
      monitor.leave() ;
    }
  }

  @Override
  public Hypermessage.Response respondTo( final Hypermessage.Request request ) {
    monitor.enter() ;
    try {
      if( firstFailure == null ) {
        final Exchange planned = plannedExchanges.poll() ;
        if( planned == null ) {
          firstFailure = new PlanExhaustedException( exchangeCount ) ;
        } else {
          if( progress == Progress.FIRST_PLANNING_HAPPENED ) {
            progress = Progress.FIRST_EXCHANGE_HAPPENED ;
          }
          exchangeCount ++ ;
          if( planned.request.equals( request ) ) {
            return planned.response ;
          } else {
            firstFailure = new RequestMismatchException( planned.request, request, exchangeCount ) ;
          }
        }
      }
    } finally {
      monitor.leave() ;
    }
    return null ;
  }

  public static class ResponderException extends Exception {
    public ResponderException( final String message ) {
      super( message ) ;
    }
  }

  public static class RequestMismatchException extends ResponderException {
    public RequestMismatchException(
        final Hypermessage.Request expected,
        final Hypermessage.Request actual,
        final int exchangePosition
    ) {
      super( "Expecting " + expected + " but got " + actual +
          " at " + Plural.th( exchangePosition ) +  " " + Exchange.class.getSimpleName() ) ;
    }
  }

  public static class PlanExhaustedException extends ResponderException {
    public PlanExhaustedException( final int exchangeCount ) {
      super( "No more " + Exchange.class.getSimpleName() + " in the queue after " +
          Plural.s( exchangeCount, Exchange.class.getSimpleName() ) ) ;
    }
  }
}
