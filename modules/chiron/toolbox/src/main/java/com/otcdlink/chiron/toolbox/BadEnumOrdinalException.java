package com.otcdlink.chiron.toolbox;

import com.google.common.collect.ImmutableList;

public class BadEnumOrdinalException extends RuntimeException {
  public BadEnumOrdinalException(
      final int ordinal,
      final ImmutableList values
  ) {
    super( "Bad ordinal: " + ordinal + ", no match in " + values ) ;
  }
}
