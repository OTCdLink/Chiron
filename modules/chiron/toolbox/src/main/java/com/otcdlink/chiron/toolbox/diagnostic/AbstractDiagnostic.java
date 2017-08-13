package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.SafeSystemProperty;

import java.io.IOException;
import java.io.Writer;

public abstract class AbstractDiagnostic implements Diagnostic {

  protected final int increasedDepth ;
  protected final String indent ;

  public AbstractDiagnostic( final int depth, final String indent ) {
    this.increasedDepth = depth + 1 ;
    this.indent = indent ;
  }

  @Override
  public String name() {
    final String simpleName = getClass().getSimpleName() ;
    final String nameWithSpaces = simpleName.replaceAll( "([a-z])([A-Z])", "$1 $2" ) ;
    final String usualSuffix = "Diagnostic" ;
    if( nameWithSpaces.endsWith( usualSuffix ) ) {
      return nameWithSpaces.substring( 0, nameWithSpaces.length() - usualSuffix.length() ) ;
    } else {
      return nameWithSpaces ;
    }
  }

  @Override
  public final void print( final Writer writer ) throws IOException {
    printTitleLine( writer, name() ) ;
    printSelf( writer ) ;
    for( final Diagnostic diagnostic : subDiagnostics() ) {
      diagnostic.print( writer ) ;
    }
    writer.flush() ;
  }

  private void appendMargin( final Writer writer, final int depth ) throws IOException {
    for( int i = 0 ; i < depth ; i ++ ) {
      writer.append( indent ) ;
    }
  }

  protected final void printBodyLine( final Writer writer, final String line ) throws IOException {
    appendMargin( writer, increasedDepth ) ;
    writer
        .append( line )
        .append( SafeSystemProperty.Standard.LINE_SEPARATOR.value )
    ;
  }

  private void printTitleLine( final Writer writer, final String line ) throws IOException {
    appendMargin( writer, increasedDepth - 1 ) ;
    writer
        .append( line )
        .append( SafeSystemProperty.Standard.LINE_SEPARATOR.value )
    ;
  }

  protected void printSelf( final Writer writer ) throws IOException { }

  @Override
  public ImmutableList< Diagnostic > subDiagnostics() {
    return ImmutableList.of() ;
  }
}
