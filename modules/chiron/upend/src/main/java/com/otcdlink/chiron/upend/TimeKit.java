package com.otcdlink.chiron.upend;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.toolbox.clock.Clock;
import com.otcdlink.chiron.toolbox.clock.Pulse;
import com.otcdlink.chiron.toolbox.clock.PulseModulator;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Gather some time-related components so they give consistent values.
 * Rename into TimeProvisioner?
 */
public class TimeKit< CLOCK extends Clock> {

  public final CLOCK clock ;

  public final Designator.Factory designatorFactory ;
  public final Stamp.Generator stampGenerator;
  public final Pulse.Factory pulseFactory ;

  public TimeKit(
      final CLOCK clock,
      final Pulse.Factory pulseFactory
  ) {
    this.clock = checkNotNull( clock ) ;
    this.stampGenerator = new Stamp.Generator( clock ) ;
    this.designatorFactory = new Designator.Factory( stampGenerator ) ;
    this.pulseFactory = checkNotNull( pulseFactory ) ;
  }

  public static TimeKit< UpdateableClock > instrumentedTimeKit( final DateTime initialClockValue ) {
    return instrumentedTimeKit( initialClockValue.getMillis() ) ;
  }

  public static TimeKit< UpdateableClock > instrumentedTimeKit( final long initialClockValue ) {
    final UpdateableClock updateableClock = new PulseModulator( initialClockValue ) ;
    return new TimeKit<>(
        updateableClock,
        ( resolution, tickee ) -> new Pulse( resolution, tickee ) {
      @Override
      protected void startScheduler() { }

      @Override
      protected void schedule( final long delayMilliseconds, final Runnable runnable ) { }

      @Override
      protected void stopScheduler() { }

      @Override
      public long currentTimeMillis() { return updateableClock.currentTimeMillis() ; }

    } ) ;
  }

  public static TimeKit< PulseModulator > instrumentedTimeKit(
      final long initialClockValue,
      final Pulse.Resolution pulseResolution
  ) {
    final PulseModulator pulseModulator = new PulseModulator( initialClockValue ) ;
    return new TimeKit<>( pulseModulator, pulseModulator ) ;
  }


  public static TimeKit< Clock > fromSystemClock() {
    return new TimeKit<>(
        Clock.SYSTEM_CLOCK,
        Pulse.Factory.newWithExecutorService()
    ) ;
  }



}
