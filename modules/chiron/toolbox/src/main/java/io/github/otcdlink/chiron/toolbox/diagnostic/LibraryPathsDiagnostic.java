package io.github.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableList;

public class LibraryPathsDiagnostic extends AbstractDiagnostic {

  public LibraryPathsDiagnostic(
      final int depth,
      final String indent
  ) {
    super( depth, indent ) ;
  }

  @Override
  public ImmutableList< Diagnostic > subDiagnostics() {
    final ImmutableList.Builder< Diagnostic > diagnosticBuilder = ImmutableList.builder() ;
    for( final String propertyName : SystemPropertiesDiagnostic.PATH_PROPERTY_NAMES ) {
      diagnosticBuilder.add( new SystemPropertyPathDiagnostic(
          increasedDepth + 1,
          indent,
          propertyName
      ) ) ;
    }
    return diagnosticBuilder.build() ;
  }
}
