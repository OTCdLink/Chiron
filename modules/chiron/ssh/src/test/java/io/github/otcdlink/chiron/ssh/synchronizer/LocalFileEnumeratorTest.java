package io.github.otcdlink.chiron.ssh.synchronizer;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import io.github.otcdlink.chiron.testing.NameAwareRunner;
import io.github.otcdlink.chiron.toolbox.collection.ImmutableKeyHolderMap;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith( NameAwareRunner.class )
public class LocalFileEnumeratorTest {

  @Test
  public void enumerate() throws Exception {
    createRepofiles() ;
    createM1files() ;
    createM2files() ;
    createM3files() ;

    final LocalFileEnumerator.FileBundle fileBundle = new LocalFileEnumerator(
        "noJavaHome",
        Joiner.on( File.pathSeparator ).join(
            mavenRepo_jar1.getAbsolutePath(),
            modules_a_m1_.getAbsolutePath(),
            modules_a_m2_.getAbsolutePath(),
            modules_b_m3_.getAbsolutePath()
        ),
        ImmutableSet.of( modules_a_, modules_b_ )
    ).enumerate() ;

    logFilesFound( fileBundle.fileDetails ) ;

    Assertions.assertThat( fileBundle.fileDetails.keySet() ).containsOnly(
        fileKey_jar1,
        fileKey_a_m1_p_c1,
        fileKey_a_m2_p_c1,
        fileKey_a_m2_p_c2,
        fileKey_a_m3_p_c3
    ) ;

    assertThat( fileBundle.classpathRelativeRoots )
        // .containsOnly( "a/m1", "a/m2", "b/m3" ) ; TODO: make it work.
        .containsOnly( "m1", "m2", "m3" ) ;
  }


  @Test
  public void commonLeadingSegment() throws Exception {
    final File commonPath = LocalFileEnumerator.commonLeadingSegment( ImmutableSet.of(
       modules_a_m1_,
       modules_a_m2_,
       modules_b_m3_
    ) ) ;
    assertThat( commonPath ).isEqualTo( modules_ ) ;
  }

  @Test
  @Ignore( "No useful assertion" )
  public void realClasspath() throws Exception {
    final long start = System.currentTimeMillis() ;

    final ImmutableKeyHolderMap<FileKey, FileDetail> files = new LocalFileEnumerator(
        ImmutableSet.of(
            new File( System.getProperty( "user.dir" ) + "/modules" )
        )
    ).enumerate().fileDetails ;

    logFilesFound( files ) ;

    LOGGER.info( "Done scanning in " + ( System.currentTimeMillis() - start ) + " ms." ) ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( LocalFileEnumeratorTest.class ) ;

  private final File mavenRepo_jar1 ;

  private final File modules_ ;
  private final File modules_a_ ;
  private final File modules_a_m1_ ;
  private final File modules_a_m1_p_c1 ;
  private final File modules_a_m2_ ;
  private final File modules_a_m2_p_c1 ;
  private final File modules_a_m2_p_c2 ;
  private final File modules_b_ ;
  private final File modules_b_m3_ ;
  private final File modules_b_m3_p_c3 ;


  private final FileKey fileKey_jar1 ;
  private final FileKey fileKey_a_m1_p_c1 ;
  private final FileKey fileKey_a_m2_p_c1 ;
  private final FileKey fileKey_a_m2_p_c2 ;
  private final FileKey fileKey_a_m3_p_c3 ;

  public LocalFileEnumeratorTest() throws IOException {
    final File testDirectory = NameAwareRunner.testDirectory() ;

    final File mavenRepo_ = new File( testDirectory, "maven-repository" ) ;
    mavenRepo_jar1 = new File( new File( new File( mavenRepo_, "com" ), "whatever" ), "j1.jar" ) ;

    modules_ = new File( testDirectory, "modules" ) ;
    modules_a_ = modules_ ; // new File( modules_, "a" ) ; TODO: make it work.
    modules_a_m1_ = new File( modules_a_, "m1" );
    modules_a_m1_p_c1 = new File( new File( modules_a_m1_, "p" ), "C1.class" ) ;
    modules_a_m2_ = new File( modules_a_, "m2" ) ;
    modules_a_m2_p_c1 = new File( new File( modules_a_m2_, "p" ), "C1.class" ) ;
    modules_a_m2_p_c2 = new File( new File( modules_a_m2_, "p" ), "C2.class" ) ;
    modules_b_ = modules_ ; // new File( modules_, "b" ) ; TODO: make it work.
    modules_b_m3_ = new File( modules_b_, "m3" );
    modules_b_m3_p_c3 = new File( new File( modules_b_m3_, "p" ), "C3.class" ) ;

    fileKey_jar1 = new FileKey( mavenRepo_jar1.getName(), FileKind.PACKAGED ) ;

    fileKey_a_m1_p_c1 = new FileKey(
        pathRelativeToModules( modules_a_m1_p_c1 ), FileKind.COMPILED ) ;

    fileKey_a_m2_p_c1 = new FileKey(
        pathRelativeToModules( modules_a_m2_p_c1 ), FileKind.COMPILED ) ;

    fileKey_a_m2_p_c2 = new FileKey(
        pathRelativeToModules( modules_a_m2_p_c2 ), FileKind.COMPILED ) ;

    fileKey_a_m3_p_c3 = new FileKey(
        pathRelativeToModules( modules_b_m3_p_c3 ), FileKind.COMPILED ) ;

  }

  @SuppressWarnings( "ResultOfMethodCallIgnored" )
  private void createM1files() throws IOException {
    modules_a_m1_p_c1.getParentFile().mkdirs() ;
    Files.write( "C1", modules_a_m1_p_c1, Charsets.UTF_8 ) ;
  }

  @SuppressWarnings( "ResultOfMethodCallIgnored" )
  private void createM2files() throws IOException {
    modules_a_m2_p_c2.getParentFile().mkdirs() ;
    Files.write( "C1", modules_a_m2_p_c1, Charsets.UTF_8 ) ;
    Files.write( "C2", modules_a_m2_p_c2, Charsets.UTF_8 ) ;
  }

  @SuppressWarnings( "ResultOfMethodCallIgnored" )
  private void createM3files() throws IOException {
    modules_b_m3_p_c3.getParentFile().mkdirs() ;
    Files.write( "C3", modules_b_m3_p_c3, Charsets.UTF_8 ) ;
  }

  @SuppressWarnings( "ResultOfMethodCallIgnored" )
  private void createRepofiles() throws IOException {
    mavenRepo_jar1.getParentFile().mkdirs() ;
    Files.write( "J1", mavenRepo_jar1, Charsets.UTF_8 ) ;
  }

  private String pathRelativeToModules( final File file ) {
    return file.getAbsolutePath().substring( modules_.getAbsolutePath().length() + 1 ) ;
  }

  private static void logFilesFound( final ImmutableKeyHolderMap< FileKey, FileDetail > files ) {
    LOGGER.info( "Found: \n  " + Joiner.on( "\n  " ).join( files.values() ) ) ;
  }


}