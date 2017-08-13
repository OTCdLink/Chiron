package com.otcdlink.chiron.upend.http.content.file;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.otcdlink.chiron.upend.http.content.StaticContent;
import com.otcdlink.chiron.upend.http.content.caching.StaticContentCacheFactory;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Resolves a resource name into a {@link StaticContent.FromFile} which is
 * {@code File}-based HTTP-streamable content.
 * <p>
 * The choice of the {@link io.github.otcdlink.chiron.upend.http.content.caching} package is arguable
 * but usage is similar to {@link StaticContentCacheFactory}.
 */
public class StaticFileContentProvider {

  private final File baseDirectory ;
  private final ImmutableMap< String, String > mimeTypeMap ;

  public StaticFileContentProvider(
      final File baseDirectory,
      final ImmutableMap< String, String > mimeTypeMap
  ) {
    // Don't check for directory existence, may be not created yet.
    this.baseDirectory = checkNotNull( baseDirectory ) ;
    this.mimeTypeMap = checkNotNull( mimeTypeMap ) ;
  }

  public StaticContent.FromFile fileContent( final String resourceName ) {
    final String mimeType ;
    final String fileExtension = Files.getFileExtension( resourceName ) ;
    if( fileExtension != null ) {
      mimeType = mimeTypeMap.get( fileExtension ) ;
      if( mimeType != null ) {
        final File file = new File( baseDirectory, resourceName ) ;
        if( file.isFile() ) {
          return new StaticContent.FromFile( file, mimeType ) ;
        }
      }
    }
    return null ;
  }
}
