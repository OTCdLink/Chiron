package io.github.otcdlink.chiron.configuration.source;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.configuration.Configuration;
import io.github.otcdlink.chiron.configuration.ConfigurationException;
import io.github.otcdlink.chiron.configuration.Sources;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Composes a list of {@link Configuration.Source} objects from command-line parameters,
 * with the first parameters being (optionally) a reference to {@link PropertiesFileSource}s.
 * <p>
 * Considering this code:
 * <pre>
 * CommandLineSources.createConfiguration( factory, ImmutableList.of(
 *     "--configuration-files", "file1.properties", "file2.properties",
 *     "--key1", "value1", "--key2", "value2",
 *     "--", "ignored"
 * ) )
 * </pre>
 * It creates 3 sources, for {@code file1}, {@code file2}, and {@code key1->value1, key2->value2}
 * respectively. If several sources define different values for the same keys, the last wins.
 */
public final class CommandLineSources {

  public static final String CONFIGURATIONFILES_DASHED_PARAMETER_NAME = "--configuration-files";

  private CommandLineSources() { }

  public static < C extends Configuration > C createConfiguration(
      final Configuration.Factory< C > factory,
      final String... filenamesAndCommandLineArguments
  ) throws ConfigurationException, IOException {
    return createConfiguration(
        factory,
        ImmutableList.copyOf( filenamesAndCommandLineArguments )
    ) ;
  }

  public static < C extends Configuration > C createConfiguration(
      final Configuration.Factory< C > factory,
      final ImmutableList< String > filenamesAndCommandLineArguments
  ) throws ConfigurationException, IOException {
    return createConfiguration(
        factory,
        CONFIGURATIONFILES_DASHED_PARAMETER_NAME,
        "--",
        "--",
        filenamesAndCommandLineArguments
    ) ;
  }

  public static < C extends Configuration > C createConfiguration(
      final Configuration.Factory< C > factory,
      final String fileListArgumentName,
      final String propertyNameMarker,
      final String argumentListEndMarker,
      final ImmutableList< String > filenamesAndCommandLineArguments
  ) throws ConfigurationException, IOException {
    final Configuration.Source[] sources = createSources(
        fileListArgumentName,
        propertyNameMarker,
        argumentListEndMarker,
        filenamesAndCommandLineArguments
    ) ;
    final Configuration.Source[] rest = sources.length == 0
        ? new Configuration.Source[ 0 ]
        : new Configuration.Source[ sources.length - 1 ]
    ;
    System.arraycopy( sources, 1, rest, 0, sources.length - 1 ) ;
    return factory.create(
        sources[ 0 ],
        rest
    ) ;
  }

  public static Configuration.Source[] createSources(
      final String fileListArgumentName,
      final String propertyNameMarker,
      final String argumentListEndMarker,
      final ImmutableList< String > filenamesAndCommandLineArguments
  ) throws IOException {
    checkArgument( ! Strings.isNullOrEmpty( fileListArgumentName ) ) ;
    checkArgument( ! Strings.isNullOrEmpty( propertyNameMarker ) ) ;
    checkArgument( ! Strings.isNullOrEmpty( argumentListEndMarker ) ) ;
    final ImmutableList.Builder< File > filesBuilder = ImmutableList.builder() ;
    final ImmutableList.Builder< String > overridingProperties = ImmutableList.builder() ;
    int index = 0 ;
    while( index < filenamesAndCommandLineArguments.size() ) {
      String argument = filenamesAndCommandLineArguments.get( index ) ;
      if( argumentListEndMarker.equals( argument ) ) {
        break ;
      }
      if( index == 0 && fileListArgumentName.equals( argument ) ) {
        index ++ ;
        while( index < filenamesAndCommandLineArguments.size()
        ) {
          argument = filenamesAndCommandLineArguments.get( index ) ;
          if( argumentListEndMarker.equals( argument )
              || argument.startsWith( propertyNameMarker )
          ) {
            break ;
          } else {
            filesBuilder.add( new File( argument ) ) ;
          }
          index ++ ;
        }
      }
      while( true ) {
        if( index < filenamesAndCommandLineArguments.size()
            && ! argumentListEndMarker.equals( argument )
        ) {
          overridingProperties.add( argument ) ;
          index ++ ;
          if( index < filenamesAndCommandLineArguments.size()
              && ! argumentListEndMarker.equals( argument )
          ) {
            argument = filenamesAndCommandLineArguments.get( index ) ;
          }
        } else {
          break ;
        }
      }
    }

    final ImmutableList< File > files = filesBuilder.build() ;
    final ImmutableList< String > moreProperties = overridingProperties.build() ;
    final List< Configuration.Source > sources
        = new ArrayList<>( files.size() + moreProperties.size() ) ;
    for( final File file : files ) {
      sources.add( Sources.newSource( file ) ) ;
    }
    sources.add( Sources.newSource( moreProperties ) ) ;

    return sources.toArray( new Configuration.Source[ sources.size() ] ) ;
  }


}
