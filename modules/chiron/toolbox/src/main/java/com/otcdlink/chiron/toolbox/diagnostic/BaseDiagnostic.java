package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

import static com.google.common.base.Preconditions.checkNotNull;

public class BaseDiagnostic implements Diagnostic {

  private final String name ;
  private final ImmutableMultimap< String, String > properties ;
  private final ImmutableList< Diagnostic > subdiagnostics ;

  public BaseDiagnostic(
      final ImmutableMultimap< String, String > properties
  ) {
    this( properties, ImmutableList.of(), null ) ;
  }

  public BaseDiagnostic(
      final ImmutableMultimap< String, String > properties,
      final ImmutableList< Diagnostic > subdiagnostics
  ) {
    this( properties, subdiagnostics, null ) ;
  }

  public BaseDiagnostic(
      final String name,
      final ImmutableMultimap< String, String > properties,
      final ImmutableList< Diagnostic > subdiagnostics
  ) {
    this( properties, subdiagnostics, checkNotNull( name ) ) ;
  }
  public BaseDiagnostic(
      final ImmutableMultimap< String, String > properties,
      final ImmutableList< Diagnostic > subdiagnostics,
      final String name
  ) {
    this.name = name == null ? resolveName( this ) : name ;
    this.properties = checkNotNull(properties ) ;
    this.subdiagnostics = checkNotNull( subdiagnostics ) ;
  }

  @Override
  public String name() {
    return name ;
  }

  private static String resolveName( final Diagnostic diagnostic ) {
    final String simpleName = diagnostic.getClass().getSimpleName() ;
    final String nameWithSpaces = simpleName.replaceAll( "([a-z])([A-Z])", "$1 $2" ) ;
    final String usualSuffix = "Diagnostic" ;
    if( nameWithSpaces.endsWith( usualSuffix ) ) {
      return nameWithSpaces.substring( 0, nameWithSpaces.length() - usualSuffix.length() ) ;
    } else {
      return nameWithSpaces ;
    }
  }

  @Override
  public ImmutableMultimap< String, String > detail() {
    return properties ;
  }

  @Override
  public ImmutableList< Diagnostic > subDiagnostics() {
    return subdiagnostics ;
  }
}
