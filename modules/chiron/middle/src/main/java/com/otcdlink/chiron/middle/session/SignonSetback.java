package com.otcdlink.chiron.middle.session;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.toolbox.EnumTools;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

public final class SignonSetback {

  public static final int DEFAULT_MAXIMUM_ATTEMPTS = 3 ;

  private SignonSetback( int[] counters ) {
    this.counters = counters ;
  }

  public enum Factor {
    PRIMARY, SECONDARY, ;

    public static final ImmutableSet< Factor > VALUES = ImmutableSet.copyOf( Factor.values() ) ;
    public static final int LENGTH = VALUES.size() ;

    public static Factor fromOrdinal( final int ordinal ) {
      return EnumTools.fromOrdinalSafe( values(), ordinal ) ;
    }

  }

  public static final SignonSetback NONE = new SignonSetback( null ) ;

  private final int[] counters ;

  public SignonSetback increment( final Factor factor ) {
    final int[] countersCopy ;
    if( isEmpty() ) {
      countersCopy = new int[ Factor.LENGTH ] ;
      countersCopy[ factor.ordinal() ] = 1 ;
    } else {
      countersCopy = Arrays.copyOf( counters, counters.length ) ;
      countersCopy[ factor.ordinal() ] ++ ;
      for( int factorIndex = factor.ordinal() - 1 ; factorIndex >= 0 ; factorIndex -- ) {
        countersCopy[ factorIndex ] = 0 ;
      }
    }
    return new SignonSetback( countersCopy ) ;
  }

  public boolean isEmpty() {
    return counters == null ;
  }

  public boolean limitReached() {
    return limitReached( DEFAULT_MAXIMUM_ATTEMPTS ) ;
  }

  public boolean limitReached( final int attemptCount ) {
    if( isEmpty() ) {
      return false ;
    } else {
      for( int counter : counters ) {
        if( counter >= attemptCount ) {
          return true ;
        }
      }
      return false ;
    }
  }

  public final ImmutableMap< Factor, Integer > toMap() {
    if( isEmpty() ) {
      return ImmutableMap.of() ;
    } else {
      final ImmutableMap.Builder<Factor, Integer > builder = ImmutableMap.builder() ;
      for( final Factor factor : Factor.VALUES ) {
        builder.put( factor, counters[ factor.ordinal() ] ) ;
      }
      return builder.build() ;
    }
  }

  public static SignonSetback fromMap( final ImmutableMap< Factor, Integer > counterMap ) {
    final int[] counters = new int[ Factor.LENGTH ] ;
    boolean hasOneCounterGreaterThanZero = false ;
    for( final Factor factor : Factor.VALUES ) {
      Integer counter = counterMap.get( factor ) ;
      if( counter == null ) {
        counter = 0 ;
      } else {
        checkArgument( counter >= 0, "Negative counter in " + counterMap ) ;
      }
      counters[ factor.ordinal() ] = counter ;
      hasOneCounterGreaterThanZero = hasOneCounterGreaterThanZero || counter > 0 ;
    }
    return hasOneCounterGreaterThanZero ? new SignonSetback( counters ) : NONE ;
  }

// ======
// Object
// ======

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }
    SignonSetback that = ( SignonSetback ) other ;
    return Arrays.equals( counters, that.counters ) ;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode( counters ) ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
        "counters=" + Arrays.toString( counters ) +
        '}'
    ;
  }
}
