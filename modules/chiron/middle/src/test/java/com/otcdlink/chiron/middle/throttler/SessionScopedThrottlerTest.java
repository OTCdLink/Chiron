package com.otcdlink.chiron.middle.throttler;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.middle.throttler.SessionScopedThrottler.Restriction;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.otcdlink.chiron.middle.throttler.SessionScopedThrottler.Throttling.NOT_APPLICABLE;
import static com.otcdlink.chiron.middle.throttler.SessionScopedThrottler.Throttling.PASSED;
import static com.otcdlink.chiron.middle.throttler.SessionScopedThrottler.Throttling.THROTTLED;
import static org.assertj.core.api.Assertions.assertThat;

public class SessionScopedThrottlerTest {

  @Test
  public void unthrottled() throws Exception {
    throttler.throttlingDuration( ThrottlerFixture.DURATION_2 ) ;

    assertOn( ThrottlerFixture.TIMESTAMP_0, ( short ) 1, NOT_APPLICABLE,
        ThrottlerFixture.noRestriction(), ThrottlerFixture.noRestriction() ) ;

    assertOn( ThrottlerFixture.TIMESTAMP_1, ( short ) 1, NOT_APPLICABLE,
        ThrottlerFixture.noRestriction(), ThrottlerFixture.noRestriction() ) ;
  }

  @Test
  public void throttled() throws Exception {
    throttler.throttlingDuration( ThrottlerFixture.DURATION_2 ) ;

    assertOn( ThrottlerFixture.TIMESTAMP_0, 1.1f, PASSED,
        ThrottlerFixture.restrictions( 1.1f ), ThrottlerFixture.noRestriction() ) ;

    assertOn( ThrottlerFixture.TIMESTAMP_1, 1.1f, THROTTLED,
        ThrottlerFixture.restrictions( 1.1f ), ThrottlerFixture.noRestriction() ) ;
  }

  @Test
  public void expiration1() throws Exception {
    throttler.throttlingDuration( ThrottlerFixture.DURATION_2 ) ;

    assertOn( ThrottlerFixture.TIMESTAMP_0, 1.1f, PASSED,
        ThrottlerFixture.restrictions( 1.1f ), ThrottlerFixture.noRestriction() ) ;

    assertOn( ThrottlerFixture.TIMESTAMP_3, 1.2f, PASSED,
        ThrottlerFixture.restrictions( 1.1f, 1.2f ), ThrottlerFixture.restrictions( 1.1f ) ) ;
  }

  @Test
  public void expiration2() throws Exception {
    throttler.throttlingDuration( ThrottlerFixture.DURATION_2 ) ;

    assertOn( ThrottlerFixture.TIMESTAMP_0, 1.1f, PASSED,
        ThrottlerFixture.restrictions( 1.1f ), ThrottlerFixture.noRestriction() ) ;

    assertOn( ThrottlerFixture.TIMESTAMP_3, ( short ) 1, NOT_APPLICABLE,
        ThrottlerFixture.restrictions( 1.1f ), ThrottlerFixture.restrictions( 1.1f ) ) ;
  }

  @Test
  public void globbing() throws Exception {
    throttler.throttlingDuration( ThrottlerFixture.DURATION_2 ) ;

    assertOn( ThrottlerFixture.TIMESTAMP_0, 1, PASSED,
        ThrottlerFixture.restrictions( 1 ), ThrottlerFixture.noRestriction() ) ;
    /** Previous {@link Restriction} applies so new one not applied. */

    assertOn( ThrottlerFixture.TIMESTAMP_1, 1.1f, THROTTLED,
        ThrottlerFixture.restrictions( 1 ), ThrottlerFixture.noRestriction() ) ;
  }



// =======
// Fixture
// =======

  private final UpdateableClock updateableClock = UpdateableClock.newClock( 0 ) ;

  private final NotificationRecordingThrottler<
        Number,
      ThrottlerFixture.NumberRestriction< Number >
    > throttler = new NotificationRecordingThrottler<>(
      new ThrottlerFixture.NumberRestrictionFactory() ) ;

  private void assertOn(
      final DateTime timestamp,
      final Number command,
      final SessionScopedThrottler.Throttling expected,
      final ImmutableList<ThrottlerFixture.NumberRestriction> expectedAddedRestrictions,
      final ImmutableList<ThrottlerFixture.NumberRestriction> expectedRemovedRestrictions
  ) {
    updateableClock.set( timestamp.getMillis() ) ;
    assertThat( throttler.evaluateAndUpdate( command ) ).isEqualTo( expected ) ;
    assertThat( throttler.restrictionsAdded ).isEqualTo( expectedAddedRestrictions ) ;
    assertThat( throttler.restrictionsRemoved ).isEqualTo( expectedRemovedRestrictions ) ;
  }

  private class NotificationRecordingThrottler<
      COMMAND,
      RESTRICTION extends Restriction< COMMAND >
  > extends SessionScopedThrottler< COMMAND, RESTRICTION >
  {
    public List< RESTRICTION > restrictionsAdded = new ArrayList<>() ;
    public List< RESTRICTION > restrictionsRemoved = new ArrayList<>() ;

    public NotificationRecordingThrottler(
        final RestrictionFactory< COMMAND, RESTRICTION > restrictionFactory
    ) {
      super( SessionScopedThrottlerTest.this.updateableClock, restrictionFactory, Duration.ZERO ) ;
    }

    @Override
    protected void restrictionAdded( final RESTRICTION restriction ) {
      restrictionsAdded.add( restriction ) ;
    }

    @Override
    protected void restrictionRemoved( final RESTRICTION restriction ) {
      restrictionsRemoved.add( restriction ) ;
    }
  }

}