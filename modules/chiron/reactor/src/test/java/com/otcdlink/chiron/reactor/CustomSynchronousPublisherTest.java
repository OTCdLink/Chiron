package com.otcdlink.chiron.reactor;

import com.otcdlink.chiron.toolbox.ToStringTools;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class CustomSynchronousPublisherTest {

  @Test
  public void name() throws Exception {

//    final ExecutorService producerExecutor =
//        Executors.newSingleThreadExecutor( ExecutorTools.newThreadFactory( "producer" ) ) ;
//    final ExecutorService consumerExecutor =
//        Executors.newSingleThreadExecutor( ExecutorTools.newThreadFactory( "consumer" ) ) ;

    final MyPublisher< String > myPublisher = new MyPublisher<>() ;
    myPublisher.value( "A" ) ;
    myPublisher.value( "B" ) ;
    myPublisher.subscribe( new MySubscriber<>() ) ;
    myPublisher.complete() ;

  }


// ======
// Custom
// ======

  private static final Logger LOGGER = LoggerFactory.getLogger( CustomSynchronousPublisherTest.class ) ;

  /**
   * Supports only one {@link Subscriber} and avoid any kind of concurrency.
   */
  private static class MyPublisher< T > implements Publisher< T > {

    private MySubscription subscription = null ;
    private final Object lock = ToStringTools.createLockWithNiceToString( MyPublisher.class ) ;
    private final Queue< T > queue = new LinkedList<>() ;

    @Override
    public void subscribe( final Subscriber< ? super T > subscriber ) {
      LOGGER.info( this + "#subscribe( " + subscriber + " )" ) ;
      synchronized( lock ) {
        checkState( subscription == null ) ;
        subscription = new MySubscription( subscriber ) ;
        subscriber.onSubscribe( subscription ) ;
        publishValues() ;
      }
    }

    public void value( final T value ) {
      checkNotNull( value ) ;
      LOGGER.info( this + "#value( " + value + " )" ) ;
      synchronized( lock ) {
        queue.add( value ) ;
        publishValues() ;
      }
    }

    /**
     * Caller must synchronize on {@link #lock}.
     */
    private void publishValues() {
      while( subscription != null && subscription.requested > 0 && ! queue.isEmpty() ) {
        try {
          subscription.subscriber.onNext( queue.remove() ) ;
        } finally {
          subscription.requested -- ;
        }
      }
    }

    public void complete() {
      LOGGER.info( this + "#complete()" ) ;
      synchronized( lock ) {
        if( subscription != null ) {
          try {
            subscription.subscriber.onComplete() ;
          } finally {
            subscription = null ;
          }
        }
      }
    }


    @Override
    public String toString() {
      return ToStringTools.nameAndCompactHash( this ) ;
    }

    private class MySubscription implements Subscription {

      private final Subscriber< ? super T > subscriber ;
      private long requested = 0 ;

      public MySubscription( final Subscriber< ? super T > subscriber ) {
        this.subscriber = checkNotNull( subscriber ) ;
      }

      @Override
      public void request( final long n ) {
        checkArgument( n > 0 ) ;
        LOGGER.info( this + "#request( " + n + " )" ) ;
        synchronized( lock ) {
          requested += n ;
        }
      }

      @Override
      public void cancel() {
        LOGGER.info( this + "#cancel()" ) ;
        synchronized( lock ) {
          if( MyPublisher.this.subscription == this ) {
            subscription = null ;
          }
        }
      }

      @Override
      public String toString() {
        return ToStringTools.nameAndCompactHash( this ) + "{" + subscriber + "}" ;
      }

    }
  }

  public static class MySubscriber< T > implements Subscriber< T > {

    private AtomicReference< Subscription > subscription = new AtomicReference<>() ;

    @Override
    public void onSubscribe( final Subscription subscription ) {
      checkState( this.subscription.compareAndSet( null, subscription ) ) ;
      LOGGER.info( this + "#onSubscribe( " + subscription + " )" ) ;
      subscription.request( 1 ) ;
    }

    @Override
    public void onNext( final T t ) {
      LOGGER.info( this + "#onNext( " + t + " )" ) ;
      subscription.get().request( 1 ) ;
    }

    @Override
    public void onError( Throwable t ) {
      LOGGER.info( this + "#onError( " + t + " )" ) ;
    }

    @Override
    public void onComplete() {
      LOGGER.info( this + "#onComplete()" ) ;

    }

    @Override
    public String toString() {
      return ToStringTools.nameAndCompactHash( this ) ;
    }
  }

}
