package com.otcdlink.chiron.buffer;

public interface CrudeWriter {

  void writeDelimitedString( final String nonNullString ) ;

  void writeNullableString( final String string ) ;

  void writeIntegerPrimitive( final int integerPrimitive ) ;

  void writeIntegerObject( final Integer integerObject ) ;

  void writeLongPrimitive( long longPrimitive ) ;

  void writeLongObject( Long longObject ) ;

  void writeFloatPrimitive( float floatPrimitive ) ;

  void writeFloatObject( Float floatObject ) ;

  void writeBooleanPrimitive( final boolean booleanPrimitive ) ;

  void writeBooleanObject( final Boolean booleanObject ) ;


}
