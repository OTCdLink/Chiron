package io.github.otcdlink.chiron.upend.http.content;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import io.github.otcdlink.chiron.upend.http.content.caching.StaticContentCacheFactory;
import io.github.otcdlink.chiron.upend.http.content.caching.StaticContentTools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Shorthands for creating {@code Map}s to be used by {@link StaticContentCacheFactory}.
 */
public final class StaticContentMapBuilder {

  private final Class resourceContextClass ;
  private final Map< String, ByteSource > byteSourceMap = new HashMap<>() ;

  public StaticContentMapBuilder( final Class resourceContextClass ) {
    this.resourceContextClass = checkNotNull( resourceContextClass ) ;
  }

  /**
   * @param resourceName also used to load the resource from classpath, using
   *     {@link #resourceContextClass} if resource path is relative.
   */
  public StaticContentMapBuilder put( final String resourceName ) {
    return put(
        resourceName,
        StaticContentTools.asByteSource( resourceContextClass, resourceName )
    ) ;
  }

  public StaticContentMapBuilder put( final String resourceName, final ByteSource byteSource ) {
    byteSourceMap.put( checkNotNull( resourceName ), checkNotNull( byteSource ) ) ;
    return this ;
  }

  public StaticContentMapBuilder addAll( final Map< String, ByteSource > other ) {
    byteSourceMap.putAll( other ) ;
    return this ;
  }

  public StaticContentMapBuilder addAll( final File directory ) throws IOException {
    checkArgument( directory.isDirectory(),
        "Not a directory: '" + directory.getAbsolutePath() + "'" ) ;
    final Path basePath = directory.toPath() ;
    java.nio.file.Files.find(
        basePath,
        Integer.MAX_VALUE,
        ( filePath, fileAttribute ) -> fileAttribute.isRegularFile() )
        .forEach( path -> byteSourceMap.put(
            basePath.relativize( path ).toString().replace( '\\', '/' ),
            com.google.common.io.Files.asByteSource( path.toFile() )
        )
    ) ;
    return this ;
  }

  public ImmutableMap< String, ByteSource > build() {
    return ImmutableMap.copyOf( byteSourceMap ) ;
  }
}
