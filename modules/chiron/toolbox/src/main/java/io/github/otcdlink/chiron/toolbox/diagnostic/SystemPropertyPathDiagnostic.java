package io.github.otcdlink.chiron.toolbox.diagnostic;

import io.github.otcdlink.chiron.toolbox.SafeSystemProperty;

import java.io.IOException;
import java.io.Writer;
import java.util.StringTokenizer;

public class SystemPropertyPathDiagnostic extends AbstractDiagnostic {

  private final String systemPropertyName ;

  public SystemPropertyPathDiagnostic(
      final int depth,
      final String indent,
      final String systemPropertyName
  ) {
    super( depth, indent ) ;
    this.systemPropertyName = systemPropertyName ;
  }

  @Override
  public String name() {
    return systemPropertyName ;
  }

  @Override
  public void printSelf( final Writer writer ) throws IOException {
    final StringTokenizer tokenizer
        = new StringTokenizer(
        System.getProperty( systemPropertyName ),
        SafeSystemProperty.Standard.PATH_SEPARATOR.value
    ) ;
    while( tokenizer.hasMoreTokens() ) {
      printBodyLine( writer, tokenizer.nextToken() ) ;
    }

  }
}
