package com.otcdlink.chiron.testing;

import sun.reflect.ConstructorAccessor;
import sun.reflect.FieldAccessor;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * From http://www.javaspecialists.eu/archive/Issue161.html with some features removed as they
 * seem broken by Java 8.
 *
 * See also: http://niceideas.ch/roller2/badtrash/entry/java_create_enum_instances_dynamically
 *
 * TODO: find ways to encapsulate features from {@code sun.reflect} for multi-JVM support.
 */
public class EnumBuster< E extends Enum< E > > {
  private static final Class[] EMPTY_CLASS_ARRAY = new Class[ 0 ] ;
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[ 0 ] ;

  private static final String VALUES_FIELD = "$VALUES" ;
  private static final String ORDINAL_FIELD = "ordinal" ;

  private final ReflectionFactory reflectionFactory = ReflectionFactory.getReflectionFactory() ;

  private final Class< E > clazz ;

  private final Deque< Memento > undoStack = new LinkedList<>() ;

  /**
   * Construct an {@link EnumBuster} for the given enum class and keep the switch statements
   * of the classes specified in switchUsers in sync with the enum values.
   */
  public EnumBuster( final Class< E > clazz ) {
    try {
      this.clazz = clazz ;
    } catch( final Exception e ) {
      throw new IllegalArgumentException( "Could not create the class", e ) ;
    }
  }

  public static< E extends Enum< E > > EnumBuster< E > create( final Class< E > clazz ) {
    return new EnumBuster<>( clazz ) ;
  }

  /**
   * Make a new enum instance, without adding it to the values array and using
   * the default ordinal of 0.
   */
  public E make( final String value ) {
    return make( value, 0,
        EMPTY_CLASS_ARRAY, EMPTY_OBJECT_ARRAY );
  }

  /**
   * Make a new enum instance with the given ordinal.
   */
  public E make( final String value, final int ordinal ) {
    return make( value, ordinal, EMPTY_CLASS_ARRAY, EMPTY_OBJECT_ARRAY ) ;
  }

  /**
   * Make a new enum instance with the given value, ordinal and additional parameters.
   * The {@code additionalTypes} is used to match the constructor accurately.
   */
  public E make(
      final String value,
      final int ordinal,
      final Class[] additionalTypes,
      final Object[] additional
  ) {
    try {
      undoStack.push( new Memento() ) ;
      final ConstructorAccessor ca = findConstructorAccessor( additionalTypes, clazz ) ;
      return constructEnum( clazz, ca, value, ordinal, additional ) ;
    } catch( final Exception e ) {
      throw new IllegalArgumentException( "Could not create enum", e ) ;
    }
  }

  /**
   * Use only wrapped in a {@code try...finally} block calling {@link #restore()} at the end.
   * <p>
   * This method adds the given enum into the array inside the enum class.
   * If the enum already contains that particular value, then the value is overwritten
   * with our enum. Otherwise it is added at the end of the array.
   * <p>
   * In addition, if there is a constant field in the enum class pointing to an enum with our value,
   * then we replace that with our enum instance.
   * <p>
   * The ordinal is either set to the existing position or to the last value.
   *
   * @param e the enum to add
   */
  public void addByValue( final E e ) {
    checkNotNull( e ) ;
    try {
      undoStack.push( new Memento() ) ;
      final Field valuesField = findValuesField() ;

      // We get the current Enum[].
      final E[] values = values() ;
      for( int i = 0 ; i < values.length ; i++ ) {
        final E value = values[ i ] ;
        if( value.name().equals( e.name() ) ) {
          setOrdinal( e, value.ordinal() ) ;
          values[ i ] = e ;
          replaceConstant( e ) ;
          return ;
        }
      }

      // We did not find it in the existing array, thus append it to the array.
      final E[] newValues = Arrays.copyOf( values, values.length + 1 ) ;
      newValues[ newValues.length - 1 ] = e ;
      ReflectionHelper.setStaticFinalField( valuesField, newValues ) ;

      final int ordinal = newValues.length - 1 ;
      setOrdinal( e, ordinal ) ;
    } catch( final Exception ex ) {
      throw new IllegalArgumentException( "Could not set the enum", ex ) ;
    }
  }

  /**
   * We delete the enum from the values array and set the constant pointer to null.
   *
   * @param e the enum to delete from the type.
   * @return true if the enum was found and deleted; false otherwise.
   */
  public boolean deleteByValue( final E e ) {
    if( e == null ) throw new NullPointerException() ;
    try {
      undoStack.push( new Memento() ) ;
      // we get the current E[]
      final E[] values = values() ;
      for( int i = 0 ; i < values.length ; i++ ) {
        final E value = values[ i ] ;
        if( value.name().equals( e.name() ) ) {
          final E[] newValues = Arrays.copyOf( values, values.length - 1 ) ;
          System.arraycopy( values, i + 1, newValues, i, values.length - i - 1 ) ;
          for( int j = i ; j < newValues.length ; j++ ) {
            setOrdinal( newValues[ j ], j ) ;
          }
          final Field valuesField = findValuesField() ;
          ReflectionHelper.setStaticFinalField( valuesField, newValues ) ;
          blankOutConstant( e ) ;
          return true ;
        }
      }
    } catch( final Exception ex ) {
      throw new IllegalArgumentException( "Could not set the enum", ex ) ;
    }
    return false ;
  }

  /**
   * Undo the state right back to the beginning when the {@link EnumBuster} was created.
   */
  @SuppressWarnings( "StatementWithEmptyBody" )
  public void restore() {
    while( undo() ) { }
  }

  /**
   * Undo the previous operation.
   */
  public boolean undo() {
    try {
      final Memento memento = undoStack.poll() ;
      if( memento == null ) return false ;
      memento.undo() ;
      return true ;
    } catch( final Exception e ) {
      throw new IllegalStateException( "Could not undo", e ) ;
    }
  }

  private ConstructorAccessor findConstructorAccessor(
      final Class[] additionalParameterTypes,
      final Class< E > clazz
  ) throws NoSuchMethodException {
    final Class[] parameterTypes = new Class[ additionalParameterTypes.length + 2 ] ;
    parameterTypes[ 0 ] = String.class ;
    parameterTypes[ 1 ] = int.class ;
    System.arraycopy(
        additionalParameterTypes, 0,
        parameterTypes, 2,
        additionalParameterTypes.length
    ) ;
    final Constructor< E > cstr = clazz.getDeclaredConstructor( parameterTypes ) ;
    return reflectionFactory.newConstructorAccessor( cstr ) ;
  }

  private E constructEnum(
      final Class< E > clazz,
      final ConstructorAccessor ca,
      final String value,
      final int ordinal,
      final Object[] additional
  ) throws Exception {
    final Object[] parms = new Object[ additional.length + 2 ] ;
    parms[ 0 ] = value ;
    parms[ 1 ] = ordinal ;
    System.arraycopy( additional, 0, parms, 2, additional.length ) ;
    return clazz.cast( ca.newInstance( parms ) ) ;
  }


  private void replaceConstant( final E e )
      throws IllegalAccessException, NoSuchFieldException
  {
    final Field[] fields = clazz.getDeclaredFields() ;
    for( final Field field : fields ) {
      if( field.getName().equals( e.name() ) ) {
        ReflectionHelper.setStaticFinalField( field, e ) ;
      }
    }
  }


  private void blankOutConstant( final E e )
      throws IllegalAccessException, NoSuchFieldException {
    final Field[] fields = clazz.getDeclaredFields() ;
    for( final Field field : fields ) {
      if( field.getName().equals( e.name() ) ) {
        ReflectionHelper.setStaticFinalField( field, null ) ;
      }
    }
  }

  private void setOrdinal( final E e, final int ordinal )
      throws NoSuchFieldException, IllegalAccessException
  {
    final Field ordinalField = Enum.class.getDeclaredField( ORDINAL_FIELD ) ;
    ordinalField.setAccessible( true ) ;
    ordinalField.set( e, ordinal ) ;
  }

  /**
   * Method to find the values field, set it to be accessible, and return it.
   *
   * @return the values array field for the enum.
   * @throws NoSuchFieldException if the field could not be found
   */
  private Field findValuesField() throws NoSuchFieldException {
    // First we find the static final array that holds the values in the enum class.
    final Field valuesField = clazz.getDeclaredField( VALUES_FIELD ) ;
    // We mark it to be public.
    valuesField.setAccessible( true ) ;
    return valuesField ;
  }

  @SuppressWarnings( "unchecked" )
  private E[] values() throws NoSuchFieldException, IllegalAccessException {
    final Field valuesField = findValuesField() ;
    return ( E[] ) valuesField.get( null ) ;
  }

  private class Memento {
    private final E[] values ;

    private Memento() throws IllegalAccessException {
      try {
        values = values().clone() ;
      } catch( final Exception e ) {
        throw new IllegalArgumentException( "Could not create the class", e ) ;
      }
    }

    private void undo() throws NoSuchFieldException, IllegalAccessException {
      final Field valuesField = findValuesField() ;
      ReflectionHelper.setStaticFinalField( valuesField, values ) ;

      for( int i = 0 ; i < values.length ; i++ ) {
        setOrdinal( values[ i ], i ) ;
      }

      // Reset all of the constants defined inside the enum.
      final Map< String, E > valuesMap = new HashMap<>();
      for( final E e : values ) {
        valuesMap.put( e.name(), e ) ;
      }
      final Field[] constantEnumFields = clazz.getDeclaredFields() ;
      for( final Field constantEnumField : constantEnumFields ) {
        final E en = valuesMap.get( constantEnumField.getName() ) ;
        if( en != null ) {
          ReflectionHelper.setStaticFinalField( constantEnumField, en ) ;
        }
      }

    }
  }

  public static final class ReflectionHelper {
    private static final String MODIFIERS_FIELD = "modifiers" ;

    private static final ReflectionFactory reflection =
        ReflectionFactory.getReflectionFactory() ;

    private ReflectionHelper() { }

    public static void setStaticFinalField( final Field field, final Object value )
        throws NoSuchFieldException, IllegalAccessException
    {
      // We mark the field to be public.
      field.setAccessible( true ) ;
      // Next we change the modifier in the Field instance to not be final anymore,
      // thus tricking reflection into letting us modify the static final field.
      final Field modifiersField = Field.class.getDeclaredField( MODIFIERS_FIELD ) ;
      modifiersField.setAccessible( true ) ;
      int modifiers = modifiersField.getInt( field ) ;
      // Blank out the final bit in the modifiers int.
      modifiers &= ~Modifier.FINAL ;
      modifiersField.setInt( field, modifiers ) ;
      final FieldAccessor fa = reflection.newFieldAccessor( field, false ) ;
      fa.set( null, value ) ;
    }
  }
}
