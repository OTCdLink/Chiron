package com.otcdlink.chiron.ssh.synchronizer;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableBiMap;
import com.otcdlink.chiron.toolbox.collection.ImmutableKeyHolderMap;
import org.assertj.core.api.Assertions;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteFileEnumeratorTest {

  @Test
  public void parseCompiled() throws Exception {
    final RemoteFileEnumerator remoteFileEnumerator = new RemoteFileEnumerator(
        "piston/",
        ImmutableBiMap.of( FileKind.COMPILED, "compiled" )
    ) ;
    final ImmutableKeyHolderMap<FileKey, FileDetail> enumerate =
        remoteFileEnumerator.enumerate( inputStream(
            "2014-11-20 19:51:05.6125617090 UTC 4096 piston/compiled",
            "2014-11-20 19:51:05.6125617090 UTC 32252861 piston/compiled/com/otcdlink/Some.class"
        )
    ) ;
    Assertions.assertThat( enumerate ).hasSize( 1 ) ;
    final FileDetail fileDetail = enumerate.values().iterator().next() ;
    assertThat( fileDetail.key.relativePath ).isEqualTo( "com/otcdlink/Some.class" ) ;
    assertThat( fileDetail.key.kind ).isEqualTo( FileKind.COMPILED ) ;
    assertThat( fileDetail.parentPath ).isEqualTo( "piston/compiled" ) ;
    assertThat( fileDetail.size ).isEqualTo( 32252861 ) ;
    assertThat( fileDetail.lastChange ).isEqualTo(
        new DateTime( 2014, 11, 20, 19, 51, 5, DateTimeZone.UTC ) ) ;

  }

  @Test
  public void parsePackaged() throws Exception {
    final RemoteFileEnumerator remoteFileEnumerator = new RemoteFileEnumerator(
        "piston",
        ImmutableBiMap.of( FileKind.PACKAGED, "packaged" )
    ) ;
    final ImmutableKeyHolderMap< FileKey, FileDetail > enumerate =
        remoteFileEnumerator.enumerate( inputStream(
            "2014-11-28 08:02:44.7956934880 UTC 100 piston/packaged/nothing.jar"
        )
    ) ;
    Assertions.assertThat( enumerate ).hasSize( 1 ) ;
    final FileDetail fileDetail = enumerate.values().iterator().next() ;
    assertThat( fileDetail.key.relativePath ).isEqualTo( "nothing.jar" ) ;
    assertThat( fileDetail.key.kind ).isEqualTo( FileKind.PACKAGED ) ;
    assertThat( fileDetail.parentPath ).isEqualTo( "piston/packaged" ) ;
    assertThat( fileDetail.size ).isEqualTo( 100 ) ;
    assertThat( fileDetail.lastChange ).isEqualTo(
        new DateTime( 2014, 11, 28, 8, 2, 44, DateTimeZone.UTC ) ) ;

  }

// =======
// Fixture
// =======

  private static InputStream inputStream( final String... lines ) {
    final char lf = ( char ) 10 ;
    final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
        ( Joiner.on( lf ).join( lines ) + lf ).getBytes( Charsets.UTF_8 ) ) ;
    return byteArrayInputStream ;
  }

}