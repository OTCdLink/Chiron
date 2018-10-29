package com.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.toolbox.diagnostic.BaseDiagnostic;
import com.otcdlink.chiron.toolbox.diagnostic.Diagnostic;

/**
 * A {@link Diagnostic} that takes a {@link Configuration}.
 */
public class ConfigurationDiagnostic extends BaseDiagnostic {

  public ConfigurationDiagnostic(
      final Configuration configuration,
      final String diagnosticName
  ) {
    super(
        diagnosticName,
        ImmutableMultimap.of(),
        ImmutableList.of(
            new SourcesDiagnostic( configuration ),
            new PropertyDiagnostic( configuration )
        )
    ) ;
  }


  private static class SourcesDiagnostic extends BaseDiagnostic {

    public SourcesDiagnostic(
        final Configuration configuration
    ) {
      super( extractProperties( configuration ), ImmutableList.of(), "Sources" ) ;
    }

    private static ImmutableMultimap< String, String > extractProperties(
        final Configuration configuration
    ) {
      final Configuration.Inspector inspector = ConfigurationTools.newInspector( configuration ) ;
      final ImmutableSet< Configuration.Source > sources = inspector.sources() ;
      final ImmutableMultimap.Builder< String, String > builder = ImmutableMultimap.builder() ;
      for( final Configuration.Source source : sources ) {
        builder.put( NO_KEY, source.sourceName() ) ;
      }
      return builder.build() ;
    }

  }

  private static class PropertyDiagnostic extends BaseDiagnostic {

    public PropertyDiagnostic( final Configuration configuration ) {
      super( properties( configuration ), ImmutableList.of(), "Properties" ) ;
    }

    private static ImmutableMultimap< String, String > properties(
        final Configuration configuration
    ) {
      final ImmutableMultimap.Builder< String, String > builder = ImmutableMultimap.builder() ;

      final Configuration.Inspector< ? > inspector = ConfigurationTools.newInspector( configuration ) ;

      try {
        for( final Configuration.Property property : inspector.properties().values() ) {
          final StringBuilder keyBuilder = new StringBuilder( property.name() ) ;
          //noinspection unchecked
          final Configuration.Property.Origin origin = inspector.origin( property ) ;
          if( origin != Configuration.Property.Origin.EXPLICIT ) {
            keyBuilder.append( " (" ).append( origin.name().toLowerCase() ).append( ")" ) ;
          }
          //noinspection unchecked
          final String safeValue = inspector.stringValueOf( property ) ;
          final String key = keyBuilder.toString() ;
          if( safeValue == null ) {
            builder.put( key, NO_VALUE ) ;
          } else {
            builder.put( key, safeValue ) ;
          }
        }
      } catch( final Exception e ) {
        builder.put( NO_KEY, "Could not parse arguments: "
            + e.getClass().getSimpleName() + ", " + e.getMessage() ) ;
      }
      return builder.build() ;
    }

  }
}