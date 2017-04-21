package io.github.otcdlink.chiron.upend.session;

import com.google.common.collect.ImmutableMap;
import org.fest.reflect.core.Reflection;
import org.fest.reflect.reference.TypeRef;
import org.junit.Test;

import static io.github.otcdlink.chiron.upend.session.SignonAttempt.PRIMARY;
import static io.github.otcdlink.chiron.upend.session.SignonAttempt.SECONDARY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FailedSignonAttempt}.
 */
public class FailedSignonAttemptTest {

  @Test
  public void initial() throws Exception {
    assertThat( SignonAttempt.values() ).containsOnly( PRIMARY, SECONDARY ) ;

    final FailedSignonAttempt failedSignonAttempt = FailedSignonAttempt.create( 2 ) ;
    assertThat( failedSignonAttempt.hasReachedLimit() ).isFalse() ;
  }

  @Test
  public void incrementPrimary() throws Exception {
    final FailedSignonAttempt _1_0 = FailedSignonAttempt.create( 2 ) ;
    assertThat( _1_0.hasReachedLimit() ).isFalse() ;
    final FailedSignonAttempt _2_0 = _1_0.increment( PRIMARY ) ;
    assertThat( _2_0.hasReachedLimit() ).isTrue() ;
  }

  @Test
  public void incrementSecondary() throws Exception {
    final FailedSignonAttempt _1_0 = FailedSignonAttempt.create( 2 ) ;
    final FailedSignonAttempt _1_1 = _1_0.increment( SECONDARY ) ;
    assertThat( _1_1.hasReachedLimit() ).isFalse() ;
    final FailedSignonAttempt _1_2 = _1_1.increment( SECONDARY ) ;
    assertThat( _1_2.hasReachedLimit() ).isTrue() ;
  }

  @Test
  public void startWithSecondary() throws Exception {
    final FailedSignonAttempt _0_1 = FailedSignonAttempt.create( SECONDARY, 2 ) ;
    assertThat( _0_1.hasReachedLimit() ).isFalse() ;
    final FailedSignonAttempt _0_2 = _0_1.increment( SECONDARY ) ;
    assertThat( _0_2.hasReachedLimit() ).isTrue() ;
  }

  @Test
  public void incrementingSecondaryResetsPrimary() throws Exception {
    final FailedSignonAttempt _1_0 = FailedSignonAttempt.create( 2 ) ;
    final FailedSignonAttempt _2_0 = _1_0.increment( PRIMARY ) ;
    final FailedSignonAttempt _0_1 = _2_0.increment( SECONDARY ) ;
    final FailedSignonAttempt _1_1 = _0_1.increment( PRIMARY ) ;
    assertThat( _1_1.hasReachedLimit() ).isFalse() ;
  }

  @Test
  public void increment() throws Exception {
    assertThat( increment( mapOf( 0, 0 ), PRIMARY ) ).isEqualTo( mapOf( 1, 0 ) ) ;
    assertThat( increment( mapOf( 1, 0 ), PRIMARY ) ).isEqualTo( mapOf( 2, 0 ) ) ;
    assertThat( increment( mapOf( 0, 0 ), SECONDARY ) ).isEqualTo( mapOf( 0, 1 ) ) ;
    assertThat( increment( mapOf( 0, 1 ), SECONDARY ) ).isEqualTo( mapOf( 0, 2 ) ) ;
    assertThat( increment( mapOf( 1, 1 ), PRIMARY ) ).isEqualTo( mapOf( 2, 1 ) ) ;
    assertThat( increment( mapOf( 1, 0 ), SECONDARY ) ).isEqualTo( mapOf( 0, 1 ) ) ;
  }

// =======
// Fixture
// =======

  private static ImmutableMap<SignonAttempt, Integer > increment(
      final ImmutableMap<SignonAttempt, Integer > counters,
      final SignonAttempt signonAttempt
  ) {
    return Reflection
        .staticMethod( "resetAndIncrement" )
        .withReturnType( new TypeRef< ImmutableMap<SignonAttempt, Integer > >() { } )
        .withParameterTypes( ImmutableMap.class, SignonAttempt.class )
        .in( FailedSignonAttempt.class )
        .invoke( counters, signonAttempt )
    ;
  }

  private static ImmutableMap<SignonAttempt, Integer > mapOf(
      final int primary,
      final int secondary
  ) {
    return ImmutableMap.of(
        PRIMARY, primary,
        SECONDARY, secondary
    ) ;
  }

}
