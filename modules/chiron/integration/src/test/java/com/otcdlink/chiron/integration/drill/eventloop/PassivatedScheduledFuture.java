package com.otcdlink.chiron.integration.drill.eventloop;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This is a really crappy implementation of {@link ScheduledFuture} but until now
 * it is good enough for what we do with it.
 */
public class PassivatedScheduledFuture< V > implements ScheduledFuture< V > {

  private static final Logger LOGGER = LoggerFactory.getLogger( PassivatedScheduledFuture.class ) ;

  private final String ownerAsString ;

  public PassivatedScheduledFuture( final String ownerAsString ) {
    this.ownerAsString = ownerAsString ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + ownerAsString + "}" ;
  }


// ===============
// ScheduledFuture
// ===============

  @Override
  public long getDelay( TimeUnit unit ) {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public int compareTo( Delayed o ) {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public boolean isSuccess() {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public boolean isCancellable() {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public Throwable cause() {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public Future<V> addListener( GenericFutureListener<? extends Future<? super V>> listener ) {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public Future<V> addListeners( GenericFutureListener<? extends Future<? super V>>... listeners ) {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public Future<V> removeListener( GenericFutureListener<? extends Future<? super V>> listener ) {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public Future<V> removeListeners( GenericFutureListener<? extends Future<? super V>>... listeners ) {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public Future<V> sync() throws InterruptedException {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public Future<V> syncUninterruptibly() {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public Future<V> await() throws InterruptedException {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public Future<V> awaitUninterruptibly() {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public boolean await( long timeout, TimeUnit unit ) throws InterruptedException {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public boolean await( long timeoutMillis ) throws InterruptedException {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public boolean awaitUninterruptibly( long timeout, TimeUnit unit ) {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public boolean awaitUninterruptibly( long timeoutMillis ) {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public V getNow() {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public boolean cancel( boolean mayInterruptIfRunning ) {
    LOGGER.info( "Cancelling " + this + "." ) ;
    return true ;
  }

  @Override
  public boolean isCancelled() {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public boolean isDone() {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public V get() throws InterruptedException, ExecutionException {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public V get( long timeout, TimeUnit unit ) throws InterruptedException, ExecutionException, TimeoutException {
    throw new UnsupportedOperationException( "TODO" );
  }
}
