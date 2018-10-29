package com.otcdlink.chiron.toolbox.random;

import com.otcdlink.chiron.toolbox.ToStringTools;

import static com.google.common.base.Preconditions.checkArgument;

public class Monotonic implements LongGenerator {

  private long currentValue ;

  Monotonic( final long currentValue ) {
    checkArgument( currentValue >= 0 ) ;
    this.currentValue = currentValue ;
  }

  public long getAsLong() {
    return currentValue ++ ;
  }

  @Override
  public LongGenerator.Progress progress() {
    return new Progress( currentValue ) ;
  }

  public static final class Progress implements LongGenerator.Progress {
    public final long currentValue ;

    public Progress( final long currentValue ) {
      checkArgument( currentValue >= 0 ) ;
      this.currentValue = currentValue ;
    }

    @Override
    public Kind kind() {
      return Kind.MONOTONIC ;
    }

    @Override
    public LongGenerator newFromThis() {
      return new Monotonic( currentValue ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" + currentValue + "}" ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final Progress that = ( Progress ) other ;
      return currentValue == that.currentValue ;
    }

    @Override
    public int hashCode() {
      return ( int ) currentValue ;
    }
  }

}
