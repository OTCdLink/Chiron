package com.otcdlink.chiron.middle;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import org.assertj.core.api.AbstractAssert;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

public class CommandAssert extends AbstractAssert< CommandAssert, Command> {

  private static final String COMMAND_CLASSNAME = Command.class.getSimpleName() ;

  protected CommandAssert( final Command actual ) {
    super( actual, CommandAssert.class ) ;
  }

  public static CommandAssert assertThat( final Command actual ) {
    return new CommandAssert( actual ) ;
  }

  public CommandAssert endpointSpecificIs( final Class< ? extends Designator> designatorClass ) {
    isNotNull() ;
    if( ! designatorClass.isAssignableFrom( actual.endpointSpecific.getClass() ) ) {
      failWithMessage(
          "Expected " + ENDPOINTSPECIFIC_FIELD.getName() + " to be " +
          designatorClass.getName() + " (or one of its descendants) " +
          "but was " + actual.endpointSpecific.getClass().getName()
      ) ;
    }
    return this ;
  }

  public CommandAssert isEquivalentTo( final Command expected ) {
    return isEquivalentTo( expected, true ) ;
  }

  public CommandAssert specificFieldsEquivalent( final Command expected ) {
    return isEquivalentTo( expected, false ) ;
  }

  public CommandAssert isEquivalentTo(
      final Command expected,
      final boolean includeEndpointSpecific
  ) {

    if( expected == null && actual == null ) {
      return this ;
    }

    isNotNull() ;

    final String failureMessage = areEquivalentNoNull( expected, actual, includeEndpointSpecific ) ;
    if( failureMessage != null ) {
      failWithMessage( failureMessage ) ;
    }

    return this ;
  }

  private static final Field ENDPOINTSPECIFIC_FIELD ;
  static {
    try {
      ENDPOINTSPECIFIC_FIELD = Command.class.getDeclaredField( "endpointSpecific" ) ;
    } catch( final NoSuchFieldException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  /**
   * We want to reuse equivalence check without having to catch {@link AssertionError}.
   */
  public static String areEquivalent( final Command expected, final Command actual ) {
    return areEquivalent( expected, actual, true ) ;
  }

  public static String areSpecificFieldsEquivalent( final Command expected, final Command actual ) {
    return areEquivalent( expected, actual, false ) ;
  }

  private static String areEquivalent(
      final Command expected,
      final Command actual,
      final boolean includeEndpointspecificField
  ) {
    if( expected == null ) {
      if( actual == null ) {
        return null ;
      } else {
        return "First is null, actual is " + actual ;
      }
    } else {
      if( actual == null ) {
        return "Second is null, expected is " + actual ;
      } else {
        return areEquivalentNoNull( expected, actual, includeEndpointspecificField ) ;
      }
    }
  }

  private static String areEquivalentNoNull(
      final Command expected,
      final Command actual,
      final boolean includeEndpointspecificField
  ) {
    if( ! expected.getClass().equals( actual.getClass() ) ) {
      return
          "Expected " + COMMAND_CLASSNAME + " class to be " + expected.getClass() +
              " but was " + actual.getClass()
          ;
    }
    final ImmutableList< Field > fields ;
    {
      final ImmutableList.Builder< Field > fieldBuilder = ImmutableList.builder() ;
      Class currentClass = expected.getClass() ;
      while( true ) {
        for( final Field field : currentClass.getDeclaredFields() ) {
          fieldBuilder.add( field ) ;
        }
        currentClass = currentClass.getSuperclass() ;
        if( Object.class.equals( currentClass ) ) {
          break ;
        }
      }
      fields = fieldBuilder.build() ;
    }

    for( final Field field : fields ) {
      if( ! includeEndpointspecificField && field.equals( ENDPOINTSPECIFIC_FIELD ) ) {
        continue ;
      }
      if( Modifier.isStatic( field.getModifiers() ) ) {
        continue ;
      }
      field.setAccessible( true ) ;
      final Object expectedValue = extract( field, expected ) ;
      final Object actualValue = extract( field, actual ) ;
      if( ! Objects.equals( expectedValue, actualValue ) ) {
        return
            "Expected field " + field.getName() + " to be " + expectedValue +
                " but was " + actualValue
        ;
      }
    }
    return null ;
  }


    private static Object extract( final Field field, final Object owner ) {
    try {
      return field.get( owner ) ;
    } catch( final IllegalAccessException e ) {
      throw new Error( "Should not happen", e ) ;
    }
  }
}
