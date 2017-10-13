package com.otcdlink.chiron.toolbox.text;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith( value = Parameterized.class )
public class ByteSizeTest {

  @Parameterized.Parameters( name = "value={0} powerOf10={1} powerOf2={2}" )
  public static Collection< Object[] > data() {
    final Object[][] parameters = {
        {                   0L,      "0 B",        "0 B" },
        {                  27L,     "27 B",       "27 B" },
        {                 999L,    "999 B",      "999 B" },
        {                1000L,   "1.0 kB",     "1000 B" },
        {                1023L,   "1.0 kB",     "1023 B" },
        {                1024L,   "1.0 kB",    "1.0 KiB" },
        {                1728L,   "1.7 kB",    "1.7 KiB" },
        {              110592L, "110.6 kB",  "108.0 KiB" },
        {             7077888L,   "7.1 MB",    "6.8 MiB" },
        {           452984832L, "453.0 MB",  "432.0 MiB" },
        {         28991029248L,  "29.0 GB",   "27.0 GiB" },
        {       1855425871872L,   "1.9 TB",    "1.7 TiB" },
        { 9223372036854775807L,   "9.2 EB",    "8.0 EiB" }
    } ;
    return Arrays.asList( parameters ) ;
  }

  @Test
  public void verify() throws Exception {
    assertThat( ByteSize.humanReadableByteCount( value, true ) ).isEqualTo( formatted10 ) ;
    assertThat( ByteSize.humanReadableByteCount( value, false ) ).isEqualTo( formatted2 ) ;
  }

  private final long value ;
  private final String formatted10 ;
  private final String formatted2 ;

  public ByteSizeTest(
      final long value,
      final String formatted10,
      final String formatted2
  ) {
    this.value = value ;
    this.formatted10 = formatted10 ;
    this.formatted2 = formatted2 ;
  }
}