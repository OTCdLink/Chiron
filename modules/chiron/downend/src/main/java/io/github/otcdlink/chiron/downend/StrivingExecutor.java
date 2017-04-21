package io.github.otcdlink.chiron.downend;

import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.concurrent.ExecutorTools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A variant of {@link java.util.concurrent.Executor} that may not run at all.
 */
public interface StrivingExecutor {

  boolean tryExecute( final Runnable runnable ) ;

  class Lifecycled implements StrivingExecutor {

    private final Object lock =
        ToStringTools.createLockWithNiceToString( StrivingExecutor.class ) ;

    private final long shutdownTimeout ;
    private final TimeUnit shutdownTimeUnit ;

    private final ExecutorTools.ExecutorServiceFactory factory ;

    private final AtomicReference< ExecutorService > executorService = new AtomicReference<>() ;

    public Lifecycled( final ExecutorTools.ExecutorServiceFactory factory ) {
      this( factory, 10, SECONDS ) ;
    }

    public Lifecycled(
        final ExecutorTools.ExecutorServiceFactory factory,
        final long shutdownTimeout,
        final TimeUnit shutdownTimeUnit
    ) {
      this.factory = checkNotNull( factory ) ;
      checkArgument( shutdownTimeout >= 0 );
      this.shutdownTimeout = shutdownTimeout ;
      this.shutdownTimeUnit = checkNotNull( shutdownTimeUnit ) ;
    }

    @Override
    public boolean tryExecute( final Runnable runnable ) {
      checkNotNull( runnable ) ;
      final ExecutorService currentService = executorService.get() ;
      if( currentService == null ) {
        return false ;
      } else {
        try {
          currentService.execute( runnable ) ;
          return true ;
        } catch( final RejectedExecutionException e ) {
          return false ;
        }
      }
    }

    public void start() {
      synchronized( lock ) {
        checkState(  executorService.compareAndSet( null, factory.create() ),
            "Already started " + this ) ;
      }
    }

    public void stop() throws InterruptedException {
      synchronized( lock ) {
        final ExecutorService current = executorService.getAndSet( null ) ;
        checkState( current != null, "Not started " + this ) ;
        current.awaitTermination( shutdownTimeout, shutdownTimeUnit ) ;
      }
    }

    @Override
    public String toString() {
      return ToStringTools.nameAndCompactHash( this ) + "{}" ;
    }
  }


}
