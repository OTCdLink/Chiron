package com.otcdlink.chiron.toolbox.clock;

import com.otcdlink.chiron.toolbox.ToStringTools;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Only for tests.
 * It behaves as an {@link UpdateableClock}, and setting current time triggers
 * {@link Pulse.Tickee#tick(DateTime)} on every created {@link Pulse}.
 */
public class PulseModulator implements Pulse.Factory, UpdateableClock {

  /**
   * There is no method to release created {@link Pulse}s so there is a memory leak here,
   * but it's OK because we use {@link PulseModulator} only inside tests.
   */
  private final List< InstrumentedPulse > pulses = new ArrayList<>() ;

  private long current ;

  private final Object lock = ToStringTools.createLockWithNiceToString( PulseModulator.class ) ;

  public PulseModulator( final long current ) {
    set( current ) ;
  }

  public static PulseModulator createClock( final long milliseconds ) {
    return new PulseModulator( milliseconds ) ;
  }

  public static PulseModulator createClock( final DateTime dateTime ) {
    return new PulseModulator( dateTime.getMillis() ) ;
  }

  /**
   * Creates a {@link Pulse} that will be affected by {@link #set(long)}.
   * By now we don't care about freeing the list of created {@link Pulse}s.
   */
  @Override
  public Pulse create( final Pulse.Resolution resolution, final Pulse.Tickee tickee ) {
    checkNotNull( tickee ) ;
    final InstrumentedPulse pulse = new InstrumentedPulse( resolution, tickee );
    synchronized( lock ) {
      pulses.add( pulse ) ;
    }
    return pulse ;
  }

  @Override
  public long currentTimeMillis() {
    return this.current ;
  }

  @Override
  public final DateTime set( final long milliseconds ) {
    checkArgument( milliseconds >= 0 ) ;
    synchronized( lock ) {
      this.current = milliseconds ;
      tick() ;
      return getCurrentDateTime() ;
    }
  }

  @Override
  public DateTime set( final DateTime dateTime ) {
    return set( dateTime.getMillis() ) ;
  }

  @Override
  public DateTime increment() {
    return increment( 1 ) ;
  }

  @Override
  public DateTime increment( final long milliseconds ) {
    checkArgument( milliseconds >= 0 ) ;
    synchronized( lock ) {
      current += milliseconds ;
      tick() ;
      return getCurrentDateTime() ;
    }
  }

  private void tick() {
    pulses.forEach( PulseModulator.InstrumentedPulse::evaluateTransitionNow ) ;
  }


  private class InstrumentedPulse extends Pulse {

    protected InstrumentedPulse( final Resolution resolution, final Tickee tickee ) {
      super( resolution, tickee ) ;
    }

    @Override
    protected void startScheduler() { }

    @Override
    protected void schedule( final long delayMilliseconds, final Runnable runnable ) { }

    @Override
    protected void stopScheduler() { }

    @Override
    public long currentTimeMillis() {
      return current ;
    }
  }
}
