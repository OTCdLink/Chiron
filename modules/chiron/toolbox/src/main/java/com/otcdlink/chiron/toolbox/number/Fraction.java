package com.otcdlink.chiron.toolbox.number;

import java.util.Objects;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;

public final class Fraction {

  private final float value ;

  public Fraction( final float value ) {
    checkArgument( value >= 0f, "value=" + value ) ;
    checkArgument( value <= 1f, "value=" + value ) ;
    this.value = value ;
  }

  public static Fraction newFraction( final float value ) {
    return new Fraction( value ) ;
  }

  public boolean asBoolean( final Random random ) {
    return Math.abs( random.nextFloat() ) < value ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + asString() + "}" ;
  }

  public String asString() {
    return Float.toString( value ) ;
  }

  public String asPercentString() {
    return Float.toString( ( 1000 * value ) / 10 ) + " %" ;
  }

  public float asFloat() {
    return value ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }
    final Fraction that = ( Fraction ) other ;
    return Float.compare( that.value, value ) == 0 ;
  }

  @Override
  public int hashCode() {
    return Objects.hash( value ) ;
  }

  public static final Fraction ZERO = new Fraction( 0f ) ;

  public static final Fraction ONE = new Fraction( 1f ) ;

}
