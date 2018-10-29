package com.otcdlink.chiron.wire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlEscapingTest {

  @ParameterizedTest
  @ValueSource( strings = {
      "nothing",
      "<",
      "'",
      "\"",
      "&",
      "Lot's of <things> & \"others\n;",
      ""
  } )
  void escapeAndUnescapeWorking( final String string ) throws IOException {
    final StringBuilder builder = new StringBuilder() ;
    XmlEscaping.ATTRIBUTE_ESCAPER.transform( string, builder ) ;
    final String escaped = builder.toString() ;
    builder.setLength( 0 ) ;
    XmlEscaping.ATTRIBUTE_UNESCAPER.transform( escaped, builder ) ;
    final String unescaped = builder.toString() ;
    LOGGER.info( () -> "Escaped '" + string + "' into '" + escaped + "'." ) ;
    assertThat( unescaped ).isEqualTo( string ) ;
  }

  @ParameterizedTest
  @ValueSource( strings = {
      "&boom;",
      "&&;",
      "&"
  } )
  void brokenEscapings( final String string ) {
    final StringBuilder builder = new StringBuilder() ;
    assertThatThrownBy( () ->
        XmlEscaping.ATTRIBUTE_UNESCAPER.transform( string, builder )
    ).isInstanceOf( IOException.class ) ;
  }

  @Test
  void binarySearch() {
    final char[][] arrays = { "foo".toCharArray(), "bar".toCharArray() } ;
    Arrays.sort( arrays, XmlEscaping.CHAR_ARRAY_COMPARATOR_NO_ZERO ) ;
    assertThat( XmlEscaping.binarySearch( arrays, "bar".toCharArray() ) ).isEqualTo( 0 ) ;
  }



// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( XmlEscaping.class ) ;
}