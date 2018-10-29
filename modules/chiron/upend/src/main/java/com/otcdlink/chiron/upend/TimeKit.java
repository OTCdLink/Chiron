package com.otcdlink.chiron.upend;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.toolbox.clock.Clock;
import com.otcdlink.chiron.toolbox.clock.Pulse;
import com.otcdlink.chiron.toolbox.clock.PulseModulator;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import org.joda.time.DateTime;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Gather some time-related components so they give consistent values.
 * Rename into TimeProvisioner?
 */
public class TimeKit< CLOCK extends Clock > {

  public final CLOCK clock ;

  public final Designator.Factory designatorFactory ;
  public final Stamp.Generator stampGenerator;
  public final Pulse.Factory pulseFactory ;

  public TimeKit(
      final CLOCK clock,
      final Pulse.Factory pulseFactory
  ) {
    this( clock, Designator.Factory::new, pulseFactory ) ;
  }

  protected TimeKit(
      final CLOCK clock,
      final Function< Stamp.Generator, Designator.Factory > designatorFactoryFactory,
      final Pulse.Factory pulseFactory
  ) {
    this.clock = checkNotNull( clock ) ;
    this.stampGenerator = new Stamp.Generator( clock ) ;
    this.designatorFactory = designatorFactoryFactory.apply( stampGenerator ) ;
    this.pulseFactory = checkNotNull( pulseFactory ) ;
  }

  public static TimeKit< UpdateableClock > instrumentedTimeKit( final DateTime initialClockValue ) {
    return instrumentedTimeKit( initialClockValue.getMillis() ) ;
  }

  public static TimeKit< UpdateableClock > instrumentedTimeKit() {
    return instrumentedTimeKit( Stamp.FLOOR ) ;
  }

  public static TimeKit< UpdateableClock > instrumentedTimeKit( final long initialClockValue ) {
    final UpdateableClock updateableClock = new PulseModulator( initialClockValue ) ;
    return new TimeKit<>(
        updateableClock,
        ( resolution, tickee ) -> new SimplePulse( resolution, tickee, updateableClock ) ) ;
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


  protected static class SimplePulse extends Pulse {
    private final Clock clock;

    public SimplePulse(
        final Resolution resolution,
        final Tickee tickee,
        final Clock clock
    ) {
      super( resolution, tickee ) ;
      this.clock = checkNotNull( clock ) ;
    }

    @Override
    protected void startScheduler() { }

    @Override
    protected void schedule( final long delayMilliseconds, final Runnable runnable ) { }

    @Override
    protected void stopScheduler() { }

    @Override
    public long currentTimeMillis() { return clock.currentTimeMillis() ; }

  }
}
