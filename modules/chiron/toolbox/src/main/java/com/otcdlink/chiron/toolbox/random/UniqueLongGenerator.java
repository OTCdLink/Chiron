package com.otcdlink.chiron.toolbox.random;

import com.otcdlink.chiron.toolbox.ToStringTools;

import java.util.BitSet;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Generates a sequence of unique pseudorandom positive numbers.
 * This sequence is repeatable with another {@link UniqueLongGenerator} built
 * with exactly the same parameters, externalized in a {@link Setup} object.
 * Collision between pseudorandom values are detected by a {@link LongPredicate}
 * which can plug to an existing structure, like a list of already-generated keys.
 * The pseudorandom values are of {@code long} type in the [0..{@link Long#MAX_VALUE}]
 * interval, which means 19-digit numbers on average. For this reason there is an
 * option to restrain this interval. If the interval got "full" the generation then goes
 * monotonic. (What "full" means is explained thereafter.)
 * <p>
 * This behavior is controlled by those parameters:
 *
 * <ul>
 *   <li>
 *     {@link UnicitySetup#floor floor}: the smallest value that can be generated.
 *   </li><li>
 *     {@link UnicitySetup#ceilingOfRandom ceilingOfRandom}: the greatest value minus one,
 *     that can be generated using pseudorandom.
 *     This is also the first value after switching to {@link LongGenerator.Kind#MONOTONIC}.
 *     Invariant: {@link UnicitySetup#ceilingOfRandom ceilingOfRandom} &gt;
 *     {@link UnicitySetup#floor floor}.
 *   </li><li>
 *     {@link UnicitySetup#collisionThreshold collisionThreshold}: the number of subsequent
 *     pseudorandom values detected as colliding, that triggers usage of a monotonic generator.
 *   </li>
 * </ul>
 */
public final class UniqueLongGenerator {

  private final UnicitySetup setup ;

  private LongGenerator longSupplier ;

  private LongGenerator.Kind kind ;

  private int collisionCount = 0 ;

  public UniqueLongGenerator( final Setup setup ) {
    // Avoid polluting the debugger view with confusing fields.
    this.setup = setup.unicityOnly() ;
    this.longSupplier = setup.progress.newFromThis() ;
    this.kind = setup.progress.kind() ;
  }

  public long nextRandomValue( final LongPredicate collisionEvaluator ) {
    while( true ) {
      final long next ;
      if( kind.mayCollide ) {
        next = longSupplier.nextLong( setup.floor, setup.ceilingOfRandom ) ;
        if( kind.mayCollide && collisionEvaluator.test( next ) ) {
          signalCollision() ;
        } else {
          collisionCount = 0 ;
          return next ;
        }
      } else {
        return longSupplier.getAsLong() ;
      }
    }
  }

  public LongStream asLongStream( final LongPredicate collisionEvaluator ) {
    checkNotNull( collisionEvaluator ) ;
    return LongStream.generate( () -> this.nextRandomValue( collisionEvaluator ) ) ;
  }

  /**
   * Using this method can be tricky because it creates its own {@code LongPredicate},
   * so calling it twice for the same {@link UniqueLongGenerator} may produce duplicate values.
   */
  LongStream asLongStreamWithOwnReservation() {
    final LongPredicate reservation = newCollisionEvaluator() ;
    return asLongStream( reservation ) ;
  }

  public CollisionEvaluator newCollisionEvaluator() {
    return setup.newCollisionEvaluator() ;
  }

  public static final class CollisionEvaluator implements LongPredicate {

    private final BitSet bitSet ;

    private CollisionEvaluator( final int size ) {
      this( new BitSet( size ) ) ;
    }

    private CollisionEvaluator( final BitSet bitSet ) {
      this.bitSet = bitSet ;
    }

    @Override
    public boolean test( final long value ) {
      checkArgument( value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE ) ;
      final boolean present = bitSet.get( ( int ) value ) ;
      if( present ) {
        return true ;
      } else {
        bitSet.set( ( int ) value ) ;
        return false ;
      }
    }

    public CollisionEvaluator copy() {
      return new CollisionEvaluator( ( BitSet ) bitSet.clone() ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + bitSet ;
    }
  }

  private void signalCollision() {
    switch( kind ) {
      case XOROSHIRO :
        collisionCount ++ ;
        if( collisionCount >= setup.collisionThreshold ) {
          longSupplier = new Monotonic( setup.ceilingOfRandom ) ;
          kind = LongGenerator.Kind.MONOTONIC ;
        }
        break ;
      case MONOTONIC :
        throw new IllegalStateException(
            "No collision expected when " + LongGenerator.Kind.MONOTONIC ) ;
      default :
        throw new IllegalStateException( "Unsupported: " + kind ) ;
    }
  }

  public static class UnicitySetup {
    public final int floor ;
    public final int ceilingOfRandom ;
    public final int collisionThreshold ;

    public UnicitySetup(
        final int floor,
        final int ceilingOfRandom,
        final int collisionThreshold
    ) {
      checkArgument( floor >= 0 ) ;
      this.floor = floor ;
      this.ceilingOfRandom = ceilingOfRandom ;
      checkArgument( collisionThreshold >= 0 ) ;
      this.collisionThreshold = collisionThreshold ;
    }

    public final CollisionEvaluator newCollisionEvaluator() {
      return new CollisionEvaluator( ceilingOfRandom - 1 ) ;
    }

    @Override
    public final String toString() {
      final StringBuilder stringBuilder = new StringBuilder() ;
      stringBuilder.append( ToStringTools.getNiceClassName( this ) ).append( '{' ) ;
      stringBuilder.append( "floor=" ).append( floor ).append( ';' ) ;
      stringBuilder.append( "ceilingOfRandom=" ).append( ceilingOfRandom ).append( ';' ) ;
      stringBuilder.append( "collisionThreshold=" ).append( collisionThreshold ) ;
      augmentToString( stringBuilder ) ;
      stringBuilder.append( '}' ) ;
      return stringBuilder.toString() ;
    }

    protected void augmentToString( final StringBuilder stringBuilder ) { }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final UnicitySetup that = ( UnicitySetup ) other ;
      return
          floor == that.floor &&
          ceilingOfRandom == that.ceilingOfRandom &&
          collisionThreshold == that.collisionThreshold
      ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( floor, ceilingOfRandom, collisionThreshold ) ;
    }

  }

  public Setup exportSetup() {
    return new Setup(
        setup.floor, setup.ceilingOfRandom, setup.collisionThreshold, longSupplier.progress() ) ;
  }


  public static final class Setup extends UniqueLongGenerator.UnicitySetup {
    public final LongGenerator.Progress progress ;

    public Setup( final UnicitySetup unicitySetup, final long seed ) {
      this(
          unicitySetup.floor,
          unicitySetup.ceilingOfRandom,
          unicitySetup.collisionThreshold,
          new Xoroshiro( seed ).progress()
      ) ;
    }

    public Setup( final UnicitySetup unicitySetup, final LongGenerator.Progress progress ) {
      this(
          unicitySetup.floor,
          unicitySetup.ceilingOfRandom,
          unicitySetup.collisionThreshold,
          progress
      ) ;
    }

    public Setup(
        final int floor,
        final int ceilingOfRandom,
        final int collisionThreshold,
        final LongGenerator.Progress progress
    ) {
      super( floor, ceilingOfRandom, collisionThreshold ) ;
      this.progress = checkNotNull( progress ) ;
    }

    @Override
    protected void augmentToString( StringBuilder stringBuilder ) {
      stringBuilder.append( ';' ).append( "progress=" ).append( progress ) ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false;
      }
      if( ! super.equals( other ) ) return false ;
      final Setup setup = ( Setup ) other ;
      return Objects.equals( progress, setup.progress ) ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( super.hashCode(), progress ) ;
    }

    public static final Setup MONOTONIC_0 = newMonotonic( 0 ).exportSetup() ;

    public UnicitySetup unicityOnly() {
      return new UnicitySetup( floor, ceilingOfRandom, collisionThreshold ) ;
    }
  }

  public static UniqueLongGenerator newMonotonic() {
    return newMonotonic( 0L ) ;
  }

  public static UniqueLongGenerator newMonotonic( final long firstValue ) {
    return new UniqueLongGenerator( new Setup( 0, 1, 0, new Monotonic.Progress( firstValue ) ) ) ;
  }

  public static UniqueLongGenerator newPseudorandom(
      final UnicitySetup unicitySetup,
      final long seed
  ) {
    return newFromSetup( new Setup( unicitySetup, new Xoroshiro( seed ).progress() ) ) ;
  }

  public static UniqueLongGenerator newPseudorandom(
      final int floor,
      final int ceilingOfRandom,
      final int collisionThreshold,
      final long seed
  ) {
    return newFromSetup( new Setup(
        floor,
        ceilingOfRandom,
        collisionThreshold,
        new Xoroshiro( seed ).progress()
    ) ) ;
  }

  public static UniqueLongGenerator newFromSetup( final Setup setup ) {
    return new UniqueLongGenerator( setup ) ;
  }


}
