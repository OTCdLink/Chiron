package com.otcdlink.chiron.toolbox.random;

import com.otcdlink.chiron.toolbox.ToStringTools;

import java.util.BitSet;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Generates a sequence of pseudo-random positive numbers, with number appearing only once
 * (within the [0-{@value Long#MAX_VALUE}] range), and this sequence being repeatable
 * with another {@link UniqueLongRandomizer} built with exactly the same parameters.
 * Those parameters are publicly accessible.
 *
 * <h1>Immutability</h1>
 * Immutability is the outstanding feature of the {@link UniqueLongRandomizer}.
 * Each {@link UniqueLongRandomizer} instance <em>must</em> be immutable. It holds its own
 * random value. To obtain the next value, call {@link #next()} and then
 * {@link UniqueLongRandomizer#currentLongValue()} from the fresh instance.
 *
 */
public interface UniqueLongRandomizer< RANDOMIZER extends UniqueLongRandomizer > {

  default  < KEY > KEY value( final LongFunction< KEY > keyInstantiator ) {
    return keyInstantiator.apply( this.currentLongValue() ) ;
  }

  long currentLongValue() ;

  RANDOMIZER next() ;

  @SuppressWarnings( "unchecked" )
  default Stream< RANDOMIZER > asRandomizerStream() {
    return Stream.iterate( ( RANDOMIZER ) this, randomizer -> ( RANDOMIZER ) randomizer.next() ) ;
  }

  default LongStream asLongStream() {
    return asRandomizerStream().mapToLong( UniqueLongRandomizer::currentLongValue ) ;
  }


// =======
// Builder
// =======

  static Builder.FloorStep newBuilder() {
    class Mixin
        implements Builder.FloorStep, Builder.CeilingStep, Builder.CollisionThresholdStep,
        Builder.CurrentGenerationStep, Builder.State0Step, Builder.State1Step,
        Builder.PseudorandomIterationCountStep, Builder.NextMonotonicValueStep,
        Builder.ReservationStep, Builder.BuildStep
    {
      private boolean exported = false ;
      private int floor ;
      private int ceilingOfRandom ;
      private int collisionThreshold ;
      private int generation ;
      private long seed = 0 ;
      private long state0 ;
      private long state1 ;
      private int pseudorandomIterationCount ;
      private long currentLongValue ;
      private BitSet reservation ;

      @Override
      public Builder.CeilingStep floor( final int floor ) {
        this.floor = floor ;
        return this ;
      }

      @Override
      public Builder.CurrentGenerationStep andReasonableDefaults() {
        return ceilingOfRandom( Xoroshiro.REASONABLE_CEILING_OF_RANDOM )
            .collisionThreshold( Xoroshiro.REASONABLE_COLLISION_THRESHOLD )
            ;
      }

      @Override
      public Builder.CollisionThresholdStep ceilingOfRandom( final int ceiling ) {
        this.ceilingOfRandom = ceiling ;
        return this ;
      }

      @Override
      public Builder.CurrentGenerationStep collisionThreshold(
          final int collisionThreshold
      ) {
        this.collisionThreshold = collisionThreshold ;
        return this ;
      }

      @Override
      public Builder.State0Step currentGenerationStep(
          final int currentGeneration
      ) {
        this.exported = true ;
        this.generation = currentGeneration ;
        return this ;
      }

      @Override
      public Builder.BuildStep seed( long seed ) {
        this.seed = seed ;
        return this ;
      }

      @Override
      public Builder.State1Step state0( final long state0 ) {
        this.state0 = state0 ;
        return this ;
      }

      @Override
      public Builder.PseudorandomIterationCountStep state1( final long state1 ) {
        this.state1 = state1 ;
        return this ;
      }

      @Override
      public Builder.NextMonotonicValueStep pseudorandomIterationCount( final int count ) {
        this.pseudorandomIterationCount = count ;
        return this ;
      }

      @Override
      public Builder.ReservationStep currentLongValue( final long current ) {
        this.currentLongValue = current;
        return this ;
      }
      @Override
      public Builder.ReservationStep addReservation( final int key ) {
        if( reservation == null ) {
          reservation = new BitSet() ;
        }
        reservation.set( key ) ;
        return this ;
      }

      @Override
      public UniqueLongRandomizer.Xoroshiro build() {
        if( exported ) {
          return new Xoroshiro(
            true,
            floor,
            ceilingOfRandom,
            collisionThreshold,
            generation,
            state0,
            state1,
            pseudorandomIterationCount,
            reservation,
            currentLongValue
          ) ;
        } else {
          return Xoroshiro.createNew( floor, ceilingOfRandom, collisionThreshold, seed ) ;
        }
      }
    }

    return new Mixin() ;
  }

  interface Builder {

    interface FloorStep {
      CeilingStep floor( int floor ) ;
    }

    interface CeilingStep {
      CurrentGenerationStep andReasonableDefaults() ;
      CollisionThresholdStep ceilingOfRandom( int ceiling ) ;
    }

    interface CollisionThresholdStep {
      CurrentGenerationStep collisionThreshold( int collisionThreshold ) ;
    }

    interface CurrentGenerationStep extends BuildStep, SeedStep {
      State0Step currentGenerationStep( int currentGeneration ) ;
    }

    interface SeedStep {
      BuildStep seed( long seed ) ;
    }

    interface State0Step extends BuildStep {
      State1Step state0( long state0 ) ;
    }

    interface State1Step extends BuildStep {
      PseudorandomIterationCountStep state1( long state1 ) ;

    }

    interface PseudorandomIterationCountStep {
      NextMonotonicValueStep pseudorandomIterationCount( int count ) ;
    }

    interface NextMonotonicValueStep {
      ReservationStep currentLongValue( long current ) ;
    }

    interface ReservationStep extends BuildStep {
      ReservationStep addReservation( int key ) ;
      default ReservationStep addReservations( final IntStream intStream ) {
        if( intStream != null ) {
          intStream.forEach( this::addReservation ) ;
        }
        return this ;
      }
    }

    interface BuildStep {
      UniqueLongRandomizer.Xoroshiro build() ;
    }

  }


// ========
// Abstract
// ========

  abstract class AbstractUniqueLongRandomizer< RANDOMIZER extends AbstractUniqueLongRandomizer >
      implements UniqueLongRandomizer< RANDOMIZER >
  {
    /**
     * How many times (zero-based) we generated a new value (first instantiation is generation
     * zero).
     */
    public final int generation ;

    public final int floor ;

    public final int ceilingOfRandom ;

    protected AbstractUniqueLongRandomizer(
        final int floor,
        final int ceilingOfRandom,
        final int generation
    ) {
      checkArgument( floor >= 0 ) ;
      this.floor = floor ;
      checkArgument( ceilingOfRandom >= floor ) ;
      this.ceilingOfRandom = ceilingOfRandom ;
      this.generation = generation ;
    }

  }


// =========
// Monotonic
// =========

  /**
   * Only for tests.
   * We don't create this field with a {@link Builder}, which brings intricate values
   * for {@link Pseudorandom#state0} and {@link Pseudorandom#state1} because of seed derivation.
   */
  UniqueLongRandomizer.Xoroshiro MONOTONIC = new Xoroshiro(
      true,
      0,
      0,
      1,
      0,
      -1,
      -1,
      0,
      null,
      0
  ) ;

  /**
   * This class only shows how to derive {@link AbstractUniqueLongRandomizer} to plug
   * other behaviors.
   *
   * @see UniqueLongRandomizer#MONOTONIC
   */
  class Monotonic extends AbstractUniqueLongRandomizer< Monotonic > {

    public Monotonic( int floor ) {
      this( floor, floor + 1, 0 ) ;
    }

    private Monotonic( int floor, int ceilingOfRandom, int iterationStep ) {
      super( floor, ceilingOfRandom, iterationStep ) ;
    }

    @Override
    public long currentLongValue() {
      return floor + generation ;
    }

    @Override
    public Monotonic next() {
      return new Monotonic( floor, ceilingOfRandom, generation + 1 ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" + generation + "}" ;
    }
  }


// ============
// Pseudorandom
// ============

  /**
   * Base class for generating pseudrandom numbers in a deterministic way.
   * First generated numbers are random within the [{@link #floor}...{@link #ceilingOfRandom}]
   * interval, then they grow monotonically. The interval of random values may not be entierely
   * filled before switching to the monotonic growth. The main invariant of this class is that,
   * given initial values ({@link #floor}, {@link #ceilingOfRandom}, {@link #generation}, and other
   * subclass-specific values like a random seed), the generated sequence will always be the same.
   *
   * <h1>State externalisation</h1>
   * <p>
   * There is no such thing as a "State" or "Configuration" object because a {@link Pseudorandom}
   * (practically: a {@link Xoroshiro}) object makes all its state publicly visible.
   *
   * <h1>Algorithm</h1>
   * <p>
   * This class is designed around {@link Xoroshiro}, which should be the default for production.
   * Subclassing is for testing other PRNG algorithms.
   *
   * <h1>Performance</h1>
   * <p>
   * This class is designed around {@link Xoroshiro}, which should be the default for production.
   * Subclassing is for testing other PRNG algorithms.
   * <p>
   * Xoroshiro algorithme implementation may not be as fast as promised in the paper, because
   * instructions in {@link #deriveResult(int, long, long)}, {@link #deriveState0(int, long, long)},
   * and {@link #deriveState1(int, long, long)} may not be inlined/pipelined as in original
   * implementation.
   *
   */
  abstract class Pseudorandom< RANDOMIZER extends Pseudorandom< RANDOMIZER > >
      extends UniqueLongRandomizer.AbstractUniqueLongRandomizer< RANDOMIZER >
  {

    protected interface Instantiator< RANDOMIZER extends Pseudorandom > {
      RANDOMIZER newInstance(
          boolean importing,
          int floor,
          int ceilingOfRandom,
          int collisionThreshold,
          int generation,
          long state0,
          long state1,
          int pseudorandomIterationCount,
          BitSet alreadyGenerated,
          long nextMonotonicValue
      ) ;
    }

    /**
     * Refers to Xoroshiro's original Java implementation.
     */
    public final long state0 ;

    /**
     * Refers to Xoroshiro's original Java implementation.
     */
    public final long state1 ;

    /**
     * If null, means we have exhausted the random interval for switching to monotonic generation.
     */
    private final BitSet reservation ;

    /**
     * A collision means the masked hash of {@link #generation} appears in
     * {@link #reservation}. This value tells how many time we try to find a new value
     * by incrementing {@link #generation} before giving up.
     */
    public final int collisionThreshold ;

    /**
     * How many times the pseudorandom generation did run since {@link #generation} zero.
     */
    public final int pseudorandomIterationCount ;

    private final long currentLongValue ;

    /**
     *
     * @param ceilingOfRandom must be a power of 2 minus 1 so we can mask the hash conveniently.
     *     If this value is equal to {@link #floor} then the generator is monotonic,
     *     and {@link #collisionThreshold} is ignored.
     *
     * @param state0 refers to Xoroshiro's original Java implementation.
     * @param state1 refers to Xoroshiro's original Java implementation.
     */
    protected Pseudorandom(
        final boolean importing,
        final int floor,
        final int ceilingOfRandom,
        final int collisionThreshold,
        final int generation,
        final long state0,
        final long state1,
        final int pseudorandomIterationCount,
        final BitSet reservation,
        final long currentLongValue
    ) {
      super( floor, ceilingOfRandom, generation ) ;
      checkArgument( collisionThreshold > 0 ) ;
      checkArgument( isPowerOfTwo( ceilingOfRandom + 1 ),
          "Must be a power of 2: " + ceilingOfRandom + " + 1" ) ;
      this.collisionThreshold = collisionThreshold ;
      if( importing ) {
        this.currentLongValue = currentLongValue ;
        this.pseudorandomIterationCount = pseudorandomIterationCount ;
        this.reservation = reservation ;
        if( reservation != null && currentLongValue <= ceilingOfRandom ) {
          reservation.set( ( int ) currentLongValue ) ;
        }
        this.state0 = state0 ;
        this.state1 = state1 ;
      } else {
        if( reservation == null ) {
          checkArgument( currentLongValue > ceilingOfRandom ) ;
          this.state0 = -1 ;
          this.state1 = -1 ;
          this.currentLongValue = currentLongValue ;
          this.pseudorandomIterationCount = pseudorandomIterationCount ;
          this.reservation = null ;
        } else {
          checkArgument( currentLongValue == -1 ) ;
          int newAttempt = 0 ;
          long newState0 = state0 ;
          long newState1 = state1 ;
          while( newAttempt < collisionThreshold ) {
            int hashingStep = pseudorandomIterationCount + newAttempt ;
            final long newResult = deriveResult( hashingStep, newState0, newState1 ) ;
            newState0 = deriveState0( hashingStep, newState0, newState1 ) ;
            newState1 = deriveState1( hashingStep, newState0, newState1 ) ;

            final int maskedResult = ( int ) ( newResult & ceilingOfRandom ) ;
            if( ! reservation.get( maskedResult ) && maskedResult >= floor ) {
              this.state0 = newState0 ;
              this.state1 = newState1 ;
              this.currentLongValue = maskedResult ;
              this.reservation = ( BitSet ) reservation.clone() ;
              this.reservation.set( maskedResult ) ;
              this.pseudorandomIterationCount = pseudorandomIterationCount + newAttempt + 1 ;
              return ;
            }
            newAttempt ++ ;
          }
          this.state0 = -1 ;
          this.state1 = -1 ;
          this.currentLongValue = ceilingOfRandom + 1 ;
          this.pseudorandomIterationCount = pseudorandomIterationCount ;
          this.reservation = null ;
        }
      }
    }

    protected abstract long deriveResult( int hashingStep, long state0, long state1 ) ;
    protected abstract long deriveState0( int hashingStep, long state0, long state1 ) ;
    protected abstract long deriveState1( int hashingStep, long state0, long state1 ) ;


    @Override
    public RANDOMIZER next() {
      if( reservation == null ) {
        return instantiator().newInstance(
            false,
            floor,
            ceilingOfRandom,
            collisionThreshold,
            generation + 1,
            -1,
            -1,
            pseudorandomIterationCount,
            null,
            currentLongValue + 1  // Monotonic behavior.
        ) ;
      } else {
        return instantiator().newInstance(
            false,
            floor,
            ceilingOfRandom,
            collisionThreshold,
            generation + 1,
            state0,
            state1,
            pseudorandomIterationCount,
            reservation,
            -1
        ) ;
      }
    }

    @Override
    public long currentLongValue() {
      return currentLongValue ;
    }

    public final Integer reservationSize() {
      if( reservation == null ) {
        return null ;
      } else {
        return reservation.cardinality() ;
      }
    }

    public Long nextMonotonicValueMaybe() {
      return reservationSize() == null ? null : currentLongValue() + 1 ;
    }

    public final boolean isReserved( final int value ) {
      if( reservation == null ) {
        return false ;
      } else {
        return reservation.get( value ) ;
      }
    }

    public final IntStream reservationsAsStream() {
      if( reservation == null ) {
        return null ;
      } else {
        return reservation.stream() ;
      }
    }



    protected abstract Instantiator< RANDOMIZER > instantiator() ;

    @SuppressWarnings( "unused" )
    public static int HIGHEST_CEILING_OF_RANDOM = ( ( int ) Math.pow( 2, 30 ) ) - 1 ;

    public static int nextPowerOfTwoMinus1( final int base ) {
      checkArgument( base > 0 ) ;
      int power = 1 ;
      int shifted = base ;
      while( shifted != 0 ) {
        shifted = shifted >>> 1 ;
        power = power | ( power << 1 ) ;
      }
      return power >>> 1 ;
    }

    /**
     * https://codereview.stackexchange.com/a/172853
     */
    private static boolean isPowerOfTwo( final int number ) {
      return number > 0 && ( ( number & ( number - 1 ) ) == 0 ) ;
    }


    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final Pseudorandom< ? > that = ( Pseudorandom< ? > ) other ;
      return
          state0 == that.state0 &&
          state1 == that.state1 &&
          collisionThreshold == that.collisionThreshold &&
          pseudorandomIterationCount == that.pseudorandomIterationCount &&
          currentLongValue == that.currentLongValue &&
          Objects.equals( reservation, that.reservation )
      ;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          state0,
          state1,
          reservation,
          collisionThreshold,
          pseudorandomIterationCount,
          currentLongValue
      ) ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{" +
          "floor=" + floor + ";" +
          "ceilingOfRandom=" + ceilingOfRandom + ";" +
          "collisionThreshold=" + collisionThreshold + ";" +
          "generation=" + generation + ";" +
          "state0=" + state0 + ";" +
          "state1=" + state1 + ";" +
          "reservation=" + reservation + ";" +
          "pseudorandomIterationCount=" + pseudorandomIterationCount + ";" +
          "currentLongValue=" + currentLongValue +
          '}'
      ;
    }
  }

// ========
// Xorshift
// ========

  /**
   * Do not use, except for comparing with {@link Xoroshiro}.
   */
  class Xorshift extends Pseudorandom< Xorshift > {

    public static Xorshift createNew(
        final int floor,
        final int ceilingOfRandom,
        final int collisionThreshold
    ) {

      return new Xorshift(
          false,
          floor,
          ceilingOfRandom,
          collisionThreshold,
          0,
          -1,
          -1,
          0,
          new BitSet(),
          -1
      ) ;
    }

    protected Xorshift(
        final boolean importing,
        final int floor,
        final int ceilingOfRandom,
        final int collisionThreshold,
        final int generation,
        final long state0,
        final long state1,
        final int pseudorandomIterationCount,
        final BitSet reservation,
        final long nextMonotonicValue
    ) {
      super(
          importing,
          floor,
          ceilingOfRandom,
          collisionThreshold,
          generation,
          state0,
          state1,
          pseudorandomIterationCount,
          reservation,
          nextMonotonicValue
      ) ;
    }

    /**
     * From original Java implementation as of 2018-07-14.
     * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
     */
    @Override
    protected long deriveResult( int hashingStep, long state0, long state1 ) {
      return xorShiftRandom( hashingStep ) ;
    }

    @Override
    protected long deriveState0( int hashingStep, long state0, long state1 ) {
      return 0 ;
    }

    /**
     * From original Java implementation as of 2018-07-14.
     * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
     */
    @Override
    protected long deriveState1( int hashingStep, long state0, long state1 ) {
      return 1 ;
    }

    /**
     * https://www.javamex.com/tutorials/random_numbers/java_util_random_subclassing.shtml
     */
    private static long xorShiftRandom( long seed ) {
      long x = seed ;
      x ^= ( x << 21 ) ;
      x ^= ( x >>> 35 ) ;
      x ^= ( x << 4 ) ;
      return x ;
    }


    @Override
    protected Instantiator< Xorshift > instantiator() {
      return Xorshift::new ;
    }

    /**
     * With a {@link Pseudorandom#ceilingOfRandom} of 2,097,151,
     * which implies a {@link #reservationSize()} of at least 262,144 bytes.
     * {@link Xorshift} reaches 16362 {@link #generation}s
     * (along with a {@link #collisionThreshold} of 100,000).
     * Increasing this value to the next power of two doesn't bring significant enhancements.
     */
    public static int BEST_CEILING_OF_RANDOM = ( ( int ) Math.pow( 2, 21 ) ) - 1 ;

  }



// =========
// Xoroshiro
// =========

  /**
   * The best.
   */
  class Xoroshiro extends Pseudorandom< Xoroshiro > {

    /**
     * A value of 524287, which implies a {@link #reservationSize()} of 64kb at most.
     * With {@link #REASONABLE_COLLISION_THRESHOLD} this can produce about 520,637 random values.
     */
    public static final int REASONABLE_CEILING_OF_RANDOM = nextPowerOfTwoMinus1( 520_000 ) ;

    /**
     * Values above this one don't give much better results along with
     * {@link #REASONABLE_CEILING_OF_RANDOM}.
     */
    public static final int REASONABLE_COLLISION_THRESHOLD = 1000 ;

    /**
     * Use {@link #newBuilder()} whenever possible.
     */
    static Xoroshiro createNew(
        final int floor,
        final int ceilingOfRandom,
        final int collisionThreshold,
        final long seed
    ) {
      /**
       * From original Java implementation as of 2018-07-14.
       * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
       */
      long state = seed + 0x9E3779B97F4A7C15L, z = state ;
      z = ( z ^ ( z >>> 30 ) ) * 0xBF58476D1CE4E5B9L ;
      z = ( z ^ ( z >>> 27 ) ) * 0x94D049BB133111EBL ;
      final long state0 = z ^ ( z >>> 31 ) ;
      state += 0x9E3779B97F4A7C15L ;
      z = state ;
      z = ( z ^ ( z >>> 30 ) ) * 0xBF58476D1CE4E5B9L ;
      z = ( z ^ ( z >>> 27 ) ) * 0x94D049BB133111EBL ;
      final long state1 = z ^ ( z >>> 31 ) ;

      return new Xoroshiro(
          false,
          floor,
          ceilingOfRandom,
          collisionThreshold,
          0,
          state0,
          state1,
          0,
          new BitSet(),
          -1
      ) ;
    }

    Xoroshiro(
        final boolean importing,
        final int floor,
        final int ceilingOfRandom,
        final int collisionThreshold,
        final int generation,
        final long state0,
        final long state1,
        final int pseudorandomIterationCount,
        final BitSet reservation,
        final long nextMonotonicValue
    ) {
      super(
          importing,
          floor,
          ceilingOfRandom,
          collisionThreshold,
          generation,
          state0,
          state1,
          pseudorandomIterationCount,
          reservation,
          nextMonotonicValue
      ) ;
    }

    /**
     * From original Java implementation as of 2018-07-14.
     * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
     */
    @Override
    protected long deriveResult( int hashingStep, long state0, long state1 ) {
      return state0 + state1 ;
    }

    /**
     * From original Java implementation as of 2018-07-14.
     * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
     */
    @Override
    protected long deriveState0( int hashingStep, long state0, long state1 ) {
      final long s0 = state0 ;
      long s1 = state1 ;
      s1 ^= s0 ;
      state0 = ( s0 << 55 | s0 >>> 9 ) ^ s1 ^ ( s1 << 14 ) ;
      return state0 ;
    }

    /**
     * From original Java implementation as of 2018-07-14.
     * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
     */
    @Override
    protected long deriveState1( int hashingStep, long state0, long state1 ) {
      final long s0 = state0 ;
      long s1 = state1 ;
      s1 ^= s0 ;  // Already done somewhere else, whatever.
      state1 = ( s1 << 36 | s1 >>> 28 ) ;
      return state1 ;
    }

    @Override
    protected Instantiator< Xoroshiro > instantiator() {
      return Xoroshiro::new ;
    }
  }


}
