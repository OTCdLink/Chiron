package io.github.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.io.Writer;

public interface Diagnostic {

  ImmutableList< Diagnostic > subDiagnostics() ;

  void print( Writer writer ) throws IOException;

  String name() ;


  Diagnostic NULL = new AbstractDiagnostic( 0, "  " ) {
    @Override
    public String name() {
      return "Null diagnostic (for tests only)" ;
    }

    @Override
    public ImmutableList< Diagnostic > subDiagnostics() {
      return ImmutableList.of() ;
    }
  } ;


}
