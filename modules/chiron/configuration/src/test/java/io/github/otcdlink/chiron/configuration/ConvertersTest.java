package io.github.otcdlink.chiron.configuration;

import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class ConvertersTest {

  @Test
  public void homeResolution() throws Exception {
    final File file = Converters.INTO_FILE.convert( "~" + File.separator + "foo" ) ;
    assertThat( file.getAbsolutePath() )
        .isEqualTo( new File( System.getProperty( "user.home" ) ) + "/foo" ) ;
  }
}