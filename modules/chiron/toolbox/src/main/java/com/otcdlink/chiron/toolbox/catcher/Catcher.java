package com.otcdlink.chiron.toolbox.catcher;

/**
 * General contract for something that processes uncaught {@code Throwable}s.
 *
 * TODO: use {@link Thread.UncaughtExceptionHandler} which also passes the {@link Thread}.
 *
 */
public interface Catcher {

  /**
   * Must be thread-safe.
   */
  void processThrowable( Throwable throwable ) ;

}
