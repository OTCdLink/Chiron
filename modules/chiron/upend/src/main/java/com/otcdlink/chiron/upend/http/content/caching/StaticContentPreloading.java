package com.otcdlink.chiron.upend.http.content.caching;

import com.otcdlink.chiron.upend.http.content.StaticContent;

enum StaticContentPreloading {

  /**
   * Don't preload at all.
   */
  LAZY( false, false ),

  /**
   * Preload everything in the calling thread.
   */
  SEQUENTIAL( false, true ),

  /**
   * Preload everything in an {@code Executor} and return only after every {@link StaticContent}
   * was fully loaded.
   */
  EXECUTOR_WAIT( true, true ),


  /**
   * Preload everything in an {@code Executor} but return as soon as possible.
   */
  EXECUTOR_NOWAIT( true, false ),

  ;

  public final boolean usesExecutor ;
  public final boolean wait ;

  StaticContentPreloading( final boolean usesExecutor, final boolean wait ) {
    this.usesExecutor = usesExecutor ;
    this.wait = wait ;
  }
}
