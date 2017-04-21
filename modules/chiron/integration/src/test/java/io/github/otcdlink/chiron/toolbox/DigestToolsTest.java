package io.github.otcdlink.chiron.toolbox;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.github.otcdlink.chiron.testing.NameAwareRunner;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Base64;

import static io.github.otcdlink.chiron.toolbox.DigestTools.AbstractDigest.isValidBase64;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith( NameAwareRunner.class )
public class DigestToolsTest {

  @Test
  public void sha256ForFile() throws Exception {
    final String content = "This is the file content to hash" ;
    final File file = new File( NameAwareRunner.testDirectory(), "some.txt" ) ;
    Files.write( content, file, Charsets.UTF_8 ) ;
    final DigestTools.Sha256 actual = DigestTools.sha256( file ) ;
    assertThat( actual.hex() ).isEqualTo(
        "6a995ba4978e6183de9ee51af8a5ceb507941cbe54dc8c6ce919dbd0fe657a58" ) ;
    assertThat( actual.base64() ).isEqualTo( "aplbpJeOYYPenuUa+KXOtQeUHL5U3Ixs6Rnb0P5lelg=" ) ;
  }

  @Test
  @Ignore( "Just playing with Base 64's padding, tests nothing" )
  public void experimentWithBase64() throws Exception {
    // http://en.wikipedia.org/wiki/Base64#Padding
    assertThat( encodeBase64String( bytes( "pleasure." ) ) ).isEqualTo( "cGxlYXN1cmUu" ) ;
    assertThat( encodeBase64String( bytes( "leasure." ) ) ).isEqualTo( "bGVhc3VyZS4=" ) ;
    assertThat( encodeBase64String( bytes( "easure." ) ) ).isEqualTo( "ZWFzdXJlLg==" ) ;
    assertThat( encodeBase64String( bytes( "asure." ) ) ).isEqualTo( "YXN1cmUu" ) ;
    assertThat( encodeBase64String( bytes( "sure." ) ) ).isEqualTo( "c3VyZS4=" ) ;
  }

  @Test
  public void evaluateValidity() throws Exception {
    assertThat( DigestTools.Sha256.parseBase64( "aplbpJeOYYPenuUa+KXOtQeUHL5U3Ixs6Rnb0P5lelg=" ) )
        .isNotNull() ;
  }

  @Test
  public void evaluateValidityPadding0() throws Exception {
    assertThat( isValidBase64( "pleasure.".length(), "cGxlYXN1cmUu" ) ).isTrue() ;
  }

  @Test
  public void evaluateValidityPadding1() throws Exception {
    assertThat( isValidBase64( "leasure.".length(), "bGVhc3VyZS4=" ) ).isTrue() ;
  }

  @Test
  public void evaluateValidityPadding2() throws Exception {
    assertThat( isValidBase64( "easure.".length(), "ZWFzdXJlLg==" ) ).isTrue() ;
  }

// =======
// Fixture
// =======

  private static byte[] bytes( final String string ) {
    return string.getBytes( Charsets.US_ASCII ) ;
  }

  private static String encodeBase64String( final byte[] bytes ) {
     return Base64.getEncoder().encodeToString( bytes ) ;
  }
}