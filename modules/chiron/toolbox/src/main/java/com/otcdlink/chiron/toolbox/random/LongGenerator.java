package com.otcdlink.chiron.toolbox.random;

import java.util.function.LongSupplier;

/**
 * Describes the contract of something generating a sequence of {@code long}.
 * Made public for the {@link Progress} class.
 */
public interface LongGenerator< PROGRESS extends LongGenerator.Progress > extends LongSupplier {

  /**
   * From
   * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
   * <p>
   * Exclusive on the outer bound; the inner bound is 0. The bound may be negative, which will
   * produce a non-positive result.
   *
   * @param bound the outer exclusive bound; may be positive or negative
   * @return a random long between 0 (inclusive) and bound (exclusive)
   */
  default long nextLong( long bound ) {
    long rand = getAsLong() ;
    final long randLow = rand & 0xFFFFFFFFL ;
    final long boundLow = bound & 0xFFFFFFFFL ;
    rand >>>= 32 ;
    bound >>= 32 ;
    final long z = ( randLow * boundLow >> 32 ) ;
    long t = rand * boundLow + z ;
    final long tLow = t & 0xFFFFFFFFL ;
    t >>>= 32 ;
    return rand * bound + t + ( tLow + randLow * bound >> 32 ) - ( z >> 63 ) - ( bound >> 63 ) ;
  }

  /**
   * From
   * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
   * <p>
   * Inclusive inner, exclusive outer; both inner and outer can be positive or negative.
   *
   * @param inner the inner bound, inclusive, can be positive or negative
   * @param outer the outer bound, exclusive, can be positive or negative and may be greater than
   *     or less than inner
   * @return a random long that may be equal to inner and will otherwise be between inner and outer
   */
  default long nextLong( final long inner, final long outer ) {
    return inner + nextLong( outer - inner ) ;
  }

  PROGRESS progress() ;

  enum Kind {
    XOROSHIRO( true ),
    MONOTONIC( false ),
    ;

    public final boolean mayCollide ;

    Kind( boolean mayCollide ) {
      this.mayCollide = mayCollide ;
    }
  }

  interface Progress {
    Kind kind() ;
    LongGenerator newFromThis() ;
  }

}
