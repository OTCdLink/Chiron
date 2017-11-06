package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public class SimpleCommandLineParametersDiagnostic extends BaseDiagnostic {

  public SimpleCommandLineParametersDiagnostic( final String[] parameters ) {
    super(
        "Command Line Parameters",
        ImmutableMultimap.of( NO_KEY, Joiner.on( ' ' ).join( parameters ) ),
        ImmutableList.of()
    ) ;
  }


}
