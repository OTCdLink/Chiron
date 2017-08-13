package com.otcdlink.chiron.upend.http.content;

import com.google.common.base.Strings;
import com.google.common.io.ByteSource;

import java.io.File;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents an immutable sequence of bytes that will end up in HTTP responses, and
 * which may be candidate for caching.
 */
public abstract class StaticContent extends MimeTypedResource {

  private StaticContent( final String resourceName, final String mimeType ) {
    super( resourceName, mimeType );
    checkArgument( !Strings.isNullOrEmpty( mimeType ) ) ;
  }


  /**
   * Captures a {@link ByteSource} whose stream content may be opened when needed and cached.
   */
  public static final class Streamed extends StaticContent {
    public final ByteSource byteSource ;

    public Streamed( final ByteSource byteSource, final String mimeType ) {
      this( null, byteSource, mimeType ) ;
    }

    public Streamed(
        final String resourceName,
        final ByteSource byteSource,
        final String mimeType
    ) {
      super( resourceName, mimeType ) ;
      this.byteSource = checkNotNull( byteSource ) ;
    }

    protected String bodyAsString() {
      return byteSource.toString() ;
    }
  }

  /**
   * Represents a non-cacheable byte sequence that Netty can stream.
   */
  public static final class FromFile extends StaticContent {

    public final File file ;

    public FromFile( final File file, final String mimeType ) {
      super( null, mimeType ) ;
      checkArgument( file.isFile() ) ;
      this.file = file ;
    }

    @Override
    protected String bodyAsString() {
      return file.getAbsolutePath() ;
    }

  }


}
