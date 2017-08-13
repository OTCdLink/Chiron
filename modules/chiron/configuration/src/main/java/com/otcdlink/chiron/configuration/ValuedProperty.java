package com.otcdlink.chiron.configuration;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Need to keep package-private because we do tricks with {@link #resolvedValue} and
 * {@link #NULL_VALUE}.
 */
class ValuedProperty {

  public final Configuration.Property property ;
  public final Configuration.Source source ;
  public final String stringValue ;
  public final Object resolvedValue ;
  public final Configuration.Property.Origin origin ;

  public ValuedProperty( final Configuration.Property property ) {
    this.property = checkNotNull( property ) ;
    this.source = Sources.UNDEFINED ;
    this.stringValue = "<not-set>"  ;
    this.resolvedValue = NO_VALUE ;
    this.origin = Configuration.Property.Origin.BUILTIN ;
  }

  public ValuedProperty(
      final Configuration.Property property,
      final Configuration.Source source,
      final Object resolvedValue,
      final Configuration.Property.Origin origin
  ) {
    this.property = checkNotNull( property ) ;
    this.source = checkNotNull( source ) ;
    this.stringValue =
        ( resolvedValue == NULL_VALUE || resolvedValue == NO_VALUE || resolvedValue == null )
        ? null
        : resolvedValue.toString()
    ;
    this.resolvedValue = resolvedValue ;
    this.origin = checkNotNull( origin ) ;
  }

  public ValuedProperty(
      final Configuration.Property property,
      final Configuration.Source source,
      final String stringValue,
      final Object resolvedValue,
      final Configuration.Property.Origin origin
  ) {
    this.property = checkNotNull( property ) ;
    this.source = checkNotNull( source ) ;
    this.stringValue = stringValue ;
    this.resolvedValue = resolvedValue ;
    this.origin = checkNotNull( origin ) ;
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
        + "stringValue=" + stringValue + "; "
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
    if ( stringValue != null
        ? ! stringValue.equals( that.stringValue ) : that.stringValue != null
    ) {
      return false;
    }

    return true ;
  }

  @Override
  public int hashCode() {
    int result = property.hashCode() ;
    result = 31 * result + source.hashCode() ;
    result = 31 * result + ( stringValue != null ? stringValue.hashCode() : 0 ) ;
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
