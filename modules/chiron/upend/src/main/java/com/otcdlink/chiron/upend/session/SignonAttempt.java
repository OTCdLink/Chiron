package com.otcdlink.chiron.upend.session;

import com.otcdlink.chiron.toolbox.EnumTools;

public enum SignonAttempt {
  PRIMARY,
  SECONDARY,
  ;

  public static SignonAttempt fromOrdinal( final int ordinal ) {
    return EnumTools.fromOrdinalSafe( values(), ordinal ) ;
  }
}
