package com.otcdlink.chiron.toolbox.random;

import com.otcdlink.chiron.toolbox.ToStringTools;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The algorithm, just the algorithm.
 */
public class Xoroshiro implements LongGenerator {

  private long state0 ;
  private long state1 ;
  private long generationCount = -1 ;

  public Xoroshiro( final long seed ) {
    setSeed( seed ) ;
  }

  private Xoroshiro( final long state0, final long state1, final long generationCount ) {
    this.state0 = state0 ;
    this.state1 = state1 ;
    checkArgument( generationCount >= -1 ) ;
    this.generationCount = generationCount ;
  }

  public long getAsLong() {
    return nextLong() ;
  }

  /**
   * From
   * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
   */
  private void setSeed( final long seed ) {
    long state = seed + 0x9E3779B97F4A7C15L, z = state ;
    z = ( z ^ ( z >>> 30 ) ) * 0xBF58476D1CE4E5B9L ;
    z = ( z ^ ( z >>> 27 ) ) * 0x94D049BB133111EBL ;
    state0 = z ^ ( z >>> 31 ) ;
    state += 0x9E3779B97F4A7C15L ;
    z = state ;
    z = ( z ^ ( z >>> 30 ) ) * 0xBF58476D1CE4E5B9L ;
    z = ( z ^ ( z >>> 27 ) ) * 0x94D049BB133111EBL ;
    state1 = z ^ ( z >>> 31 ) ;
  }

  /**
   * From
   * https://github.com/SquidPony/SquidLib/blob/master/squidlib-util/src/main/java/squidpony/squidmath/XoRoRNG.java
   */
  private long nextLong() {
    final long s0 = state0 ;
    long s1 = state1 ;
    final long result = s0 + s1 ;

    s1 ^= s0 ;
    state0 = ( s0 << 55 | s0 >>> 9 ) ^ s1 ^ ( s1 << 14 ) ; // a, b
    state1 = ( s1 << 36 | s1 >>> 28) ; // c
    /*
    state0 = Long.rotateLeft( s0, 55 ) ^ s1 ^ ( s1 << 14 ) ; // a, b
    state1 = Long.rotateLeft( s1, 36 ) ; // c
    */
    generationCount ++ ;
    return result ;
  }


  @Override
  public LongGenerator.Progress progress() {
    return new Progress( generationCount, state0, state1 ) ;
  }

  public static final class Progress implements LongGenerator.Progress {
    public final long state0 ;
    public final long state1 ;

    /**
     * Useful for debugging.
     */
    public final long generation ;

    public Progress( long generation, long state0, long state1 ) {
      checkArgument( generation >= -1 ) ;
      this.generation = generation ;
      this.state0 = state0 ;
      this.state1 = state1 ;
    }

    @Override
    public Kind kind() {
      return Kind.XOROSHIRO ;
    }

    @Override
    public LongGenerator newFromThis() {
      return new Xoroshiro( state0, state1, generation ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" +
          "generation=" + generation + ";state0=" + state0 + ";state1=" + state1 +
          "}"
      ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final Progress progress = ( Progress ) other ;
      return
          state0 == progress.state0 &&
          state1 == progress.state1 &&
          generation == progress.generation
      ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( state0, state1, generation ) ;
    }
  }

}
