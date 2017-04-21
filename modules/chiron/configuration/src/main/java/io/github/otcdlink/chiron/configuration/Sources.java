package io.github.otcdlink.chiron.configuration;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.configuration.source.DashedCommandLineSource;
import io.github.otcdlink.chiron.configuration.source.PropertiesFileSource;
import io.github.otcdlink.chiron.configuration.source.StringSource;

import java.io.File;
import java.io.IOException;

/**
 * Static methods for quick creation of {@link Configuration.Source} objects.
 */
public final class Sources {

  private Sources() { }

  public static Configuration.Source newSource( final String... propertiesObjectLines ) {
    final String singlePropertiesObject = Joiner.on( "\n" ).join( propertiesObjectLines ) ;
    return new StringSource( singlePropertiesObject ) ;
  }

  public static Configuration.Source newSource(
      final ImmutableList< String > commandLineArguments
  ) {
    return new DashedCommandLineSource( commandLineArguments ) ;
  }

  public static Configuration.Source newSource( final File file ) 
      throws IOException 
  {
    return new PropertiesFileSource( file ) ;
  }

  static final Configuration.Source UNDEFINED = new Configuration.Source() {
    @Override
    public String sourceName() {
      return "undefined" ;
    }
  } ;

  static final Configuration.Source TWEAKING = new Configuration.Source() {
    @Override
    public String sourceName() {
      return TemplateBasedFactory.class.getSimpleName() + "#tweak" ;
    }
  } ;

}
