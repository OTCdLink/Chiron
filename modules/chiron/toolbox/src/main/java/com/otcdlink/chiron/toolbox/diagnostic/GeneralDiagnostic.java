package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableList;

public class GeneralDiagnostic extends AbstractDiagnostic {

  public GeneralDiagnostic() {
    this( 0, "  " ) ;
  }

  public GeneralDiagnostic( final int depth, final String indent ) {
    super( depth, indent ) ;
  }

  @Override
  public String name() {
    return "General self-diagnostic" ;
  }

  @Override
  public ImmutableList< Diagnostic > subDiagnostics() {
    return ImmutableList.< Diagnostic >of(
        new SystemPropertiesDiagnostic( increasedDepth, indent ),
        new LibraryPathsDiagnostic( increasedDepth, indent ),
        new EnvironmentVariablesDiagnostic( increasedDepth, indent )
    ) ;
  }
}
