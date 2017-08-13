package com.otcdlink.chiron.toolbox.diagnostic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Publish-subscribe mechanism for {@link Diagnostic}.
 * This is one of the rare implementation of the Listener pattern, otherwise we prefer
 * the Watcher pattern (where there is a single listener passed to the constructor).
 */
public interface DiagnosticUpdate {


  interface Source< DIAGNOSTIC extends Diagnostic > {
    void addListener( final Listener< DIAGNOSTIC > listener ) ;
    void removeListener( final Listener< DIAGNOSTIC > listener ) ;
    DIAGNOSTIC current() ;
  }

  interface Listener< DIAGNOSTIC extends Diagnostic > {
    void updated( DIAGNOSTIC diagnostic ) ;
  }

  interface Applier< DIAGNOSTIC extends Diagnostic > {
    void update( final DIAGNOSTIC diagnostic ) ;
  }

  interface Whole< DIAGNOSTIC extends Diagnostic >
      extends Source< DIAGNOSTIC >, Applier< DIAGNOSTIC >
  { }

  final class Default< DIAGNOSTIC extends Diagnostic > implements Whole< DIAGNOSTIC > {

    private volatile DIAGNOSTIC diagnostic ;
    private final List< Listener > listeners = new CopyOnWriteArrayList<>() ;

    public Default() {
      this.diagnostic = null ;
    }

    public Default( DIAGNOSTIC diagnostic ) {
      this.diagnostic = checkNotNull( diagnostic ) ;
    }

    @Override
    public void addListener( Listener listener ) {
      listeners.add( checkNotNull( listener ) ) ;
    }

    @Override
    public void removeListener( Listener listener ) {
      listeners.remove( checkNotNull( listener ) ) ;
    }

    public void update( final DIAGNOSTIC diagnostic ) {
      this.diagnostic = checkNotNull( diagnostic ) ;
      for( final Listener listener : listeners ) {
        listener.updated( diagnostic ) ;
      }
    }

    /**
     * Returned {@link Diagnostic} may be inconsistent with the one passed to
     * {@link Listener#updated(Diagnostic)} because we don't synchronize everything,
     * but we don't look for ultimate consistency here.
     */
    @Override
    public DIAGNOSTIC current() {
      return diagnostic ;
    }
  }

}
