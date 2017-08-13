package com.otcdlink.chiron.middle.throttler;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.ToStringTools;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import static com.google.common.base.Preconditions.checkNotNull;

final class ThrottlerFixture {

  public static final Duration DURATION_2 = new Duration( 2 ) ;
  public static final DateTime TIMESTAMP_0 = new DateTime( 0 ) ;
  public static final DateTime TIMESTAMP_1 = new DateTime( 1 ) ;
  public static final DateTime TIMESTAMP_3 = new DateTime( 3 ) ;

  private ThrottlerFixture() { }

  public static ImmutableList< NumberRestriction > noRestriction() {
    return ImmutableList.of() ;
  }

  public static ImmutableList< NumberRestriction > restrictions( final Number... numbers ) {
    final ImmutableList.Builder< NumberRestriction > builder = ImmutableList.builder() ;
    for( final Number number : numbers ) {
      if( number instanceof Float ) {
        builder.add( new FloatRestriction( ( Float ) number ) ) ;
      } else if( number instanceof Integer ) {
        builder.add( new IntegerPartRestriction( ( Integer ) number ) ) ;
      }
    }
    return builder.build() ;
  }

  /**
   * Applies to positive {@code Number}s that are {@code Integer}s or {@code Float}s
   * (the {@link NumberRestriction} filtered them), with an
   * {@code Integer}-based {@link SessionScopedThrottler.Restriction} applying to every
   * {@link Integer}, and every {@code Float} that has the same integer part.
   */
  public static class NumberRestrictionFactory
      implements SessionScopedThrottler.RestrictionFactory< Number, NumberRestriction< Number >>
  {
    @Override
    public boolean supports( final Number number ) {
      return number instanceof Integer || number instanceof Float ;
    }

    @Override
    public NumberRestriction< Number > createFrom( final Number number ) {
      final NumberRestriction numberRestriction ;
      if( number instanceof Integer ) {
        numberRestriction = new IntegerPartRestriction( ( Integer ) number ) ;
      } else if( number instanceof Float ) {
        numberRestriction = new FloatRestriction( ( Float ) number ) ;
      } else {
        throw new IllegalArgumentException( "Unsupported: " + number.getClass() ) ;
      }
      // Better idea, anyone?
      //noinspection unchecked
      return ( NumberRestriction< Number > ) numberRestriction ;
    }
  }

  public abstract static class NumberRestriction< NUMBER extends Number >
      implements SessionScopedThrottler.Restriction< Number >
  {
    protected final NUMBER restrictionBaseValue ;

    protected NumberRestriction( final NUMBER restrictionBaseValue ) {
      this.restrictionBaseValue = checkNotNull( restrictionBaseValue ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.nameAndCompactHash( this ) + '{' + restrictionBaseValue + '}' ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final NumberRestriction< ? > that = ( NumberRestriction<?> ) other ;
      return restrictionBaseValue.equals( that.restrictionBaseValue ) ;
    }

    @Override
    public int hashCode() {
      return restrictionBaseValue.hashCode() ;
    }
  }

  public static class IntegerPartRestriction extends NumberRestriction< Integer > {

    public IntegerPartRestriction( final int restrictionIntegerValue ) {
      super( restrictionIntegerValue ) ;
    }
    @Override
    public boolean appliesTo( final Number number ) {
      return number.intValue() == this.restrictionBaseValue ;
    }

  }

  public static class FloatRestriction extends NumberRestriction< Float > {

    public FloatRestriction( final float restrictionFloatValue ) {
      super( restrictionFloatValue ) ;
    }
    @Override
    public boolean appliesTo( final Number number ) {
      return restrictionBaseValue.equals( number ) ;
    }

  }
}
