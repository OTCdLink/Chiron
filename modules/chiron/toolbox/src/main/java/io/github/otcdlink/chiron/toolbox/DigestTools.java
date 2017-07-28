package io.github.otcdlink.chiron.toolbox;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class DigestTools {

  private DigestTools() { }

// ===
// MD5
// ===

  public static final class Md5 extends AbstractDigest {

    public static final int BYTE_COUNT = 16 ;

    public Md5( final byte[] bytes ) {
      super( BYTE_COUNT, bytes ) ;
    }

    public Md5( final String base64 ) {
      super( BYTE_COUNT, base64 ) ;
    }

    public static Md5 parseBase64( final String string ) {
      return AbstractDigest.isValidBase64( BYTE_COUNT, string ) ? new Md5( string ) : null ;
    }
  }

  public static Md5 md5( final File file ) throws IOException {
    final MessageDigest digest = newMd5MessageDigest() ;
    return new Md5( hash( file, digest ) ) ;
  }

  public static Md5 md5( final byte[] bytes ) throws IOException {
    checkNotNull( bytes ) ;
    final MessageDigest digest = newMd5MessageDigest() ;
    return new Md5( digest.digest( bytes ) ) ;
  }

  public static MessageDigest newMd5MessageDigest() {
    final MessageDigest digest ;
    try {
      digest = MessageDigest.getInstance( "MD5" ) ;
    } catch( final NoSuchAlgorithmException e ) {
      throw new Error( "Can't find MD5 implementation", e ) ;
    }
    return digest ;
  }


// ====
// SHA1
// ====

  public static final class Sha1 extends AbstractDigest {

    public static final int BYTE_COUNT = 20 ;

    public Sha1( final byte[] bytes ) {
      super( BYTE_COUNT, bytes ) ;
    }

    public Sha1( final String base64 ) {
      super( BYTE_COUNT, base64 ) ;
    }

    public static Sha1 parseBase64( final String string ) {
      return AbstractDigest.isValidBase64( BYTE_COUNT, string ) ? new Sha1( string ) : null ;
    }
  }

  public static Sha1 sha1( final File file ) throws IOException {
    final MessageDigest digest = newSha1MessageDigest() ;
    return new Sha1( hash( file, digest ) ) ;
  }

  public static Sha1 sha1( final byte[] bytes ) throws IOException {
    checkNotNull( bytes ) ;
    final MessageDigest digest = newSha1MessageDigest() ;
    return new Sha1( digest.digest( bytes ) ) ;
  }

  public static MessageDigest newSha1MessageDigest() {
    final MessageDigest digest ;
    try {
      digest = MessageDigest.getInstance( "SHA1" ) ;
    } catch( final NoSuchAlgorithmException e ) {
      throw new Error( "Can't find SHA1 implementation", e ) ;
    }
    return digest ;
  }



// ======
// SHA256
// ======

  public static final class Sha256 extends AbstractDigest {

    public static final int BYTE_COUNT = 32 ;

    public Sha256( final byte[] bytes ) {
      super( BYTE_COUNT, bytes ) ;
    }

    public Sha256( final String base64 ) {
      super( BYTE_COUNT, base64 ) ;
    }

    public static Sha256 parseBase64( final String string ) {
      return AbstractDigest.isValidBase64( BYTE_COUNT, string ) ? new Sha256( string ) : null ;
    }
  }


  public static Sha256 sha256( final File file ) throws IOException {
    final MessageDigest sha256Digest = newSha256MessageDigest() ;
    return new Sha256( hash( file, sha256Digest ) ) ;
  }

  public static MessageDigest newSha256MessageDigest() {
    final MessageDigest sha256Digest ;
    try {
      sha256Digest = MessageDigest.getInstance( "SHA-256" ) ;
    } catch( final NoSuchAlgorithmException e ) {
      throw new Error( "Can't find SHA256 implementation", e ) ;
    }
    return sha256Digest ;
  }



// =====
// Other
// =====

  private static final String HEXES = "0123456789abcdef" ;

  /**
   * http://www.rgagnon.com/javadetails/java-0596.html
   */
  public static String toHex( final byte[] bytes ) {
    if( bytes == null ) {
      return null ;
    }
    final StringBuilder hex = new StringBuilder( 2 * bytes.length ) ;
    for( final byte b : bytes ) {
      hex.append( HEXES.charAt( ( b & 0xF0 ) >> 4 ) ).append( HEXES.charAt( ( b & 0x0F ) ) ) ;
    }
    return hex.toString() ;
  }

  public static byte[] hash( final File file, final MessageDigest digest ) throws IOException {
    try(
        final FileInputStream inputStream = new FileInputStream( file ) ;
        final FileChannel channel = inputStream.getChannel()
    ) {
      final ByteBuffer buffer = ByteBuffer.allocate( 8192 ) ;
      while( channel.read( buffer ) != -1 ) {
        buffer.flip() ;
        digest.update( buffer ) ;
        buffer.clear() ;
      }
      final byte[] hashValue = digest.digest() ;
      return hashValue ;
    }
  }

  public static abstract class AbstractDigest {
    private final byte[] digestBytes ;

    protected AbstractDigest( final int byteCount, final byte[] digestBytes ) {
      checkArgument( digestBytes.length == byteCount,
          "Got " + digestBytes.length + " instead of " + byteCount ) ;
      this.digestBytes = digestBytes.clone() ;
    }

    protected AbstractDigest( final int byteCount, final String base64 ) {
      this( byteCount, java.util.Base64.getDecoder().decode( base64 ) ) ;
    }

    public String base64() {
      return java.util.Base64.getEncoder().encodeToString( digestBytes ) ;
    }

    public String hex() {
      return toHex( digestBytes ) ;
    }

    public byte[] bytes() {
      return digestBytes.clone() ;
    }

    public static boolean isValidBase64( final int initialLength, final String string ) {
      final int padding ;
      final String regexTail ;
      switch( ( initialLength ) % 3 ) {
        case 1 :
          padding = 2 ;
          regexTail = "==" ;
          break ;
        case 2 :
          padding = 1 ;
          regexTail = "=" ;
          break ;
        default :
          padding = 0 ;
          regexTail = "" ;
      }
      final int encodedLength = ( ( ( initialLength / 3 ) + ( padding > 0 ? 1 : 0 ) ) * 4 ) ;
      final String regex = "[a-zA-Z0-9/\\+]{" + ( encodedLength - padding ) + "}" + regexTail ;
      return Pattern.compile( regex ).matcher( string ).matches() ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{" + base64() + "}" ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }

      final AbstractDigest that = ( AbstractDigest ) other ;

      if( ! Arrays.equals( this.digestBytes, that.digestBytes ) ) {
        return false ;
      }

      return true ;
    }

    @Override
    public int hashCode() {
      return hex().hashCode() ;
    }
  }


}
