package io.github.otcdlink.chiron.upend.http.content;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import io.github.otcdlink.chiron.testing.MethodSupport;
import io.github.otcdlink.chiron.upend.http.content.caching.StaticContentCacheTest;
import io.github.otcdlink.chiron.upend.http.content.file.FileFixture;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticContentMapBuilderTest {


  @Test
  public void fromDirectory() throws Exception {
    final FileFixture fileFixture = new FileFixture( methodSupport.getDirectory() ) ;

    fileFixture.file_X.file() ;
    fileFixture.file_A_Y.file() ;
    fileFixture.file_A_Z.file() ;

    final ImmutableMap< String, ByteSource > bytesourceMap =
        new StaticContentMapBuilder( StaticContentMapBuilderTest.class )
        .addAll( fileFixture.directory )
        .build()
    ;
    LOGGER.info( "Built: " + bytesourceMap + "." ) ;

    fileFixture.verifyAll( bytesourceMap ) ;
//    fileFixture.file_X.verifyContent( bytesourceMap ) ;
//    fileFixture.file_A_Y.verifyContent( bytesourceMap ) ;
//    fileFixture.file_A_Z.verifyContent( bytesourceMap ) ;

  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( StaticContentCacheTest.class ) ;
  @Rule
  public final MethodSupport methodSupport = new MethodSupport() ;


}