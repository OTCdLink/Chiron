package com.otcdlink.chiron.configuration;

import com.google.common.base.Converter;
import com.google.common.base.Strings;

import java.lang.reflect.Method;
import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

class ConfigurationProperty< C extends Configuration >
    implements
    Configuration.Property< C >,
    Comparable<Configuration.Property>
{

  private final Method method ;
  private final String name ;
  private final Converter< String, ? > converter ;
  private final String defaultValueAsString ;
  private final Object defaultValue ;
  private final boolean maybeNull ;
  private final String documentation ;

  ConfigurationProperty(
      final Method method,
      final String name,
      final Object defaultValue,
      final String defaultValueAsString,
      final Converter< String, ? > converter,
      final boolean maybeNull,
      final String documentation
  ) {
    checkArgument( ! Strings.isNullOrEmpty( name ) ) ;
    this.method = checkNotNull( method ) ;
    this.name = name ;
    this.defaultValue = defaultValue ;
    this.defaultValueAsString = defaultValueAsString ;
    this.converter = checkNotNull( converter ) ;
    this.maybeNull = maybeNull ;
    this.documentation = documentation == null ? "" : documentation ;
  }


  @Override
  public Method declaringMethod() {
    return method ;
  }

  @Override
  public String name() {
    return name ;
  }

  @Override
  public Class< ? > type() {
    return method.getReturnType() ;
  }

  @Override
  public Converter< String, ? > converter() {
    return converter ;
  }

  @Override
  public Object defaultValue() {
    return defaultValue ;
  }

  @Override
  public String defaultValueAsString() {
    return defaultValueAsString ;
  }

  @Override
  public boolean maybeNull() {
    return maybeNull ;
  }

  @Override
  public String documentation() {
    return documentation ;
  }

  @Override
  public int compareTo( final Configuration.Property other ) {
    return COMPARATOR.compare( this, other ) ;
  }

  @Override
  public String toString() {
    return Configuration.Property.class.getName() + "{"
        + "name='" + name() + "'"
        + "; type=" + type().getName()
        + "; method=" + method.getDeclaringClass().getName() + "#" + method.getName() + "()"
        + "; default=" + defaultValueAsString()
        + "; maybeNull=" + maybeNull()
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

    ConfigurationProperty that = ( ConfigurationProperty ) other ;

    return COMPARATOR.compare( this, that ) == 0 ;
  }

  @Override
  public int hashCode() {
    int result = method.hashCode() ;
    result = 31 * result + name.hashCode() ;
    result = 31 * result + ( defaultValue != null ? defaultValue.hashCode() : 0 ) ;
    return result ;
  }

  public static final Comparator<Configuration.Property> COMPARATOR
      = new Comparator<Configuration.Property>() {
        @Override
        public int compare( final Configuration.Property first, final Configuration.Property second ) {
          if ( first == null ) {
            if ( second == null ) {
              return 0 ;
            } else {
              return -1 ;
            }
          } else {
            if ( second == null ) {
              return 1 ;
            } else {
              final int nameComparison = first.name().compareTo( second.name() ) ;
              if ( nameComparison == 0 ) {
                final int allowNullComparison
                    = Boolean.valueOf( first.maybeNull() ).compareTo( second.maybeNull() ) ;
                if ( allowNullComparison == 0 ) {
                  if ( first.defaultValue() == null ) {
                    if ( second.defaultValue() == null ) {
                      return 0 ;
                    } else {
                      return -1 ;
                    }
                  } else {
                    if ( second.defaultValue() == null ) {
                      return 1 ;
                    } else {
                      int defaultValueComparison = first.defaultValueAsString()
                          .compareTo( second.defaultValueAsString() ) ;
                      if ( defaultValueComparison == 0 ) {
                        final int methodComparison
                            = first.declaringMethod().toGenericString().compareTo(
                            second.declaringMethod().toGenericString() ) ;
                        return methodComparison ;
                      } else {
                        return defaultValueComparison ;
                      }
                    }
                  }
                } else {
                  return allowNullComparison ;
                }
              } else {
                return nameComparison ;
              }
            }
          }
      }
    }
  ;

}
