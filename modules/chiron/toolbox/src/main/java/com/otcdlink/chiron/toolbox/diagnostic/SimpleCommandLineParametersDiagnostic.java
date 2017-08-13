package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.base.Joiner;

import java.io.IOException;
import java.io.Writer;

public class SimpleCommandLineParametersDiagnostic extends AbstractDiagnostic {

  final String line ;

  public SimpleCommandLineParametersDiagnostic(
      final int depth,
      final String indent,
      final String[] parameters
  ) {
    super( depth, indent ) ;
    line = Joiner.on( ' ' ).join( parameters ) ;
  }

  @Override
  public String name() {
    return "Command Line Parameters" ;
  }

  @Override
  protected void printSelf( final Writer writer ) throws IOException {
    printBodyLine( writer, line ) ;
  }
}
