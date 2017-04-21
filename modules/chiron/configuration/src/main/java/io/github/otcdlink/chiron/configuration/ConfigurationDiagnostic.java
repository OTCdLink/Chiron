package io.github.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.otcdlink.chiron.toolbox.diagnostic.AbstractDiagnostic;
import io.github.otcdlink.chiron.toolbox.diagnostic.Diagnostic;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link Diagnostic} that takes a {@link Configuration}.
 */
public class ConfigurationDiagnostic extends AbstractDiagnostic {

  private final String diagnosticName ;
  private final Configuration configuration ;

  public ConfigurationDiagnostic(
      final int depth,
      final String indent,
      final Configuration configuration,
      final String diagnosticName
  ) {
    super( depth, indent ) ;
    this.diagnosticName = checkNotNull( diagnosticName ) ;
    this.configuration = checkNotNull( configuration ) ;
  }

  @Override
  public String name() {
    return diagnosticName ;
  }


  @Override
  public ImmutableList< Diagnostic > subDiagnostics() {
    return ImmutableList.< Diagnostic >of(
        new PropertyDiagnostic( increasedDepth, indent, configuration ),
        new SourcesDiagnostic( increasedDepth, indent, configuration )
    ) ;
  }


  private static class SourcesDiagnostic extends AbstractDiagnostic {

    private final ImmutableList< String > diagnosticLines ;

    public SourcesDiagnostic(
        final int depth,
        final String indent,
        final Configuration configuration
    ) {
      super( depth, indent ) ;
      final Configuration.Inspector inspector = ConfigurationTools.newInspector( configuration ) ;
      final ImmutableSet< Configuration.Source > sources = inspector.sources() ;
      final List< String > linesBuilder = new ArrayList<>( sources.size() ) ;
      for( final Configuration.Source source : sources ) {
        linesBuilder.add( source.sourceName() ) ;
      }
      diagnosticLines = ImmutableList.copyOf( linesBuilder ) ;
    }

    @Override
    public String name() {
      return "Sources" ;
    }

    @Override
    protected void printSelf( final Writer writer ) throws IOException {
      for( final String line : diagnosticLines ) {
        printBodyLine( writer, line ) ;
      }
    }

  }

  private static class PropertyDiagnostic extends AbstractDiagnostic {

    private final ImmutableList< String > diagnosticLines ;

    public PropertyDiagnostic(
        final int depth,
        final String indent,
        final Configuration configuration
    ) {
      super( depth, indent ) ;
      final List< String > linesBuilder = new ArrayList<>() ;

      final Configuration.Inspector< ? > inspector = ConfigurationTools.newInspector( configuration ) ;

      try {
        for( final Configuration.Property property : inspector.properties().values() ) {
          final StringBuilder lineBuilder = new StringBuilder( property.name() ) ;
          //noinspection unchecked
          final Configuration.Property.Origin origin = inspector.origin( property ) ;
          if( origin != Configuration.Property.Origin.EXPLICIT ) {
            lineBuilder.append( " (" + origin.name().toLowerCase() + ")" ) ;
          }
          //noinspection unchecked
          final String safeValue = inspector.safeValueOf( property, "******" ) ;
          if( safeValue == null ) {
            lineBuilder.append( " null" ) ;
          } else {
            lineBuilder.append( " = " ) ;
            lineBuilder.append( safeValue ) ;
          }
          linesBuilder.add( lineBuilder.toString() ) ;
        }
      } catch( final Exception e ) {
        linesBuilder.clear() ;
        linesBuilder.add( "Could not parse arguments: "
            + e.getClass().getSimpleName() + ", " + e.getMessage() ) ;
      }
      diagnosticLines = ImmutableList.copyOf( linesBuilder ) ;
    }

    @Override
    public String name() {
      return "Properties" ;
    }

    @Override
    protected void printSelf( final Writer writer ) throws IOException {
      for( final String line : diagnosticLines ) {
        printBodyLine( writer, line ) ;
      }
    }
  }
}