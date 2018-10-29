package com.otcdlink.chiron.upend.session;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.middle.session.SignonSetback;
import org.fest.reflect.core.Reflection;
import org.fest.reflect.reference.TypeRef;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FailedSignonAttempt}.
 */
public class FailedSignonAttemptTest {

  @Test
  public void initial() throws Exception {
    assertThat( SignonSetback.Factor.values() ).containsOnly( SignonSetback.Factor.PRIMARY, SignonSetback.Factor.SECONDARY ) ;

    final FailedSignonAttempt failedSignonAttempt = FailedSignonAttempt.create( 2 ) ;
    assertThat( failedSignonAttempt.hasReachedLimit() ).isFalse() ;
  }

  @Test
  public void incrementPrimary() throws Exception {
    final FailedSignonAttempt _1_0 = FailedSignonAttempt.create( 2 ) ;
    assertThat( _1_0.hasReachedLimit() ).isFalse() ;
    final FailedSignonAttempt _2_0 = _1_0.increment( SignonSetback.Factor.PRIMARY ) ;
    assertThat( _2_0.hasReachedLimit() ).isTrue() ;
  }

  @Test
  public void incrementSecondary() throws Exception {
    final FailedSignonAttempt _1_0 = FailedSignonAttempt.create( 2 ) ;
    final FailedSignonAttempt _1_1 = _1_0.increment( SignonSetback.Factor.SECONDARY ) ;
    assertThat( _1_1.hasReachedLimit() ).isFalse() ;
    final FailedSignonAttempt _1_2 = _1_1.increment( SignonSetback.Factor.SECONDARY ) ;
    assertThat( _1_2.hasReachedLimit() ).isTrue() ;
  }

  @Test
  public void startWithSecondary() throws Exception {
    final FailedSignonAttempt _0_1 = FailedSignonAttempt.create( SignonSetback.Factor.SECONDARY, 2 ) ;
    assertThat( _0_1.hasReachedLimit() ).isFalse() ;
    final FailedSignonAttempt _0_2 = _0_1.increment( SignonSetback.Factor.SECONDARY ) ;
    assertThat( _0_2.hasReachedLimit() ).isTrue() ;
  }

  @Test
  public void incrementingSecondaryResetsPrimary() throws Exception {
    final FailedSignonAttempt _1_0 = FailedSignonAttempt.create( 2 ) ;
    final FailedSignonAttempt _2_0 = _1_0.increment( SignonSetback.Factor.PRIMARY ) ;
    final FailedSignonAttempt _0_1 = _2_0.increment( SignonSetback.Factor.SECONDARY ) ;
    final FailedSignonAttempt _1_1 = _0_1.increment( SignonSetback.Factor.PRIMARY ) ;
    assertThat( _1_1.hasReachedLimit() ).isFalse() ;
  }

  @Test
  public void increment() throws Exception {
    assertThat( increment( mapOf( 0, 0 ), SignonSetback.Factor.PRIMARY ) ).isEqualTo( mapOf( 1, 0 ) ) ;
    assertThat( increment( mapOf( 1, 0 ), SignonSetback.Factor.PRIMARY ) ).isEqualTo( mapOf( 2, 0 ) ) ;
    assertThat( increment( mapOf( 0, 0 ), SignonSetback.Factor.SECONDARY ) ).isEqualTo( mapOf( 0, 1 ) ) ;
    assertThat( increment( mapOf( 0, 1 ), SignonSetback.Factor.SECONDARY ) ).isEqualTo( mapOf( 0, 2 ) ) ;
    assertThat( increment( mapOf( 1, 1 ), SignonSetback.Factor.PRIMARY ) ).isEqualTo( mapOf( 2, 1 ) ) ;
    assertThat( increment( mapOf( 1, 0 ), SignonSetback.Factor.SECONDARY ) ).isEqualTo( mapOf( 0, 1 ) ) ;
  }

// =======
// Fixture
// =======

  private static ImmutableMap<SignonSetback.Factor, Integer > increment(
      final ImmutableMap<SignonSetback.Factor, Integer > counters,
      final SignonSetback.Factor signonAttempt
  ) {
    return Reflection
        .staticMethod( "resetAndIncrement" )
        .withReturnType( new TypeRef< ImmutableMap<SignonSetback.Factor, Integer > >() { } )
        .withParameterTypes( ImmutableMap.class, SignonSetback.Factor.class )
        .in( FailedSignonAttempt.class )
        .invoke( counters, signonAttempt )
    ;
  }

  private static ImmutableMap<SignonSetback.Factor, Integer > mapOf(
      final int primary,
      final int secondary
  ) {
    return ImmutableMap.of(
        SignonSetback.Factor.PRIMARY, primary,
        SignonSetback.Factor.SECONDARY, secondary
    ) ;
  }

}
