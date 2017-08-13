package com.otcdlink.chiron.ssh.synchronizer;

import com.google.common.base.Splitter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.toolbox.collection.ImmutableKeyHolderMap;
import com.otcdlink.chiron.toolbox.concurrent.Lazy;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Scans the classpath.
 * Running file scan on several threads doesn't seem to make it run any faster.
 */
public class LocalFileEnumerator {

  private static final Logger LOGGER = LoggerFactory.getLogger( LocalFileEnumerator.class ) ;

  private final String javaHome ;
  private final String classpath ;
  private final ImmutableSet< File > projectModuleDirectories ;

  public LocalFileEnumerator( final ImmutableSet< File >  projectModuleDirectories ) {
    this(
        correctJavaHome(),
        javaClasspath(),
        projectModuleDirectories
    ) ;
  }

  private static String javaClasspath() {
    return StandardSystemProperty.JAVA_CLASS_PATH.value() ;
  }

  public static String correctJavaHome() {
    final String defaultHome = System.getProperty( "java.home" ) ;
    return new File( defaultHome ).getParent() ;
  }

  public LocalFileEnumerator(
      final String javaHome,
      final String classpath,
      final ImmutableSet< File > projectModuleDirectories
  ) {
    checkArgument( ! Strings.isNullOrEmpty( javaHome ) ) ;
    this.javaHome = javaHome ;
    checkArgument( ! Strings.isNullOrEmpty( classpath ) ) ;
    this.classpath = classpath ;
    this.projectModuleDirectories = checkNotNull( projectModuleDirectories ) ;
    LOGGER.info( "javaHome='" + javaHome + "'."  ) ;
    LOGGER.info( "projectModulesDirectories=" + projectModuleDirectories + "."  ) ;
  }

  public static Lazy< FileBundle > lazy(
      final ImmutableSet< File > projectModuleDirectories
  ) {
    return lazy( projectModuleDirectories, ImmutableKeyHolderMap.of() ) ;
  }

  public static Lazy< FileBundle > lazy(
      final ImmutableSet< File > projectModuleDirectories,
      final ImmutableKeyHolderMap< FileKey, FileDetail > otherFiles
  ) {
    return new Lazy<>( () -> {
      try {
        return new LocalFileEnumerator( projectModuleDirectories )
            .enumerate().addFiles( otherFiles ) ;
      } catch( IOException e ) {
        throw new RuntimeException( e ) ;
      }
    } ) ;
  }

  /**
   * Finds the first segments common to all given paths.
   * <pre>
   *   foo/bar/baz
   *   foo/bar/boo      -->  foo
   *   foo/bar/zoo/waz
   * </pre>
   */
  static File commonLeadingSegment(
      final ImmutableSet< File > projectModuleDirectories
  ) {
    checkArgument( ! projectModuleDirectories.isEmpty() ) ;
    final Map< File, List< String > > segmentMap = new HashMap<>() ;
    for( final File moduleDirectory : projectModuleDirectories ) {
      final List< String > segmentList = new ArrayList<>() ;
      for( Path path : moduleDirectory.getAbsoluteFile().toPath() ) {
        segmentList.add( path.toFile().getName() ) ;
      }
      segmentMap.put( moduleDirectory, segmentList ) ;
    }
    int depth = 0 ;
    File commonDirectory = new File( "/" ) ;
    String currentSegment = null ;
    infinite : while( true ) {
      for( final Map.Entry< File, List< String > > entry : segmentMap.entrySet() ) {
        final List< String > segmentList = entry.getValue() ;
        if( depth >= segmentList.size() ) {
          break infinite ;
        }
        final String segmentAtDepth = segmentList.get( depth ) ;
        if( currentSegment == null ) {
          currentSegment = segmentAtDepth ;
        } else if( ! segmentAtDepth.equals( currentSegment ) ) {
          break infinite ;
        }
      }
      depth ++ ;
      if( currentSegment != null ) {
        commonDirectory = new File( commonDirectory, currentSegment ) ;
      }
      currentSegment = null ;
    }

    return commonDirectory ;
  }

  public FileBundle enumerate() throws IOException {
    final Iterable< String > paths =
        Splitter.on( File.pathSeparator ).omitEmptyStrings().split( classpath ) ;
    final ImmutableKeyHolderMap.Builder< FileKey, FileDetail > collector =
        ImmutableKeyHolderMap.builder() ;
    final ImmutableSet.Builder< String > relativeClasspathRoots = ImmutableSet.builder() ;

    // Need this ugly trick because IntelliJ's 'idea_rt.jar' may appear twice in the classpath.
    final Set< String > packagedFilesAdded = new HashSet<>() ;

    final File commonModuleDirectory = commonLeadingSegment( projectModuleDirectories ) ;
    for( final String path : paths ) {
      if( ! path.startsWith( javaHome ) ) {
        final File projectModulesDirectory ;
        final FileKind fileKind ;
        // Trying to add module directory like "trader" or "chiron" somewhere, with messy results.
        // TODO: fix that mess, create nicer paths on upload target.
        // if( path.startsWith( commonModuleDirectory.getAbsolutePath() ) ) {
        //   fileKind = FileKind.COMPILED ;
        //   projectModulesDirectory = commonModuleDirectory ;
        // } else {
        //   fileKind = FileKind.PACKAGED ;
        //   projectModulesDirectory = null ;
        //}
        {
          File projectModuleDirectoryFound = null ;
          FileKind fileKindFound = null ;
          for( File someProjectModuleDirectory : projectModuleDirectories ) {
            if( path.startsWith( someProjectModuleDirectory.getAbsolutePath() ) ) {
              fileKindFound = FileKind.COMPILED ;
              projectModuleDirectoryFound = someProjectModuleDirectory ;
            }
          }
          fileKind = fileKindFound == null ? FileKind.PACKAGED : fileKindFound ;
          projectModulesDirectory = projectModuleDirectoryFound ;
        }
        // LOGGER.debug( "Processing path '" + path + "' ..." ) ;
        switch( fileKind ) {
          case COMPILED :
            LOGGER.debug( "Found classpath entry for " + fileKind + " files: '" + path + "'." ) ;
            final Path startPath = new File( path ).toPath() ;
            final String relativeClasspathRoot =
                path.substring( projectModulesDirectory.getAbsolutePath().length() + 1 ) ;
            relativeClasspathRoots.add( relativeClasspathRoot ) ;
            addCompiledFiles(
                collector,
                projectModulesDirectory.toPath().toAbsolutePath(),
                startPath
            ) ;
            break ;
          case PACKAGED :
            if( ! packagedFilesAdded.contains( path ) ) {
              addPackagedFile( collector, new File( path ).getPath() ) ;
              packagedFilesAdded.add( path ) ;
            }
          break ;
          default :
            throw new IllegalArgumentException( "Unsupported: " + fileKind ) ;
        }
      }
    }
    return new FileBundle( collector.build(), relativeClasspathRoots.build() ) ;
  }

  private static void addPackagedFile(
      final ImmutableKeyHolderMap.Builder< FileKey, FileDetail > collector,
      final String path
  ) {
    final File file = new File( path ) ;
    final FileDetail fileDetail = new FileDetail(
        file.getParentFile().getAbsolutePath(),
        file.getName(),
        FileKind.PACKAGED,
        new DateTime( file.lastModified() ),
        file.length()
    ) ;
    collector.put( fileDetail ) ;
  }



  public static void addOtherFiles(
      final ImmutableKeyHolderMap.Builder< FileKey, FileDetail > collector,
      final Path startPath
  ) throws IOException {
    addFiles( collector, startPath, startPath.resolve( "" ), FileKind.OTHER ) ;
  }

  private static void addCompiledFiles(
      final ImmutableKeyHolderMap.Builder< FileKey, FileDetail > collector,
      final Path startPath,
      final Path subPath
  ) throws IOException {
    addFiles( collector, startPath, subPath, FileKind.COMPILED ) ;
  }

  private static void addFiles(
      final ImmutableKeyHolderMap.Builder< FileKey, FileDetail > collector,
      final Path startPath,
      final Path subPath,
      final FileKind fileKind
  ) throws IOException {
    try( DirectoryStream< Path > stream = Files.newDirectoryStream( subPath ) ) {
      for( final Path entry : stream ) {
        if( Files.isDirectory( entry ) ) {
          addFiles( collector, startPath, entry, fileKind ) ;
        } else {
          final File file = entry.toFile() ;
          final FileDetail fileDetail = new FileDetail(
              startPath.toString(),
              // startPath.relativize( entry ).toString(),
              // Line above fails when dealing with paths of different origins.
              entry.toString().substring( startPath.toString().length() + 1 ),
              fileKind,
              new DateTime( file.lastModified() ),
              file.length()
          ) ;
          // LOGGER.debug( "Adding " + fileDetail ) ;
          collector.put( fileDetail ) ;
        }
      }
    }
  }

  public static class FileBundle {
    public final ImmutableKeyHolderMap< FileKey, FileDetail > fileDetails ;
    public final ImmutableSet< String > classpathRelativeRoots ;

    public FileBundle(
        final ImmutableKeyHolderMap< FileKey, FileDetail > fileDetails,
        final ImmutableSet< String > classpathRelativeRoots
    ) {
      this.fileDetails = checkNotNull( fileDetails ) ;
      this.classpathRelativeRoots = checkNotNull( classpathRelativeRoots ) ;
    }

    public FileBundle addFiles( final ImmutableKeyHolderMap< FileKey, FileDetail > other ) {
      final ImmutableKeyHolderMap.Builder< FileKey, FileDetail > builder =
          ImmutableKeyHolderMap.builder() ;
      builder.putAll( fileDetails.values() ) ;
      builder.putAll( other.values() ) ;
      return new FileBundle( builder.build(), classpathRelativeRoots ) ;
    }
  }

}
