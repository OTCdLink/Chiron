package io.github.otcdlink.chiron.configuration.source;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.configuration.Configuration;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

/**
 * Reads a single {@code String} under {@code java.util.Properties} format.
 */
public class StringSource implements Configuration.Source.Stringified {

  private final ImmutableMap< String, String > map ;

  public StringSource( final String string ) {
    map = buildMap( string ) ;
  }

  @Override
  public ImmutableMap< String, String > map() {
    return map ;
  }

  @Override
  public String sourceName() {
    return "java:" + getClass().getSimpleName() + '@' + System.identityHashCode( this ) ;
  }

  public static ImmutableMap< String, String > buildMap( final String string ) {
    try {
      return buildMap( new StringReader( string ) ) ;
    } catch ( final IOException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public static ImmutableMap< String, String > buildMap( final Reader reader ) throws IOException {
    final Properties properties = new Properties() ;
    properties.load( reader ) ;
    final ImmutableMap.Builder< String, String > builder = ImmutableMap.builder() ;
    for( final Map.Entry< Object, Object > entry : properties.entrySet() ) {
      builder.put( ( String ) entry.getKey(), ( String ) entry.getValue() ) ;
    }
    return builder.build() ;
  }


}
