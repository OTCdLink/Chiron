package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public class LibraryPathsDiagnostic extends BaseDiagnostic {

  public LibraryPathsDiagnostic() {
    super( ImmutableMultimap.of(), subdiagnostics(), null ) ;
  }

  private static ImmutableList< Diagnostic > subdiagnostics() {
    final ImmutableList.Builder< Diagnostic > diagnosticBuilder = ImmutableList.builder() ;
    for( final String propertyName : SystemPropertiesDiagnostic.PATH_PROPERTY_NAMES ) {
      diagnosticBuilder.add( new SystemPropertyPathDiagnostic(
          propertyName
      ) ) ;
    }
    return diagnosticBuilder.build() ;
  }
}
