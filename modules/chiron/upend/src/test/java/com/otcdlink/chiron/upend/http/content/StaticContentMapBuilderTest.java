package com.otcdlink.chiron.upend.http.content;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.otcdlink.chiron.testing.junit5.DirectoryExtension;
import com.otcdlink.chiron.upend.http.content.caching.StaticContentCacheTest;
import com.otcdlink.chiron.upend.http.content.file.FileFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StaticContentMapBuilderTest {


  @Test
  void fromDirectory() throws Exception {
    final FileFixture fileFixture = new FileFixture( methodSupport.testDirectory() ) ;

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

  @SuppressWarnings( "WeakerAccess" )
  @RegisterExtension
  final DirectoryExtension methodSupport = new DirectoryExtension() ;


}