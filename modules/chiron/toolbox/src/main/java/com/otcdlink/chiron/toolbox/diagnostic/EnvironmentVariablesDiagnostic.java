package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnvironmentVariablesDiagnostic extends BaseDiagnostic {

  public EnvironmentVariablesDiagnostic() {
    super( map() ) ;
  }

  private static ImmutableMultimap< String, String > map() {
    final ImmutableMultimap.Builder< String, String > builder = ImmutableMultimap.builder() ;
    final Map< String, String > environmentVariables = System.getenv() ;
    final Set< String > names = environmentVariables.keySet() ;
    final List< String > sortedNames = Lists.newArrayList( names ) ;
    Collections.sort( sortedNames ) ;
    for( final String name : sortedNames ) {
      builder.put( name, environmentVariables.get( name ) ) ;
    }
    return builder.build() ;
  }


}
