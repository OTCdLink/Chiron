package io.github.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnvironmentVariablesDiagnostic extends AbstractDiagnostic {

  public EnvironmentVariablesDiagnostic(
      final int depth,
      final String indent
  ) {
    super( depth, indent ) ;
  }

  @Override
  public void printSelf( final Writer writer ) throws IOException {
    final Map< String, String > environmentVariables = System.getenv() ;
    final Set< String > names = environmentVariables.keySet() ;
    final List< String > sortedNames = Lists.newArrayList( names ) ;
    Collections.sort( sortedNames ) ;
    for( final String name : sortedNames ) {
      printBodyLine( writer, name + " = " + environmentVariables.get( name ) ) ;
    }
  }


}
