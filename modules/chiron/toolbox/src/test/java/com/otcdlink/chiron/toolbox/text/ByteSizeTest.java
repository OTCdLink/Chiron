package com.otcdlink.chiron.toolbox.text;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ByteSizeTest {

  private static Stream< Arguments > data() {
    return Stream.of(
        Arguments.of(                   0L,      "0 B",        "0 B" ),
        Arguments.of(                  27L,     "27 B",       "27 B" ),
        Arguments.of(                 999L,    "999 B",      "999 B" ),
        Arguments.of(                1000L,   "1.0 kB",     "1000 B" ),
        Arguments.of(                1023L,   "1.0 kB",     "1023 B" ),
        Arguments.of(                1024L,   "1.0 kB",    "1.0 KiB" ),
        Arguments.of(                1728L,   "1.7 kB",    "1.7 KiB" ),
        Arguments.of(              110592L, "110.6 kB",  "108.0 KiB" ),
        Arguments.of(             7077888L,   "7.1 MB",    "6.8 MiB" ),
        Arguments.of(           452984832L, "453.0 MB",  "432.0 MiB" ),
        Arguments.of(         28991029248L,  "29.0 GB",   "27.0 GiB" ),
        Arguments.of(       1855425871872L,   "1.9 TB",    "1.7 TiB" ),
        Arguments.of( 9223372036854775807L,   "9.2 EB",    "8.0 EiB" )
    ) ;
  }

  @ParameterizedTest
  @MethodSource( "data" )
  void verify(
      final long value,
      final String formatted10,
      final String formatted2
  ) {
    assertThat( ByteSize.humanReadableByteCount( value, true ) ).isEqualTo( formatted10 ) ;
    assertThat( ByteSize.humanReadableByteCount( value, false ) ).isEqualTo( formatted2 ) ;
  }


}