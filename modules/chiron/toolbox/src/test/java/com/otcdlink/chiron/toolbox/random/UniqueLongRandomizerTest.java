package com.otcdlink.chiron.toolbox.random;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.random.UniqueLongRandomizer.Monotonic;
import com.otcdlink.chiron.toolbox.random.UniqueLongRandomizer.Xoroshiro;
import com.otcdlink.chiron.toolbox.random.UniqueLongRandomizer.Xorshift;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.otcdlink.chiron.toolbox.random.UniqueLongRandomizer.MONOTONIC;
import static com.otcdlink.chiron.toolbox.random.UniqueLongRandomizer.Pseudorandom.nextPowerOfTwoMinus1;
import static com.otcdlink.chiron.toolbox.random.UniqueLongRandomizer.Xorshift.BEST_CEILING_OF_RANDOM;
import static com.otcdlink.chiron.toolbox.random.UniqueLongRandomizer.newBuilder;
import static org.assertj.core.api.Assertions.assertThat;

public class UniqueLongRandomizerTest {

  @Test
  public void monotonicDeprecated() {
    final Monotonic randomizer0 = new Monotonic( 0 ) ;
    assertThat( randomizer0.currentLongValue() ).isEqualTo( 0 ) ;
    final Monotonic randomizer1 = randomizer0.next() ;
    assertThat( randomizer1.currentLongValue() ).isEqualTo( 1 ) ;
    final Monotonic randomizer2 = randomizer1.next() ;
    assertThat( randomizer2.currentLongValue() ).isEqualTo( 2 ) ;

    // Equivalent to the code above.
    final ImmutableList< Long > generated = new Monotonic( 0 )
        .asLongStream()
        .limit( 3 )
        .boxed()
        .collect( ImmutableList.toImmutableList() )
    ;
    assertThat( generated ).containsExactly( 0L, 1L, 2L ) ;
  }

  @Test
  public void xoroshiro() {
    run( Xoroshiro::createNew, 1_000, 10, nextPowerOfTwoMinus1( 64_000 ), 1_000, true, true ) ;
  }

  @Test
  public void quickXoroshiroDemoWithDefaults() {
    final Xoroshiro xoroshiro = UniqueLongRandomizer.newBuilder()
        .floor( 1111 )
        .andReasonableDefaults()
        .build()
    ;
    xoroshiro.asRandomizerStream()
        .limit( 6 )
        .forEach( g -> LOGGER.info( "Generator is now " + g + "." ) )
    ;
  }

  @Test
  public void xorshift() {
    run( NEW_XORSHIFT, 1_000, 10, nextPowerOfTwoMinus1( 1000 ), 200_000, true, true ) ;
  }

  @Test
  public void createWithBuilder() {
    final Xoroshiro xoroshiro = newBuilder()
        .floor( 10 )
        .ceilingOfRandom( 1023 )
        .collisionThreshold( 100 )
        .seed( 0 )
        .build()
    ;
    assertThat( xoroshiro.floor ).isEqualTo( 10 ) ;
    assertThat( xoroshiro.ceilingOfRandom ).isEqualTo( 1023 ) ;
    assertThat( xoroshiro.collisionThreshold ).isEqualTo( 100 ) ;
  }

  @Test
  public void createWithBuilderAndDefaults() {
    final Xoroshiro xoroshiro = newBuilder()
        .floor( 10 )
        .andReasonableDefaults()
        .build()
    ;
    assertThat( xoroshiro.floor ).isEqualTo( 10 ) ;
    assertThat( xoroshiro.ceilingOfRandom ).isEqualTo( 524287 ) ;
    assertThat( xoroshiro.collisionThreshold ).isEqualTo( 1000 ) ;
  }

  @Test
  public void fromRandomToMonotonic() {
    final Xoroshiro randomizer = newBuilder()
        .floor( 1 )
        .ceilingOfRandom( 7 )
        .collisionThreshold( 100 )
        .build()
    ;
    ImmutableList< Long > randomValues = randomizer
        .asLongStream().limit( 10 ).boxed().collect( ImmutableList.toImmutableList() ) ;
    assertThat( randomValues ).containsExactly( 3L, 1L, 4L, 7L, 6L, 5L, 2L, 8L, 9L, 10L ) ;
  }

  @Test
  public void monotonicBySetup() {
    final Xoroshiro randomizer = newBuilder()
        .floor( 0 )
        .ceilingOfRandom( 0 )
        .collisionThreshold( 1 )
        .build()
    ;
    ImmutableList< Long > randomValues = randomizer
        .asLongStream().limit( 10 ).boxed().collect( ImmutableList.toImmutableList() ) ;
    assertThat( randomValues ).containsExactly( 0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L ) ;
  }

  @Test
  public void monotonicWithFloor() {
    final Xoroshiro randomizer = newBuilder()
        .floor( 127 )
        .ceilingOfRandom( 127 )
        .collisionThreshold( 1 )
        .build()
    ;
    ImmutableList< Long > randomValues = randomizer
        .asLongStream().limit( 8 ).boxed().collect( ImmutableList.toImmutableList() ) ;
    assertThat( randomValues ).containsExactly( 128L, 129L, 130L, 131L, 132L, 133L, 134L, 135L ) ;
  }

  @Test
  public void monotonicAsConstant() {
    final Xoroshiro randomizer = MONOTONIC ;
    ImmutableList< Long > randomValues = randomizer
        .asLongStream().limit( 10 ).boxed().collect( ImmutableList.toImmutableList() ) ;
    assertThat( randomValues ).containsExactly( 0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L ) ;
  }

  /**
   * Useful for tests asserting on {@link UniqueLongRandomizer#MONOTONIC},
   * expecting well-known values.
   */
  @Test
  public void monotonicContainsKnownState() {
    assertThat( MONOTONIC.floor ).isEqualTo( 0 ) ;
    assertThat( MONOTONIC.ceilingOfRandom ).isEqualTo( 0 ) ;
    assertThat( MONOTONIC.collisionThreshold ).isEqualTo( 1 ) ;
    assertThat( MONOTONIC.generation ).isEqualTo( 0 ) ;
    assertThat( MONOTONIC.pseudorandomIterationCount ).isEqualTo( 0 ) ;
    assertThat( MONOTONIC.currentLongValue() ).isEqualTo( 0 ) ;
    assertThat( MONOTONIC.state0 ).isEqualTo( -1 ) ;
    assertThat( MONOTONIC.state1 ).isEqualTo( -1 ) ;
    assertThat( MONOTONIC.reservationSize() ).isNull() ;
  }

  @Test
  public void seedMatters() {
    class FromSeed {
      private long generate( final long seed ) {
        return newBuilder()
            .floor( 10 )
            .ceilingOfRandom( 1023 )
            .collisionThreshold( 100 )
            .seed( seed )
            .build()
            .asLongStream()
            .limit( 1 )
            .reduce( ( left, right ) -> right )
            .getAsLong()
        ;
      }
    }
    long long0 = new FromSeed().generate( 0 ) ;
    long long1 = new FromSeed().generate( 1 ) ;
    assertThat( long0 ).isNotEqualTo( long1 ) ;
  }

  @Test
  public void recreateWithBuilder() {
    final Xoroshiro xoroshiro1 = newBuilder()
        .floor( 10 )
        .ceilingOfRandom( 1023 )
        .collisionThreshold( 100 )
        .build()
        .next().next().next()
    ;

    LOGGER.info( "Created xoroshiro1: " + xoroshiro1 + "."  );

    final Xoroshiro xoroshiro2 = newBuilder()
        .floor( xoroshiro1.floor )
        .ceilingOfRandom( xoroshiro1.ceilingOfRandom )
        .collisionThreshold( xoroshiro1.collisionThreshold )
        .currentGenerationStep( xoroshiro1.generation )
        .state0( xoroshiro1.state0 )
        .state1( xoroshiro1.state1 )
        .pseudorandomIterationCount( xoroshiro1.pseudorandomIterationCount )
        .currentLongValue( xoroshiro1.currentLongValue() )
        .addReservations( xoroshiro1.reservationsAsStream() )
        .build()
    ;

    LOGGER.info( "Created xoroshiro2: " + xoroshiro2 + "."  ) ;

    assertThat( xoroshiro1.currentLongValue() )
        .isEqualTo( xoroshiro2.currentLongValue() ) ;
    assertThat( xoroshiro1.next().currentLongValue() )
        .isEqualTo( xoroshiro2.next().currentLongValue() ) ;
    assertThat( xoroshiro1.next().next().currentLongValue() )
        .isEqualTo( xoroshiro2.next().next().currentLongValue() ) ;

    ImmutableList< Integer > reservationList = reservationsAsList( xoroshiro1 ) ;
    LOGGER.info( "Reserved: " + reservationList ) ;
    assertThat( reservationsAsList( xoroshiro1 ) ).hasSize( 4 ) ;
    assertThat( reservationsAsList( xoroshiro2 ) ).hasSize( 4 ) ;
  }

  @Test
  public void nextPowerOfTwoMinusOne() {
    assertThat( nextPowerOfTwoMinus1( 1 ) ).isEqualTo( 1 ) ;
    assertThat( nextPowerOfTwoMinus1( 2 ) ).isEqualTo( 3 ) ;
    assertThat( nextPowerOfTwoMinus1( 3 ) ).isEqualTo( 3 ) ;
    assertThat( nextPowerOfTwoMinus1( 5 ) ).isEqualTo( 7 ) ;
  }

  @Ignore( "May take a long" )
  @Test
  public void xoroshiroDemo3() {
    LOGGER.info( "Expect to hit ceiling of random around 64425th generation." ) ;
    run( Xoroshiro::createNew, 70_000,
        1111, nextPowerOfTwoMinus1(      32_000 ), 1_000, true, false ) ;
  }

  @Ignore( "May take a long" )
  @Test
  public void xoroshiroDemo4() {
    LOGGER.info( "Expect to hit ceiling of random around 128728th generation." ) ;
    run( Xoroshiro::createNew, 130_000,
        1111, nextPowerOfTwoMinus1(      64_000 ), 1000, true, false ) ;
  }

  @Ignore( "May take a long" )
  @Test
  public void xoroshiroDemo5() {
    LOGGER.info( "Expect to hit ceiling of random around 128728th generation." ) ;
    run( Xoroshiro::createNew, 250_000,
        1111, nextPowerOfTwoMinus1(     128_000 ), 1000, true, false ) ;
  }

  @Ignore( "May take a long" )
  @Test
  public void xoroshiroDemo6() {
    LOGGER.info( "Expect to hit ceiling of random around 524287th generation." ) ;
    run( Xoroshiro::createNew, 600_000,
        0,
        Xoroshiro.REASONABLE_CEILING_OF_RANDOM,
        Xoroshiro.REASONABLE_COLLISION_THRESHOLD,
        true,
        false
    ) ;
  }

  @Ignore( "May take a long" )
  @Test
  public void xoroshiroDemo2() {
    run( Xoroshiro::createNew, 100_000, 10, nextPowerOfTwoMinus1( 64_000 ), 1_000, true, true ) ;
  }

  @Ignore( "May take a long" )
  @Test
  public void xoroshiroDemo1() {
    run( Xoroshiro::createNew, 20_000,
        0,    BEST_CEILING_OF_RANDOM,              100_000, true, false ) ;
  }

  @Ignore( "May take a long" )
  @Test
  public void xorshiftDemo() {
    run( NEW_XORSHIFT, 1_000,
        1111, nextPowerOfTwoMinus1(      32_000 ), 100_000, false, true ) ;
  }


// =======
// Fixture
// =======

  private interface Initiator< RANDOMIZER
      extends UniqueLongRandomizer.Pseudorandom< RANDOMIZER > >
  {
    RANDOMIZER createNew(
        final int floor,
        final int ceilingOfRandom,
        final int collisionThreshold,
        final long seed
    ) ;
  }

  private static final Initiator< Xorshift > NEW_XORSHIFT =
      ( floor, ceilingOfRandom, collisionThreshold, seed ) ->
      Xorshift.createNew( floor, ceilingOfRandom, collisionThreshold )
  ;

  private static < RANDOMIZER extends UniqueLongRandomizer.Pseudorandom< RANDOMIZER > > void run(
      final Initiator< RANDOMIZER > initiator,
      final int generationCount,
      final int floor,
      final int ceilingOfRandom,
      final int collisionThreshold,
      final boolean logGeneratedValues,
      final boolean consistencyCheck
  ) {
    final Set< Long > generatedValueSet = new LinkedHashSet<>() ;
    RANDOMIZER keyRandomizer = initiator.createNew(
        floor,
        ceilingOfRandom,
        collisionThreshold,
        0
    ) ;
    boolean ceilingOfRandomReached = false ;
    Integer lastReservationSize = null ;
    String messageAboutCeilingHit = "" ;
    for( int i = 0 ; i < generationCount ; i ++ ) {
      final long keyValue = keyRandomizer.currentLongValue() ;
      if( keyValue > ceilingOfRandom && ! ceilingOfRandomReached ) {
        final String messageAboutPrevious ;
        if( lastReservationSize != null ) {
          messageAboutPrevious = "Last non-null reservation size was " + lastReservationSize + "." ;
        } else {
          messageAboutPrevious = "" ;
        }
        messageAboutCeilingHit =
            "Ceiling of random of " + keyRandomizer.ceilingOfRandom +
            " reached after " + keyRandomizer.generation + " generations, " +
            "with a collision threshold of " + keyRandomizer.collisionThreshold +
            ". " + messageAboutPrevious
        ;
        LOGGER.info( "Ceiling of random was hit!" ) ;
        ceilingOfRandomReached = true ;
      }
      if( logGeneratedValues ) {
        LOGGER.info( "Generation[" + i + "] => " + keyValue + " " +
            "(" + Long.toBinaryString( keyValue ) + ")." ) ;
      }
      if( consistencyCheck ) {
        assertThat( generatedValueSet ).doesNotContain( keyValue ) ;
        generatedValueSet.add( keyValue ) ;
      }
      final Integer reservationSize = keyRandomizer.reservationSize() ;
      if( reservationSize != null ) {
        lastReservationSize = reservationSize ;
      }
      keyRandomizer = keyRandomizer.next() ;
    }
    if( generationCount > 10 &&
        ceilingOfRandomReached &&
        ! messageAboutCeilingHit.isEmpty()
    ) {
      LOGGER.info( messageAboutCeilingHit ) ;
    }
    LOGGER.info( "Randomizer used a ceiling of random of " + keyRandomizer.ceilingOfRandom + ", " +
        "with reservation taking at least " + ( keyRandomizer.ceilingOfRandom / 8 ) + " bytes." ) ;

  }


  private static final Logger LOGGER = LoggerFactory.getLogger( UniqueLongRandomizerTest.class ) ;

  private static ImmutableList< Integer > reservationsAsList(
      final UniqueLongRandomizer.Pseudorandom pseudorandom
  ) {
    return pseudorandom.reservationsAsStream().boxed().collect( ImmutableList.toImmutableList() ) ;
  }


}