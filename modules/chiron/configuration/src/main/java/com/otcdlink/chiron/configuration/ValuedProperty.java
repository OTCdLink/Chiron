package com.otcdlink.chiron.configuration;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Need to keep package-private because we do tricks with {@link #resolvedValue} and
 * {@link #NULL_VALUE}.
 */
class ValuedProperty {

  public final Configuration.Property property ;
  public final Configuration.Source source ;
  public final String valueFromSource ;
  public final Object resolvedValue ;
  public final String resolvedValueToString ;
  public final Configuration.Property.Origin origin ;

  public ValuedProperty( final Configuration.Property property ) {
    this.property = checkNotNull( property ) ;
    this.source = Sources.UNDEFINED ;
    this.valueFromSource = "<not-set>"  ;
    this.resolvedValueToString = "<not-set>"  ;
    this.resolvedValue = NO_VALUE ;
    this.origin = Configuration.Property.Origin.BUILTIN ;
  }


  public ValuedProperty(
      final Configuration.Property property,
      final Configuration.Source source,
      final String valueFromSource,
      final Object resolvedValue,
      final Configuration.Property.Origin origin
  ) {
    this.property = checkNotNull( property ) ;
    this.source = checkNotNull( source ) ;
    this.valueFromSource = valueFromSource ;
    this.resolvedValue = resolvedValue ;
    this.origin = checkNotNull( origin ) ;
    if( resolvedValue == NULL_VALUE || resolvedValue == NO_VALUE || resolvedValue == null ) {
      resolvedValueToString = null ;
    } else {
      resolvedValueToString = valueFromSource ;
    }
  }


  static Object safeNull( final Class propertyType ) {
    if( Integer.TYPE.equals( propertyType ) ) {
      return 0 ;
    } else if( Byte.TYPE.equals( propertyType ) ) {
      return ( byte ) 0 ;
    } else if( Short.TYPE.equals( propertyType ) ) {
      return ( short ) 0 ;
    } else if( Long.TYPE.equals( propertyType ) ) {
      return ( long ) 0 ;
    } else if( Double.TYPE.equals( propertyType ) ) {
      return ( double ) 0 ;
    } else if( Float.TYPE.equals( propertyType ) ) {
      return ( float ) 0 ;
    } else if( Character.TYPE.equals( propertyType ) ) {
      return ( char ) 0 ;
    } else if( Boolean.TYPE.equals( propertyType ) ) {
      return false  ;
    }
    return null ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{"
        + "property.name()=" + property.name() + "; "
        + "stringValue=" + valueFromSource + "; "
        + "source=" + source.sourceName()
        + "}"
    ;
  }

  @Override
  public boolean equals( final Object other ) {
    if ( this == other ) {
      return true ;
    }
    if ( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final ValuedProperty that = ( ValuedProperty ) other ;

    if ( origin != that.origin ) {
      return false ;
    }
    if ( !property.equals( that.property ) ) {
      return false ;
    }
    if ( resolvedValue != null
        ? ! resolvedValue.equals( that.resolvedValue ) : that.resolvedValue != null
    ) {
       return false;
    }
    if ( !source.equals( that.source ) ) {
      return false ;
    }
    if ( valueFromSource != null
        ? ! valueFromSource.equals( that.valueFromSource ) : that.valueFromSource != null
    ) {
      return false;
    }

    return true ;
  }

  @Override
  public int hashCode() {
    int result = property.hashCode() ;
    result = 31 * result + source.hashCode() ;
    result = 31 * result + ( valueFromSource != null ? valueFromSource.hashCode() : 0 ) ;
    result = 31 * result + ( resolvedValue != null ? resolvedValue.hashCode() : 0 ) ;
    result = ( 31 * result ) + origin.hashCode() ;
    return result ;
  }

  public static final Object NULL_VALUE = new Object() {
    @Override
    public String toString() {
      return ValuedProperty.class.getSimpleName() + "#NULL_VALUE{}";
    }
  } ;

  public static final Object NO_VALUE = new Object() {
    @Override
    public String toString() {
      return ValuedProperty.class.getSimpleName() + "#NO_VALUE{}" ;
    }
  } ;
}
