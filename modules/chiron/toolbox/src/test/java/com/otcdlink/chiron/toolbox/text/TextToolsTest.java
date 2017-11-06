package com.otcdlink.chiron.toolbox.text;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TextToolsTest {

  @Test
  public void printableAsciiOnly() throws Exception {

    assertThat( TextTools.toPrintableAscii( " aze!^~" ) ).isEqualTo( " aze!^~" ) ;

    assertThat( TextTools.toPrintableAscii( "é aze!\u9999^~" ) )
        .isEqualTo( "\\u00E9 aze!\\u9999^~" ) ;
  }

  @Test
  public void splitByLineBreaks() throws Exception {
    final String lf = new String( new char[] { 10 } ) ;
    final String cr = new String( new char[] { 13 } ) ;

    final String allLines =
        " " +
        lf +
        "1" + lf +
        "2" + lf + cr +
        "3" + cr + cr +
        "4" + cr + lf + cr +
        "5\n6" + cr + lf + cr +  // Using "\n" makes the test platform-dependant. So sad.
        ""
    ;
    final ImmutableList< String > linesWithEmpties = TextTools.splitByLineBreaks( allLines ) ;
    assertThat( linesWithEmpties ).isEqualTo( ImmutableList.of(
        " ", "1", "2", "3", "4", "5", "6", ""
    ) ) ;

  }
}