package com.otcdlink.chiron.configuration.showcase;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.otcdlink.chiron.configuration.Configuration;
import com.otcdlink.chiron.configuration.ConfigurationTools;
import com.otcdlink.chiron.configuration.source.CommandLineSources;
import com.otcdlink.chiron.testing.junit5.DirectoryExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class CommandLineAndFileSources {

  @Test
  void test() throws Exception {
    final File file1 = directoryExtension.newFile( "1.properties" ) ;
    Files.asCharSink( file1, Charsets.UTF_8 ).write( "string=foo" ) ;
    final File file2 = directoryExtension.newFile( "2.properties" ) ;
    Files.asCharSink( file2, Charsets.UTF_8 ).write( "number=42" ) ;

    final ImmutableList< String > arguments = ImmutableList.of(
        "--configuration-files", file1.getAbsolutePath(), file2.getAbsolutePath(),
        "--number", "43",
        "--", "ignore"
    ) ;

    final Configuration.Factory< Simple > factory = ConfigurationTools.newFactory( Simple.class ) ;

    final Simple configuration = CommandLineSources.createConfiguration( factory, arguments ) ;

    assertThat( configuration.string() ).isEqualTo( "foo" ) ;
    assertThat( configuration.number() ).isEqualTo( 43 ) ;
  }

// =======
// Fixture
// =======

  @SuppressWarnings( "WeakerAccess" )
  @RegisterExtension
  final DirectoryExtension directoryExtension = new DirectoryExtension() ;

  public interface Simple extends Configuration {
    String string() ;
    int number() ;
  }


}
