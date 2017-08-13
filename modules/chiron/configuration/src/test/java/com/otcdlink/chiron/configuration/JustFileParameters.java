package com.otcdlink.chiron.configuration;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.otcdlink.chiron.configuration.source.CommandLineSources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class JustFileParameters {

  public interface Simple extends Configuration {
    int number() ;
  }

  @Test
  public void twoFiles() throws Exception {

    final File file1 = folder.newFile( "1.properties" ) ;
    Files.write( "number = 1", file1, Charsets.UTF_8 ) ;
    final File file2 = folder.newFile( "2.properties" ) ;
    Files.write( "number = 2", file2, Charsets.UTF_8 ) ;

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

  @Rule
  public TemporaryFolder folder = new TemporaryFolder() ;

}
