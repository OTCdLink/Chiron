package com.otcdlink.chiron.toolbox.diagnostic;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.StringWriter;

public final class DiagnosticTools {

  private DiagnosticTools() { }

  public static void log( final Logger logger, final Diagnostic diagnostic ) {
    logger.info( "\n" + stringify( diagnostic ) ) ;
  }

  public static String stringify( final Diagnostic diagnostic ) {
    final StringWriter stringWriter = new StringWriter() ;
    try {
      diagnostic.print( stringWriter ) ;
    } catch( final IOException e ) {
      throw new RuntimeException( e ) ;
    }
    return stringWriter.toString() ;
  }
}
