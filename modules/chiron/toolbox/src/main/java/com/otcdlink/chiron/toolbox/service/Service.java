package com.otcdlink.chiron.toolbox.service;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.toolbox.collection.Autoconstant;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

/**
 * Generic behavior of a service-like object.
 *
 * <h1>Main features</h1>
 * <ul>
 *   <li>
 *    Well-defined {@link State}s.
 *   </li> <li>
 *     Thread-safety through blocking primitives. Non-blocking is good for IOs, implementors
 *     of {@link Service} are supposed to be not too frequently instantiated.
 *   </li> <li>
 *     Restartable.
 *   </li> <li>
 *     Fine grained control of threads (especially naming).
 *   </li> <li>
 *     Synchronous <em>and</em> asynchronous {@link #start()} and {@link #stop()} with
 *     {@code CompletableFuture}.
 *   </li> <li>
 *     Get a result of {@link COMPLETION} type from {@link #run()}.
 *   </li>
 * </ul>
 * <p>
 * The main use case is to run processes through SSH.
 *
 * <h1>Why not Guava {@link com.google.common.util.concurrent.Service}?</h1>
 * <ul>
 *   <li>
 *     Its services are not restartable. (OK, most of time we don't need that.)
 *   </li><li>
 *     Thread naming is a mess.
 *   </li><li>
 *     There are so many cases supported that it's easy to get lost; implementing wished feature
 *     looks sometimes "unnatural".
 *   </li><li>
 *     Passing back a result is not supported at all.
 *   </li><li>
 *     The 'await' primitives are ugly, {@code CompletableFuture} rulez.
 *   </li><li>
 *     The need for an "ERROR" state is arguable.
 *   </li>
 * </ul>
 *
 * @param <SETUP>
 * @param <COMPLETION>
 */
public interface Service< SETUP, COMPLETION > extends Lifecycled< COMPLETION > {

  /**
   * Pass a {@link SETUP} object for additional parameters that were not available when
   * instantiating.
   */
  void setup( SETUP setup ) ;

  /**
   * Try to become {@link Service.State#STARTED}.
   *
   * @throws IllegalStateException if not in {@link Service.State#STOPPED}.
   *     More formally, if {@link #start()} was called since instance creation,
   *     and {@link #terminationFuture()} did not complete after that.
   */
  CompletableFuture< Void > start() ;

  /**
   * Try to become {@link Service.State#STOPPED}.
   * This method does <em>not</em> throw an {@code IllegalStateException} if state is already
   * {@link Service.State#STOPPING} or {@link Service.State#STOPPED} because it may already have "naturally"
   * completed (because of {@link #run()}.
   *
   * @return a {@code CompletableFuture} that may return {@code null}.
   * @throws IllegalStateException if not {@link Service.State#STARTED}, {@link Service.State#STOPPING}
   *     or {@link Service.State#STOPPED}.
   */
  CompletableFuture<COMPLETION> stop() ;



  /**
   * Same as {@link #start()} but may returns a {@link COMPLETION} value.
   * The {@code CompletableFuture} is the one returned by {@link #stop()}, but the latter
   * may cause premature termination.
   */
  CompletableFuture< COMPLETION > run() throws Exception ;

  /**
   * Returns the same {@code CompletableFuture} as {@link #start()}.
   */
  CompletableFuture< Void > startFuture() ;

  /**
   * Returns the same {@code CompletableFuture} as {@link #stop()} or {@link #run()} would.
   */
  CompletableFuture< COMPLETION > terminationFuture() ;

  /**
   * Non-final class so subclasses can add their own states.
   */
  class State extends Autoconstant {

    protected static State newState() {
      return new State() ;
    }

    /**
     * {@link Service} instance created but no call to {@link #setup(Object)} occured yet.
     */
    public static final State NEW = newState() ;

    /**
     * Ready to get {@link #start()}ed.
     */
    public static final State STOPPED = newState() ;

    /**
     * Initialization happening.
     * For running a remote Java process, this {@link State} represents the creation of SSH
     * connections and ancillary threads.
     */
    public static final State INITIALIZING = newState() ;

    /**
     * This is the state next to {@link #INITIALIZING} after calling {@link #start()}.
     * For a remote Java process, this means the process is running, standard input/output streams
     * are connected, but some additional operations need to be completed before getting
     * {@link #STARTED}.
     */
    public static final State STARTING = newState() ;

    /**
     * The {@link Service} object is fully usable.
     * For a remote Java process, it means that the process itself is past its own initialization.
     */
    public static final State STARTED = newState() ;

    /**
     * {@link #stop()} got called, there is some cleanup in progress.
     */
    public static final State STOPPING = newState() ;

    /**
     * Used by {@link AbstractService#executeSingleComputation(ThreadFactory, Callable)}.
     */
    public static final State BUSY = newState() ;

    public static final ImmutableMap< String, State > MAP = valueMap( State.class ) ;
  }
}
