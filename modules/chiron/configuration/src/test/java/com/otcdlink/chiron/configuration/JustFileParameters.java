package com.otcdlink.chiron.configuration;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.otcdlink.chiron.configuration.source.CommandLineSources;
import com.otcdlink.chiron.testing.junit5.DirectoryExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class JustFileParameters {

  @Test
  void twoFiles() throws Exception {

    final File file1 = directoryExtension.newFile( "1.properties" ) ;
    Files.asCharSink( file1, Charsets.UTF_8 ).write( "number = 1" ) ;
    final File file2 = directoryExtension.newFile( "2.properties" ) ;
    Files.asCharSink( file2, Charsets.UTF_8 ).write( "number = 2" ) ;

    final ImmutableList< String > arguments = ImmutableList.of(
        "--configuration-files", file1.getAbsolutePath(), file2.getAbsolutePath()
    ) ;
    final Configuration.Factory< Simple > factory = ConfigurationTools.newFactory( Simple.class ) ;

    final Simple configuration = CommandLineSources.createConfiguration( factory, arguments ) ;

    assertThat( configuration.number() ).isEqualTo( 2 ) ;
  }

// =======
// Fixture
// =======

  @SuppressWarnings( "WeakerAccess" )
  @RegisterExtension
  final DirectoryExtension directoryExtension = new DirectoryExtension() ;

  public interface Simple extends Configuration {
    int number() ;
  }


}
