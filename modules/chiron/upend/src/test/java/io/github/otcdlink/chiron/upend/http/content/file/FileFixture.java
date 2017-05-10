package io.github.otcdlink.chiron.upend.http.content.file;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import io.github.otcdlink.chiron.toolbox.concurrent.Lazy;
import io.github.otcdlink.chiron.upend.http.content.caching.StaticContentCacheTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;

public final class FileFixture {

  private static final Logger LOGGER = LoggerFactory.getLogger( StaticContentCacheTest.class ) ;

  private static final Charset CHARSET = Charsets.US_ASCII ;

  public final File directory ;

  private final Lazy< ImmutableList< FileElement > > fileElements = new Lazy<>( () -> {
    final ImmutableList.Builder< FileElement > fileElementBuilder = ImmutableList.builder() ;
    for( final Field field : getClass().getFields() ) {
      if( field.getName().startsWith( "file_" ) ) {
        final FileElement fileElement;
        try {
          fileElement = ( FileElement ) field.get( FileFixture.this ) ;
          fileElement.file() ;  // Force creation.
        } catch( IllegalAccessException | IOException e ) {
          throw new RuntimeException( e ) ;
        }
        fileElementBuilder.add( fileElement ) ;
      }
    }
    return fileElementBuilder.build() ;
  } ) ;

  private final Lazy< ImmutableMap< String, String > > mimeTypeMap = new Lazy<>( () -> {
    final Map< String, String > mimeTypeMapBuilder = new HashMap<>() ;
    for( final FileElement fileElement : fileElements() ) {
      final String fileExtension = Files.getFileExtension( fileElement.relativePath ) ;
      mimeTypeMapBuilder.put( fileExtension, fileExtension.toUpperCase() ) ;
    }
    return ImmutableMap.copyOf( mimeTypeMapBuilder ) ;
  } ) ;

  public FileFixture( final File directory ) throws IllegalAccessException {
    this.directory = checkNotNull( directory ) ;
    LOGGER.info( "Test directory: '" + directory.getAbsolutePath() + "'." ) ;
  }



  /** Fields are gathered in {@link #fileElements} using their special name prefix. */

  public final FileElement file_X = new FileElement( "x.x", "Xxx" ) ;
  public final FileElement file_A_Y = new FileElement( "a/y.y", "Yyy" ) ;
  public final FileElement file_A_Z = new FileElement( "a/z.z", "Zzz" ) ;


  public void verifyAll( final ImmutableMap< String, ByteSource > bytesourceMap )
      throws IOException
  {
    for( final FileElement fileElement : fileElements() ) {
      fileElement.verifyContent( bytesourceMap ) ;
    }
  }

  public ImmutableList< FileElement > fileElements() {
    return fileElements.get() ;
  }

  public ImmutableMap< String, String > mimeTypeMap() {
    return mimeTypeMap.get() ;
  }

  public final class FileElement {
    public final String relativePath ;
    public final String content ;
    private final Lazy< File > file ;

    public FileElement( final String relativePath, final String content ) {
      this.relativePath = checkNotNull( relativePath ) ;
      this.content = checkNotNull( content ) + "\n" ;
      file = new Lazy<>( () -> {
        final File newFile = new File( directory, this.relativePath ) ;
        if( ! newFile.getParentFile().exists() ) {
          newFile.getParentFile().mkdir() ;
        }
        if( ! newFile.exists() ) {
          // Let's say that if file already exists, it has expected content.
          try {
            newFile.createNewFile() ;
            Files.write( this.content, newFile, CHARSET ) ;
            LOGGER.info( "Created '" + newFile.getAbsolutePath() + "'." ) ;
          } catch( final IOException e ) {
            throw new RuntimeException( e ) ;
          }
        }
        return newFile ;
      } ) ;
    }

    public File file() throws IOException {
      return file.get() ;
    }

    public String mimeType() {
      return mimeTypeMap().get( Files.getFileExtension( relativePath ) ) ;
    }

    public void verifyContent( final ImmutableMap< String, ByteSource > bytesourceMap )
        throws IOException
    {
      final CharSource charSource = bytesourceMap.get( relativePath ).asCharSource( CHARSET ) ;
      final String actualContent = charSource.read() ;
      assertThat( actualContent )
          .describedAs( "Resource name: '" + relativePath + "' in " + bytesourceMap )
          .isEqualTo( content )
      ;
      LOGGER.info( "Verified content for '" + relativePath + "', " +
          "mime type='" + mimeType() + "'." ) ;
    }
  }
}
