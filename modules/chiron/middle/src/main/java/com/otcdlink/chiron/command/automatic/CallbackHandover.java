package com.otcdlink.chiron.command.automatic;

import com.otcdlink.chiron.toolbox.handover.Handover;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link Handover} with a callback on receiver side.
 * Callback executes in the thread calling {@link #give(Object)}.
 */
public class CallbackHandover< OBJECT > implements Handover< OBJECT > {

  private final AtomicReference< Consumer< OBJECT > > callback = new AtomicReference<>() ;

  public void takeOrWait( final Consumer< OBJECT > onSuccess ) {
    checkNotNull( onSuccess ) ;
    final Consumer< OBJECT > previous = callback.getAndSet( onSuccess ) ;
    checkState( previous == null, "Already waiting with " + previous ) ;
  }

  @Override
  public void give( final OBJECT object ) {
    final Consumer< OBJECT > consumer = callback.getAndSet( null ) ;
    checkState( consumer != null, "Not waiting" ) ;
    consumer.accept( object ) ;
  }

}
