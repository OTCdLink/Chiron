package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableMultimap;

public class ProcessIdentifierDiagnostic extends BaseDiagnostic {

  public ProcessIdentifierDiagnostic() {
    super( ImmutableMultimap.of(
        NO_KEY,
        processIdentifierAsString()
    ) ) ;
  }

  public static Long currentProcessIdentifier() {
    try {
      final String processName =
          java.lang.management.ManagementFactory.getRuntimeMXBean().getName() ;
      return Long.parseLong( processName.split( "@" )[ 0 ] ) ;
    } catch( NumberFormatException e ) {
      return null ;
    }
  }

  private static String processIdentifierAsString() {
    final Long processIdentifier = currentProcessIdentifier() ;
    if( processIdentifier == null ) {
      return  "[No process identifier]" ;
    } else {
      return Long.toString( processIdentifier ) ;
    }
  }
}
