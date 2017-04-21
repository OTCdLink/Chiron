package io.github.otcdlink.chiron.toolbox.clock;

import org.joda.time.DateTime;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PulseToolsTest {

  @Test
  public void transition() throws Exception {
    transitioned( time( 1, 1, 0 ), time( 1, 1, 1 ), Pulse.Resolution.SECOND, true ) ;
    transitioned( time( 1, 1, 0 ), time( 1, 1, 0 ), Pulse.Resolution.SECOND, false ) ;
    transitioned( time( 1, 1, 0, 999 ), time( 1, 1, 1, 0 ), Pulse.Resolution.SECOND, true ) ;
    transitioned( time( 1, 1, 0, 999 ), time( 1, 1, 1, 0 ), Pulse.Resolution.DECISECOND, true ) ;

    transitioned(
        new DateTime( 2015, 12, 31, 23, 59, 59, 999 ),
        new DateTime( 2016, 1, 1, 0, 0, 0 ),
        Pulse.Resolution.SECOND,
        true
    ) ;

    transitioned(
        new DateTime( 2015, 12, 31, 23, 59, 59, 999 ),
        new DateTime( 2016, 1, 1, 0, 0, 0 ),
        Pulse.Resolution.DAY,
        true
    ) ;
  }

  @Test
  public void next() throws Exception {
    assertThat( PulseTools.next(
        new DateTime( 2015, 12, 31, 23, 59, 59, 999 ),
        Pulse.Resolution.SECOND
    ) ).isEqualTo( new DateTime( 2016, 1, 1, 0, 0, 0 ) ) ;

    assertThat( PulseTools.next(
        new DateTime( 2015, 1, 1, 1, 1, 0 ),
        Pulse.Resolution.SECOND
    ) ).isEqualTo( new DateTime( 2015, 1, 1, 1, 1, 1 ) ) ;

    assertThat( PulseTools.next(
        new DateTime( 2015, 12, 31, 23, 59, 59, 999 ),
        Pulse.Resolution.DAY
    ) ).isEqualTo( new DateTime( 2016, 1, 1, 0, 0, 0 ) ) ;

    assertThat( PulseTools.next(
        time( 1, 1, 1, 50 ),
        Pulse.Resolution.DECISECOND
    ) ).isEqualTo( time( 1, 1, 1, 100 ) ) ;
  }

  @Test
  public void mostRecent() throws Exception {
    final DateTime one = new DateTime( 1 ) ;
    final DateTime two = new DateTime( 2 ) ;
    assertThat( PulseTools.mostRecent( null, null ) ).isNull() ;
    assertThat( PulseTools.mostRecent( one, null ) ).isEqualTo( one ) ;
    assertThat( PulseTools.mostRecent( null, one ) ).isEqualTo( one ) ;
    assertThat( PulseTools.mostRecent( one, one ) ).isEqualTo( one ) ;
    assertThat( PulseTools.mostRecent( one, two ) ).isEqualTo( two ) ;
    assertThat( PulseTools.mostRecent( two, one ) ).isEqualTo( two ) ;
  }


  // =======
// Fixture
// =======

  private static DateTime time( final int hours, final int minutes, final int seconds ) {
    return new DateTime( 2015, 1, 1, hours, minutes, seconds ) ;
  }

  private static DateTime time(
      final int hours,
      final int minutes,
      final int seconds,
      final int milliseconds
  ) {
    return new DateTime( 2015, 1, 1, hours, minutes, seconds, milliseconds ) ;
  }

  private static void transitioned(
      final DateTime previous,
      final DateTime now,
      final Pulse.Resolution resolution,
      final boolean transition
  ) {
    assertThat( PulseTools.transitioned( previous, now, resolution ) ).isEqualTo( transition ) ;

  }

}