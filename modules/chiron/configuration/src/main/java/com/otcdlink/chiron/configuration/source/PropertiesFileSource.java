package com.otcdlink.chiron.configuration.source;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.otcdlink.chiron.configuration.Configuration;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * Reads a {@code java.util.Properties} file.
 */
public class PropertiesFileSource implements Configuration.Source.Stringified {

  private final ImmutableMap<String, String> map ;
  private final File source ;

  public PropertiesFileSource( final File file ) throws IOException {
    this.map = StringSource.buildMap( Files.newReader( file, Charsets.UTF_8 ) ) ;
    this.source = file ;
  }

  @Override
  public ImmutableMap< String, String > map() {
    return map ;
  }

  @Override
  public String sourceName() {
    try {
      return source.toURI().toURL().toExternalForm() ;
    } catch ( MalformedURLException e ) {
      throw new RuntimeException( e ) ;
    }
  }
}
