package com.otcdlink.chiron.buffer;

import com.otcdlink.chiron.codec.DecodeException;

public interface CrudeReader {

  String readDelimitedString() throws DecodeException ;

  String readNullableString() throws DecodeException ;

  int readIntegerPrimitive() throws DecodeException ;

  Integer readIntegerObject() throws DecodeException ;

  long readLongPrimitive() throws DecodeException ;

  Long readLongObject() throws DecodeException ;

  float readFloatPrimitive() throws DecodeException ;

  Float readFloatObject() throws DecodeException ;

  boolean readBooleanPrimitive() throws DecodeException ;

  Boolean readBooleanObject() throws DecodeException ;


}
