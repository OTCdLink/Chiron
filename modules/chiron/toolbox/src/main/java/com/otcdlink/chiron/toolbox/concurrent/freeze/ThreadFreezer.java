package com.otcdlink.chiron.toolbox.concurrent.freeze;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Monitor;
import com.otcdlink.chiron.toolbox.ToStringTools;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Threading utility to ask several threads (identified by an arbitrary {@link KEY})
 * to block themselves and pass a value, then wait until all those threads are blocked
 * and gather the passed values, then unblock them.
 */
public final class ThreadFreezer< KEY > {

  private final ImmutableMap< KEY, InternalFreezeControl > freezeControls ;

  /**
   * Usage of {@link InternalFreezeControl#unitMonitor} is subordinate to this {@link Monitor}
   * so we can make freeze/unfreeze changes "bubble up" to {@link ThreadFreezer}.
   */
  private final Monitor allMonitor = new Monitor() ;

  /**
   * No need for additional locking when evaluating {@link Monitor.Guard#isSatisfied()} because
   * {@link InternalFreezeControl} already entered {@link #allMonitor} at this point.
   */
  private final Monitor.Guard allFrozen = new Monitor.Guard( allMonitor ) {
    @Override
    public boolean isSatisfied() {
      return freezeControls.values().stream().allMatch( fc -> fc.frozen != null ) ;
    }
  } ;

  public ThreadFreezer( final ImmutableSet< KEY > keys ) {
    final ImmutableMap.Builder< KEY, InternalFreezeControl > builder = ImmutableMap.builder() ;
    for( final KEY key : keys ) {
      builder.put( key, new InternalFreezeControl( key ) ) ;
    }
    this.freezeControls = builder.build() ;
  }

  public ImmutableMap< KEY, Object > waitForAllFrozen() {
    final ImmutableMap.Builder< KEY, Object > builder = ImmutableMap.builder() ;
    allMonitor.enterWhenUninterruptibly( allFrozen ) ;
    try {
      for( final Map.Entry< KEY, InternalFreezeControl > entry : freezeControls.entrySet() ) {
        builder.put( entry.getKey(), entry.getValue().frozen ) ;
      }
      return builder.build() ;
    } finally {
      allMonitor.leave() ;
    }
  }

  public < FROZEN > FreezeControl< FROZEN > internalControl( final KEY key ) {
    return freezeControls.get( key ) ;
  }

  public void unfreezeAll() {
    allMonitor.enterWhenUninterruptibly( allFrozen ) ;
    try {
      for( final InternalFreezeControl freezeControl : freezeControls.values() ) {
        freezeControl.unfreeze() ;
      }
    } finally {
      allMonitor.leave() ;
    }
  }



  private class InternalFreezeControl implements FreezeControl {

    private final KEY key ;

    /**
     * {@code volatile} avoids lock acquisition when we just want to know if warm,
     * in a context in which no concurrent change may happen.
     */
    private volatile Object frozen = null ;

    private boolean warm() {
      return frozen == null ;
    }

    private final Monitor unitMonitor = new Monitor() ;

    private final Monitor.Guard isWarm = new Monitor.Guard( unitMonitor ) {
      public boolean isSatisfied() {
        return warm() ;
      }
    } ;

    private InternalFreezeControl( final KEY key ) {
      this.key = checkNotNull( key ) ;
    }

    /**
     * Blocks calling thread, if {@link #freeze(Object)} was called since
     * {@link InternalFreezeControl} creation, or since last call to {@link #unfreeze()}.
     * Must be called only in a context in which no concurrent change may happen, since it
     * relies on a {@code volatile} variable to continue quickly if there is no reason to block.
     */
    @Override
    public void continueWhenWarm() {
      if( ! warm() ) {
        unitMonitor.enter() ;
        try {
          unitMonitor.waitForUninterruptibly( isWarm ) ;
        } finally {
          unitMonitor.leave() ;
        }
      }
    }

    @Override
    public void freeze( final Object frozen ) {
      checkNotNull( frozen ) ;
      allMonitor.enter() ;
      unitMonitor.enter() ;
      try {
        checkState( this.frozen == null ) ;
        this.frozen = frozen ;
      } finally {
        unitMonitor.leave() ;
        allMonitor.leave() ;
      }
    }

    public void unfreeze() {
      allMonitor.enter() ;
      unitMonitor.enter() ;
      try {
        checkState( frozen != null ) ;
        frozen = null ;
      } finally {
        unitMonitor.leave() ;
        allMonitor.leave() ;
      }
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" +
          key +
          "}"
      ;
    }
  }
}
