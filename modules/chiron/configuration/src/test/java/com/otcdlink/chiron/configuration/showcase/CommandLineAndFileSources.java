package com.otcdlink.chiron.configuration.showcase;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.otcdlink.chiron.configuration.Configuration;
import com.otcdlink.chiron.configuration.ConfigurationTools;
import com.otcdlink.chiron.configuration.source.CommandLineSources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineAndFileSources {

  public interface Simple extends Configuration {
    String string() ;
    int number() ;
  }

  @Test
  public void test() throws Exception {
    final File file1 = folder.newFile( "1.properties" ) ;
    Files.write( "string=foo", file1, Charsets.UTF_8 ) ;
    final File file2 = folder.newFile( "2.properties" ) ;
    Files.write( "number=42", file2, Charsets.UTF_8 ) ;

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

  @Rule
  public TemporaryFolder folder = new TemporaryFolder() ;
}
