package io.github.otcdlink.chiron.toolbox.clock;


import mockit.Expectations;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class PulseModulatorTest {

  @Test
  public void incrementTriggersTick( @Injectable final Pulse.Tickee tickee ) {
    final PulseModulator pulseModulator = newPulseModulator( DAY_0_END ) ;
    startNewPulse( pulseModulator, tickee ) ;

    new StrictExpectations() { {
      tickee.tick( DAY_1_START ) ;
      times = 1 ;
    } } ;

    pulseModulator.increment() ;
    pulseModulator.increment() ;
  }

  /**
   * This test is not {@link PulseModulator}-specific, it applies to
   * {@link Pulse.WithExecutorService} but it is easier to test with instrumented clock.
   */
  @Test
  public void startWithNullTriggersTick( @Injectable final Pulse.Tickee tickee ) {
    final PulseModulator pulseModulator = newPulseModulator( DAY_1_START ) ;
    final Pulse pulse = newPulse( pulseModulator, tickee ) ;

    new StrictExpectations() { {
      tickee.tick( DAY_1_START ) ;
      times = 1 ;
    } } ;
    pulse.start( null ) ;
  }

  /**
   * This test is not {@link PulseModulator}-specific, it applies to
   * {@link Pulse.WithExecutorService} but it is easier to test with instrumented clock.
   */
  @Test
  public void startWithNonNullTriggersTick( @Injectable final Pulse.Tickee tickee ) {
    final PulseModulator pulseModulator = newPulseModulator( DAY_1_START ) ;
    final Pulse pulse = newPulse( pulseModulator, tickee ) ;

    new StrictExpectations() { {
      tickee.tick( DAY_1_START ) ;
    } } ;
    pulse.start( DAY_0_END ) ;
  }

  /**
   * This test is not {@link PulseModulator}-specific, it applies to
   * {@link Pulse.WithExecutorService} but it is easier to test with instrumented clock.
   */
  @Test
  public void startWithNonNullTriggersNoTick( @Injectable final Pulse.Tickee tickee ) {
    final PulseModulator pulseModulator = newPulseModulator( DAY_1_START ) ;
    final Pulse pulse = newPulse( pulseModulator, tickee ) ;
    pulse.start( DAY_1_START ) ;
  }

  @Test
  public void incrementNoTick( @Injectable final Pulse.Tickee tickee ) {
    final PulseModulator pulseModulator = newPulseModulator( DAY_0_START ) ;
    startNewPulse( pulseModulator, tickee ) ;

    final long differenceWithinDay = DAY_0_END.getMillis() - DAY_0_START.getMillis() ;

    new StrictExpectations() { { } } ;

    pulseModulator.increment( differenceWithinDay ) ;
  }

  @Test
  public void incrementNoTickThenTick( @Injectable final Pulse.Tickee tickee ) {
    final PulseModulator pulseModulator = newPulseModulator( DAY_0_START ) ;
    startNewPulse( pulseModulator, tickee ) ;

    final long differenceWithinDay = DAY_0_END.getMillis() - DAY_0_START.getMillis() ;

    new StrictExpectations() { { } } ;

    pulseModulator.increment( differenceWithinDay ) ;

    new Expectations() { {
      tickee.tick( DAY_1_START ) ;
    } } ;

    pulseModulator.increment() ;

  }

  @Test
  public void startWithPreviousInstant( @Injectable final Pulse.Tickee tickee ) {
    final PulseModulator pulseModulator = newPulseModulator( DAY_1_START ) ;
    final Pulse pulse = newPulse( pulseModulator, tickee ) ;

    new Expectations() { {
      tickee.tick( DAY_1_START ) ;
    } } ;
    pulse.start( DAY_0_END ) ;

  }


// =======
// Fixture
// =======

  private static final DateTime DAY_0_START =
      new DateTime( 2000, 12, 31, 0, 0, 0, 0, DateTimeZone.UTC ) ;

  private static final DateTime DAY_0_END =
      new DateTime( 2000, 12, 31, 23, 59, 59, 999, DateTimeZone.UTC ) ;

  private static final DateTime DAY_1_START =
      new DateTime( 2001, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC ) ;

  private static void startNewPulse(
      final PulseModulator pulseModulator,
      final Pulse.Tickee tickee
  ) {
    final Pulse pulse = newPulse( pulseModulator, tickee ) ;
    pulse.start() ;
  }

  private static Pulse newPulse( final PulseModulator pulseModulator, final Pulse.Tickee tickee ) {
    return pulseModulator.create( Pulse.Resolution.DAY, tickee ) ;
  }

  private static PulseModulator newPulseModulator( final DateTime start ) {
    return new PulseModulator( start.getMillis() ) ;
  }

}