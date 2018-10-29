package com.otcdlink.chiron.upend.session;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.middle.session.SignonSetback;
import com.otcdlink.chiron.middle.session.SignonSetback.Factor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FailedSignonAttempt}.
 */
class SignonSetbackTest {

  @Test
  void initial() {
    assertThat( Factor.values() ).containsOnly(
        Factor.PRIMARY, Factor.SECONDARY ) ;
    assertThat( SignonSetback.NONE.isEmpty() ).isTrue() ;
    assertThat( SignonSetback.NONE.toMap() ).isEmpty() ;
    assertThat( SignonSetback.NONE.limitReached( 1 ) ).isFalse() ;
    assertThat( SignonSetback.NONE.limitReached( 2 ) ).isFalse() ;
  }

  @Test
  void incrementPrimary() {
    final SignonSetback _1_0 = SignonSetback.NONE ;
    assertThat( _1_0.limitReached( 1 ) ).isFalse() ;
    final SignonSetback _2_0 = _1_0.increment( Factor.PRIMARY ) ;
    assertThat( _2_0.limitReached( 1 ) ).isTrue() ;
  }

  @Test
  void incrementSecondary() {
    final SignonSetback _1_0 = SignonSetback.NONE ;
    final SignonSetback _1_1 = _1_0.increment( Factor.SECONDARY ) ;
    assertThat( _1_1.limitReached( 2 ) ).isFalse() ;
    final SignonSetback _1_2 = _1_1.increment( Factor.SECONDARY ) ;
    assertThat( _1_2.limitReached( 2 ) ).isTrue() ;
  }

  @Test
  void startWithSecondary() {
    final SignonSetback _0_1 = SignonSetback.NONE ;
    assertThat( _0_1.limitReached( 1 ) ).isFalse() ;
    final SignonSetback _0_2 = _0_1.increment( Factor.SECONDARY ) ;
    assertThat( _0_2.limitReached( 1 ) ).isTrue() ;
  }

  @Test
  void incrementingSecondaryResetsPrimary() {
    final SignonSetback _1_0 = SignonSetback.NONE ;
    final SignonSetback _2_0 = _1_0.increment( Factor.PRIMARY ) ;
    final SignonSetback _0_1 = _2_0.increment( Factor.SECONDARY ) ;
    final SignonSetback _1_1 = _0_1.increment( Factor.PRIMARY ) ;
    assertThat( _1_1.limitReached( 2 ) ).isFalse() ;
  }

  @Test
  void increment() {
    assertThat( increment( Factor.PRIMARY ) ).isEqualTo( mapOf( 1, 0 ) ) ;
    assertThat( increment( Factor.PRIMARY, Factor.PRIMARY ) ).isEqualTo( mapOf( 2, 0 ) ) ;
    assertThat( increment( Factor.SECONDARY ) ).isEqualTo( mapOf( 0, 1 ) ) ;
    assertThat( increment( Factor.SECONDARY, Factor.SECONDARY ) ).isEqualTo( mapOf( 0, 2 ) ) ;
    assertThat( increment( Factor.PRIMARY, Factor.PRIMARY, Factor.SECONDARY ) )
        .isEqualTo( mapOf( 0, 1 ) ) ;
    assertThat( increment( Factor.PRIMARY, Factor.SECONDARY, Factor.PRIMARY ) )
        .isEqualTo( mapOf( 1, 1 ) ) ;
  }

  @Test
  void fromMap() {
    assertThat( SignonSetback.fromMap( mapOf( 0, 0 ) ).isEmpty() ).isTrue() ;
    verifyMapConversion( mapOf( 1, 0 ) ) ;
    verifyMapConversion( mapOf( 1, 1 ) ) ;
    verifyMapConversion( mapOf( 1, 2 ) ) ;
    verifyMapConversion( mapOf( 0, 2 ) ) ;
  }


// =======
// Fixture
// =======

  private static ImmutableMap< Factor, Integer > increment(
      final Factor... factors
  ) {
    SignonSetback signonSetback = SignonSetback.NONE ;
    for( final Factor factor : factors ) {
      signonSetback = signonSetback.increment( factor ) ;
    }
    return signonSetback.toMap() ;
  }

  private static ImmutableMap< Factor, Integer > mapOf(
      final int primary,
      final int secondary
  ) {
    return ImmutableMap.of(
        Factor.PRIMARY, primary,
        Factor.SECONDARY, secondary
    ) ;
  }

  private void verifyMapConversion( ImmutableMap< Factor, Integer > map ) {
    assertThat( SignonSetback.fromMap( map ).toMap() ).isEqualTo( map ) ;
  }

}
