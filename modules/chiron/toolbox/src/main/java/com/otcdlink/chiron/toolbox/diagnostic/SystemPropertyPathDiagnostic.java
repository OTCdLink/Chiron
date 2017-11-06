package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableMultimap;
import com.otcdlink.chiron.toolbox.SafeSystemProperty;

import java.util.StringTokenizer;

public class SystemPropertyPathDiagnostic extends BaseDiagnostic {

  private final String systemPropertyName ;

  public SystemPropertyPathDiagnostic(
      final String systemPropertyName
  ) {
    super( pathElements( systemPropertyName ) ) ;
    this.systemPropertyName = systemPropertyName ;
  }

  @Override
  public String name() {
    return systemPropertyName ;
  }

  private static ImmutableMultimap< String, String > pathElements(
      final String systemPropertyName
  ) {
    final ImmutableMultimap.Builder< String, String > builder = ImmutableMultimap.builder() ;
    final StringTokenizer tokenizer
        = new StringTokenizer(
        System.getProperty( systemPropertyName ),
        SafeSystemProperty.Standard.PATH_SEPARATOR.value
    ) ;
    while( tokenizer.hasMoreTokens() ) {
      builder.put( NO_KEY, tokenizer.nextToken() ) ;
    }
    return builder.build() ;
  }

}
