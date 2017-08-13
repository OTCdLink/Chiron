package com.otcdlink.chiron.toolbox.number;

import com.google.common.base.Equivalence;
import com.otcdlink.chiron.toolbox.ComparatorTools;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;

public final class PositiveIntegerRange {

  public final int lowerBound ;
  public final int upperBound ;

  public PositiveIntegerRange( final int lowerBound, final int upperBound ) {
    checkArgument( lowerBound >= 0 ) ;
    checkArgument( upperBound >= 0 ) ;
    checkArgument( upperBound >= lowerBound ) ;
    this.lowerBound = lowerBound ;
    this.upperBound = upperBound ;
  }

  public static PositiveIntegerRange newRange( final int lowerBound, final int upperBound ) {
    return new PositiveIntegerRange( lowerBound, upperBound ) ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' + boundsAsCompactString() + '}';
  }

  public String boundsAsCompactString() {
    return lowerBound + ".." + upperBound ;
  }

  public int random( final Random random ) {
    return flooredRandom( random, lowerBound ) ;
  }

  public int flooredRandom( final Random random, final int floor ) {
    checkArgument( floor <= upperBound, "Floor=" + floor + " but upperBound=" + upperBound ) ;
    final int lowest = Math.max( lowerBound, floor ) ;
    final int cap = upperBound - lowest ;
    if( cap == 0 ) {
      return lowest ;
    } else {
      return random.nextInt( cap ) + lowest ;
    }
  }

  public String asString() {
    return ( "[" + lowerBound + "," + upperBound + "]" ) ;
  }

  public static final Comparator<PositiveIntegerRange> COMPARATOR
      = new ComparatorTools.WithNull<PositiveIntegerRange>() {
        @Override
        protected int compareNoNulls( 
            final PositiveIntegerRange first,
            final PositiveIntegerRange second
        ) {
          final int lowerBoundComparison = first.lowerBound - second.lowerBound ;
          if( lowerBoundComparison == 0 ) {
            final int upperBoundComparison = first.upperBound - second.upperBound ;
            return upperBoundComparison ;
          } else {
            return lowerBoundComparison ;
          }
        }
      }
  ;

  public static final PositiveIntegerRange ONE_ONE = new PositiveIntegerRange( 1, 1 ) ;
  public static final PositiveIntegerRange ZERO_ZERO = new PositiveIntegerRange( 0, 0 ) ;


  @Override
  public boolean equals( Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final PositiveIntegerRange that = ( PositiveIntegerRange ) other ;

    return EQUIVALENCE.equivalent( this, that ) ;

  }

  @Override
  public int hashCode() {
    return EQUIVALENCE.hash( this ) ;
  }

  public static final Equivalence< PositiveIntegerRange > EQUIVALENCE =
      new Equivalence< PositiveIntegerRange >() {
        @Override
        protected boolean doEquivalent(
            @Nonnull final PositiveIntegerRange first,
            @Nonnull final PositiveIntegerRange second
        ) {
          return first.lowerBound == second.lowerBound && first.upperBound == second.upperBound ;
        }

        @Override
        protected int doHash( @Nonnull final PositiveIntegerRange positiveIntegerRange ) {
          int result = positiveIntegerRange.lowerBound ;
          result = 31 * result + positiveIntegerRange.upperBound ;
          return result ;
        }
      }
  ;
}
