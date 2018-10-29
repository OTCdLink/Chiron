package com.otcdlink.chiron.configuration;

import com.otcdlink.chiron.toolbox.converter.DefaultStringConverters;
import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultStringConvertersTest {

  @Test
  public void homeResolution() throws Exception {
    final File file = DefaultStringConverters.FILE.convert( "~" + File.separator + "foo" ) ;
    assertThat( file.getAbsolutePath() )
        .isEqualTo( new File( System.getProperty( "user.home" ) ) + "/foo" ) ;
  }
}