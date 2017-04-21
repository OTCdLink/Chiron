package io.github.otcdlink.chiron.command.automatic;


import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;

/**
 * Base class for manual {@code Annotation} instantiation.
 * Copied from:
 * http://svn.apache.org/repos/asf/geronimo/specs/trunk/geronimo-jcdi_1.0_spec/src/main/java/javax/enterprise/util/AnnotationLiteral.java
 *
 * @param <T> wrapped annotation class
 */
@SuppressWarnings( "unchecked" )
abstract class AnnotationLiteral<T extends Annotation> implements Annotation {

  private final Class<T> annotationType;

  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[ 0 ];

  protected AnnotationLiteral() {
    this.annotationType = getAnnotationType( getClass() );

  }

  public Class<? extends Annotation> annotationType() {
    return annotationType;
  }

  private Class<T> getAnnotationType( final Class<?> definedClazz ) {
    final Type superClazz = definedClazz.getGenericSuperclass();

    Class<T> clazz = null;

    if( superClazz.equals( Object.class ) ) {
      throw new RuntimeException( "Super class must be parametrized type!" );
    } else if( superClazz instanceof ParameterizedType ) {
      final ParameterizedType paramType = ( ParameterizedType ) superClazz;
      final Type[] actualArgs = paramType.getActualTypeArguments();

      if( actualArgs.length == 1 ) {
        //Actual annotation type
        final Type type = actualArgs[ 0 ];

        if( type instanceof Class ) {
          clazz = ( Class<T> ) type;
          return clazz;
        } else {
          throw new RuntimeException( "Not class type!" );
        }
      } else {
        throw new RuntimeException( "More than one parametric type!" );
      }
    } else {
      return getAnnotationType( ( Class<?> ) superClazz );
    }

  }

  @Override
  public boolean equals( final Object other ) {
    final Method[] methods = ( Method[] ) AccessController.doPrivileged( ( PrivilegedAction )
        annotationType::getDeclaredMethods ) ;

    if( other == this ) {
      return true;
    }

    if( other == null ) {
      return false;
    }

    if( other instanceof Annotation ) {
      final Annotation annotOther = ( Annotation ) other;
      if( this.annotationType().equals( annotOther.annotationType() ) ) {
        for( final Method method : methods ) {
          final Object value = callMethod( this, method );
          final Object annotValue = callMethod( annotOther, method );

          if( ( value == null && annotValue != null ) || ( value != null && annotValue == null ) ) {
            return false;
          }

          if( value == null && annotValue == null ) {
            continue;
          }

          final Class<?> valueClass = value.getClass();
          final Class<?> annotValueClass = annotValue.getClass();

          if( valueClass.isPrimitive() && annotValueClass.isPrimitive() ) {
            if( ( valueClass != Float.TYPE && annotValue != Float.TYPE )
                || ( valueClass != Double.TYPE && annotValue != Double.TYPE ) ) {
              if( value != annotValue ) {
                return false;
              }

            }
          } else if( valueClass.isArray() && annotValueClass.isArray() ) {
            final Class<?> type = valueClass.getComponentType();
            if( type.isPrimitive() ) {
              if( Long.TYPE == type ) {
                if( !Arrays.equals( ( ( Long[] ) value ), ( Long[] ) annotValue ) ) return false;
              } else if( Integer.TYPE == type ) {
                if( !Arrays.equals( ( ( Integer[] ) value ), ( Integer[] ) annotValue ) )
                  return false;
              } else if( Short.TYPE == type ) {
                if( !Arrays.equals( ( ( Short[] ) value ), ( Short[] ) annotValue ) ) return false;
              } else if( Double.TYPE == type ) {
                if( !Arrays.equals( ( ( Double[] ) value ), ( Double[] ) annotValue ) )
                  return false;
              } else if( Float.TYPE == type ) {
                if( !Arrays.equals( ( ( Float[] ) value ), ( Float[] ) annotValue ) ) return false;
              } else if( Boolean.TYPE == type ) {
                if( !Arrays.equals( ( ( Boolean[] ) value ), ( Boolean[] ) annotValue ) )
                  return false;
              } else if( Byte.TYPE == type ) {
                if( !Arrays.equals( ( ( Byte[] ) value ), ( Byte[] ) annotValue ) ) return false;
              } else if( Character.TYPE == type ) {
                if( !Arrays.equals( ( ( Character[] ) value ), ( Character[] ) annotValue ) )
                  return false;
              }
            } else {
              if( !Arrays.equals( ( ( Object[] ) value ), ( Object[] ) annotValue ) ) return false;
            }
          } else if( value != null && annotValue != null ) {
            if( !value.equals( annotValue ) ) {
              return false;
            }
          }

        }

        return true;
      }
    }

    return false;
  }

  private Object callMethod( final Object instance, final Method method ) {
    final boolean access = method.isAccessible();

    try {
      if( !method.isAccessible() ) {
        AccessController.doPrivileged( new PrivilegedActionForAccessibleObject( method, true ) );
      }

      return method.invoke( instance, EMPTY_OBJECT_ARRAY );
    } catch( final Exception e ) {
      throw new RuntimeException( "Exception in method call : " + method.getName(), e );
    } finally {
      AccessController.doPrivileged( new PrivilegedActionForAccessibleObject( method, access ) );
    }
  }

  @Override
  public int hashCode() {
    final Method[] methods = ( Method[] ) AccessController.doPrivileged( new PrivilegedAction() {
      public Object run() {
        return annotationType.getDeclaredMethods();
      }
    } );

    int hashCode = 0;
    for( final Method method : methods ) {
      // Member name
      final int name = 127 * method.getName().hashCode();

      // Member value
      final Object object = callMethod( this, method );
      int value = 0;
      if( object.getClass().isArray() ) {
        final Class<?> type = object.getClass().getComponentType();
        if( type.isPrimitive() ) {
          if( Long.TYPE == type ) {
            value = Arrays.hashCode( ( Long[] ) object );
          } else if( Integer.TYPE == type ) {
            value = Arrays.hashCode( ( Integer[] ) object );
          } else if( Short.TYPE == type ) {
            value = Arrays.hashCode( ( Short[] ) object );
          } else if( Double.TYPE == type ) {
            value = Arrays.hashCode( ( Double[] ) object );
          } else if( Float.TYPE == type ) {
            value = Arrays.hashCode( ( Float[] ) object );
          } else if( Boolean.TYPE == type ) {
            value = Arrays.hashCode( ( Long[] ) object );
          } else if( Byte.TYPE == type ) {
            value = Arrays.hashCode( ( Byte[] ) object );
          } else if( Character.TYPE == type ) {
            value = Arrays.hashCode( ( Character[] ) object );
          }
        } else {
          value = Arrays.hashCode( ( Object[] ) object );
        }
      } else {
        value = object.hashCode();
      }

      hashCode += name ^ value;
    }
    return hashCode;
  }


  @Override
  public String toString() {
    final Method[] methods = ( Method[] ) AccessController.doPrivileged( ( PrivilegedAction )
        annotationType::getDeclaredMethods ) ;
    final StringBuilder sb = new StringBuilder( "@" + annotationType().getName() + "(" );
    final int lenght = methods.length;

    for( int i = 0 ; i < lenght ; i++ ) {
      // Member name
      sb.append( methods[ i ].getName() ).append( "=" );

      // Member value
      sb.append( callMethod( this, methods[ i ] ) );

      if( i < lenght - 1 ) {
        sb.append( "," );
      }
    }

    sb.append( ")" );

    return sb.toString();
  }

  protected static class PrivilegedActionForAccessibleObject implements PrivilegedAction<Object> {
    AccessibleObject object;
    boolean flag;

    protected PrivilegedActionForAccessibleObject( final AccessibleObject object, final boolean flag ) {
      this.object = object;
      this.flag = flag;
    }

    public Object run() {
      object.setAccessible( flag );
      return null;
    }
  }

}