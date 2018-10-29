package com.otcdlink.chiron.configuration.source;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.configuration.Configuration;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Interprets a list of {@code String}s where odd elements
 * (expected to be property names) follow GNU convention for command-line parameter names:
 * first a pairs of hyphens, then hyphen-separated lower case words.
 * This {@link Configuration.Source} retransforms property names into camel style
 * (restoring Java method name convention).
 * <p>
 * Constructing one {@link DashedCommandLineSource} with this list of {@code String}s:
 * <pre>
 * --file-name my/file.txt --delay 2000
 * </pre>
 * Its {@link #map()} method returns this:
 * <pre>
 * fileName -@gt; my/file.txt
 * delay    -@gt; 2000"
 * </pre>
 * <p>
 * This class is subject to the limitations of {@link com.google.common.base.CaseFormat}.
 * This means behavior is unspecificed with non-ASCII characters.
 */
public class DashedCommandLineSource implements Configuration.Source.Stringified {

  private final ImmutableMap< String, String > map ;

  public DashedCommandLineSource( final ImmutableList<String> arguments ) {
    checkArgument( arguments.size() % 2 == 0,
        "Expecting an even number of arguments: " + arguments ) ;
    final ImmutableMap.Builder< String, String > builder = ImmutableMap.builder() ;
    for( int i = 0 ; i < arguments.size() ; i += 2 ) {
      final String propertyName = arguments.get( i ) ;
      checkArgument( propertyName.startsWith( "--" ) ) ;
      final String propertyValue = arguments.get( i + 1 ) ;
      builder.put( propertyName.substring( 2 ), propertyValue ) ;
    }
    this.map = builder.build() ;
  }

  @Override
  public ImmutableMap< String, String > map() {
    return map ;
  }

  @Override
  public String sourceName() {
    return "java:" + getClass().getSimpleName() ;
  }


}
