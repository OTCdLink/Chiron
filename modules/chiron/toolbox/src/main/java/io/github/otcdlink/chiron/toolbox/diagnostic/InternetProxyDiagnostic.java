package io.github.otcdlink.chiron.toolbox.diagnostic;

import io.github.otcdlink.chiron.toolbox.internet.InternetProxyAccess;

import java.io.IOException;
import java.io.Writer;

public class InternetProxyDiagnostic extends AbstractDiagnostic {

  private final InternetProxyAccess internetProxyAccess ;

  public InternetProxyDiagnostic(
      final int depth,
      final String indent,
      final InternetProxyAccess internetProxyAccess
      ) {
    super( depth, indent ) ;
    this.internetProxyAccess = internetProxyAccess ;
  }

  @Override
  protected void printSelf( final Writer writer ) throws IOException {
    if( internetProxyAccess == null ) {
      printBodyLine( writer, "[No Internet proxy]" ) ;
    } else {
      printBodyLine( writer, internetProxyAccess.asString() ) ;
    }
  }
}
