package io.github.otcdlink.chiron.toolbox.concurrent;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * http://minborgsjavapot.blogspot.com/2016/01/be-lazy-with-java-8.html
 */
public final class Lazy< T > implements Supplier< T > {

  private final Supplier< T > supplier ;
  private volatile T value = null ;

  public Lazy( final Supplier< T > supplier ) {
    this.supplier = requireNonNull( supplier ) ;
  }

  @Override
  public T get() {
    final T result = value ;  // Read volatile just once.
    return result == null ? maybeCompute() : result ;
  }

  private T maybeCompute() {
    synchronized( this ) {
      if( value == null ) {
        value = requireNonNull( supplier.get() ) ;
      }
      return value ;
    }
  }

}
