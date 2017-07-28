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

// ===
// MD5
// ===

  @Test
  public void md5() throws Exception {
    final String content = "Wikipedia, l'encyclopedie libre et gratuite" ;
    final DigestTools.Md5 md5 = DigestTools.md5( content.getBytes( Charsets.US_ASCII ) ) ;
    assertThat( md5.hex() ).isEqualTo( "d6aa97d33d459ea3670056e737c99a3d" ) ;
  }


// ====
// SHA1
// ====

  @Test
  public void sha1() throws Exception {
    final String content = "The quick brown fox jumps over the lazy dog" ;
    final DigestTools.Sha1 sha1 = DigestTools.sha1( content.getBytes( Charsets.US_ASCII ) ) ;
    assertThat( sha1.hex() ).isEqualTo( "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12" ) ;
    assertThat( sha1.base64() ).isEqualTo( "L9ThxnotKPzthJ7hu3bnORuT6xI=" ) ;
  }


// ======
// Sha256
// ======

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


// ======
// Base64
// ======

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