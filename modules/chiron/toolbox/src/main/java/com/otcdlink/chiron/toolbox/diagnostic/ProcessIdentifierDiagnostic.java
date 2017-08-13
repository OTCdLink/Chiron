package com.otcdlink.chiron.toolbox.diagnostic;

import java.io.IOException;
import java.io.Writer;

public class ProcessIdentifierDiagnostic extends AbstractDiagnostic {

  private final Long processIdentifier ; ;

  public ProcessIdentifierDiagnostic(
      final int depth,
      final String indent) {
    super( depth, indent ) ;
    this.processIdentifier = currentProcessIdentifier() ;
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

  @Override
  protected void printSelf( final Writer writer ) throws IOException {
    if( processIdentifier == null ) {
      printBodyLine( writer, "[No process identifier]" ) ;
    } else {
      printBodyLine( writer, Long.toString( processIdentifier ) ) ;
    }
  }
}
