package com.otcdlink.chiron.toolbox.text;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TextToolsTest {

  @Test
  public void printableAsciiOnly() throws Exception {

    assertThat( TextTools.toPrintableAscii( " aze!^~" ) ).isEqualTo( " aze!^~" ) ;

    assertThat( TextTools.toPrintableAscii( "é aze!\u9999^~" ) )
        .isEqualTo( "\\u00E9 aze!\\u9999^~" ) ;
  }
}