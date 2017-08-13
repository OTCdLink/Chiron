package com.otcdlink.chiron.testing;

import com.google.common.base.Throwables;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ReflectionTools {

  public static< OBJECT > void modifyFinalField(
      final Class< OBJECT > fieldOwnerClass,
      final String fieldName,
      final OBJECT instanceToModify,
      final Object newFieldValue
  ) {
    try {
      final Field field = fieldOwnerClass.getDeclaredField( fieldName ) ;
      field.setAccessible( true ) ;
      final Field modifiersField = Field.class.getDeclaredField( "modifiers" ) ;
      modifiersField.setAccessible( true ) ;
      int modifiers = modifiersField.getInt( field ) ;
      modifiers &= ~Modifier.FINAL ;
      modifiersField.setInt( field, modifiers ) ;
      field.set( instanceToModify, newFieldValue ) ;
    } catch( NoSuchFieldException | IllegalAccessException e ) {
      throw Throwables.propagate( e ) ;
    }
  }
}
