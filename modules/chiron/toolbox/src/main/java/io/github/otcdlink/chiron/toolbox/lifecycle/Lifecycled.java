package io.github.otcdlink.chiron.toolbox.lifecycle;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.toolbox.collection.ConstantShelf;

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
 *     of {@link Lifecycled} are supposed to be not too frequently instantiated.
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
 *
 * <h1>Why not Guava {@link com.google.common.util.concurrent.AbstractService}?</h1>
 * <p>
 * Its services are not restartable, and its thread naming is a mess.
 * There are so many cases supported that it's easy to get lost; implementing wished feature
 * looks sometimes "unnatural".
 * Passing back a result is not supported at all.
 * The need for an "ERROR" state is arguable.
 *
 * @param <SETUP>
 * @param <COMPLETION>
 */
public interface Lifecycled< SETUP, COMPLETION > {

  void initialize( SETUP setup ) ;

  /**
   * @throws IllegalStateException if already started. More formally, if {@link #start()}
   *     was called since instance creation, and {@link #terminationFuture()} did not complete
   *     after that.
   */
  CompletableFuture< ? > start() ;


  /**
   * Same as {@link #start()} but may returns a {@link COMPLETION} value.
   * The {@code CompletableFuture} is the one returned by {@link #stop()}, but the latter
   * may cause premature termination.
   */
  CompletableFuture< COMPLETION > run() throws Exception;

  /**
   * Try to terminate execution as soon as possible.
   *
   * @return a {@code CompletableFuture} that may return {@code null}.
   * @throws IllegalStateException if {@link #startFuture()} did not complete.
   */
  CompletableFuture< COMPLETION > stop() ;

  /**
   * Returns the same {@code CompletableFuture} as {@link #start()}.
   */
  CompletableFuture< ? > startFuture() ;

  /**
   * Returns the same {@code CompletableFuture} as {@link #stop()} or {@link #run()} would.
   */
  CompletableFuture< COMPLETION > terminationFuture() ;

  /**
   * Non-final class so subclasses can add their own states.
   */
  class State extends ConstantShelf {

    protected static State newState() {
      return new State() ;
    }

    /**
     * {@link Lifecycled} instance created but no {@link #initialize} yet.
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
     * The {@link Lifecycled} object is fully usable.
     * For a remote Java process, it means that the process itself is past its own initialization.
     */
    public static final State STARTED = newState() ;

    /**
     * {@link #stop()} got called, there is some cleanup in progress.
     */
    public static final State STOPPING = newState() ;

    /**
     * Used by {@link AbstractLifecycled#executeSingleComputation(ThreadFactory, Callable)}.
     */
    public static final State BUSY = newState() ;

    public static final ImmutableMap< String, State > MAP = valueMap( State.class ) ;
  }
}
