package com.otcdlink.chiron.toolbox;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FilenameToolsTest {

  @Test
  public void filenameSanitization() throws Exception {
    assertThat( FilenameTools.sanitize( "-)_../\\x" ) ).isEqualTo( "-_.x" ) ;
  }

  @Test
  public void suffixRemoval() throws Exception {
    assertThat( FilenameTools.removeSuffix( "foo.txt", ".txt" ) ).isEqualTo( "foo" ) ;
  }
}