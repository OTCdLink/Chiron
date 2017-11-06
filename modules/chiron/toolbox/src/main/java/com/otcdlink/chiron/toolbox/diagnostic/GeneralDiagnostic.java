package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public class GeneralDiagnostic extends BaseDiagnostic {

  public GeneralDiagnostic() {
    super(
        "General self-diagnostic",
        ImmutableMultimap.of(),
        ImmutableList.< Diagnostic >of(
            new SystemPropertiesDiagnostic(),
            new LibraryPathsDiagnostic(),
            new EnvironmentVariablesDiagnostic()
        )
    ) ;
  }

}
