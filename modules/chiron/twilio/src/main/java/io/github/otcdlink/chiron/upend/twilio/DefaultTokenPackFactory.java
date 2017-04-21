package io.github.otcdlink.chiron.upend.twilio;

import io.github.otcdlink.chiron.middle.PhoneNumber;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;

import java.security.SecureRandom;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DefaultTokenPackFactory implements TokenPack.Factory {

  private final SecureRandom secureRandom = new SecureRandom() ;

  @Override
  public TokenPack createNew( final PhoneNumber phoneNumber ) {
    return new DefaultTokenPack(
        new SecondaryToken( "AUL-" + generateAlpha( secureRandom, 12 ) ),
        new SecondaryCode( generateNumbers( secureRandom, 6 ) ),
        "AUT-" + generateAlpha( secureRandom, 16 ),
        phoneNumber
    ) ;
  }

  private static class DefaultTokenPack implements TokenPack {

    private final SecondaryToken signonEnrichmentToken;
    private final SecondaryCode tokenExpectedFromUser ;
    private final String urlToken ;
    private final PhoneNumber phoneNumber ;

    private DefaultTokenPack(
        final SecondaryToken signonEnrichmentToken,
        final SecondaryCode tokenExpectedFromUser,
        final String urlToken,
        final PhoneNumber phoneNumber
    ) {
      checkArgument( ! urlToken.isEmpty(), "%s", urlToken ) ;
      checkNotNull( phoneNumber ) ;
      this.signonEnrichmentToken = checkNotNull( signonEnrichmentToken ) ;
      this.tokenExpectedFromUser = checkNotNull( tokenExpectedFromUser ) ;
      this.urlToken = urlToken ;
      this.phoneNumber = phoneNumber ;
    }

    @Override
    public SecondaryToken signonEnrichmentToken() {
      return signonEnrichmentToken;
    }

    @Override
    public SecondaryCode tokenExpectedFromUser() {
      return tokenExpectedFromUser ;
    }

    @Override
    public String urlToken() {
      return urlToken ;
    }

    @Override
    public PhoneNumber userPhoneNumber() {
      return phoneNumber ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{"
          + "loginEnrichmentToken=" + signonEnrichmentToken + ";"
          + "tokenExpectedFromUser=" + tokenExpectedFromUser + ";"
          + "urlToken=" + urlToken + ";"
          + "phoneNumber=" + phoneNumber
          + "}"
      ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }

      final DefaultTokenPack that = ( DefaultTokenPack ) other;

      if( !signonEnrichmentToken.equals( that.signonEnrichmentToken ) ) {
        return false ;
      }
      if( !phoneNumber.equals( that.phoneNumber ) ) {
        return false ;
      }
      if( !tokenExpectedFromUser.equals( that.tokenExpectedFromUser ) ) {
        return false ;
      }
      if( !urlToken.equals( that.urlToken ) ) {
        return false ;
      }

      return true ;
    }

    @Override
    public int hashCode() {
      int result = signonEnrichmentToken.hashCode() ;
      result = 31 * result + tokenExpectedFromUser.hashCode() ;
      result = 31 * result + urlToken.hashCode() ;
      result = 31 * result + phoneNumber.hashCode() ;
      return result ;
    }
  }



  public static String generateNumbers( final SecureRandom random, final int numberOfDigits ) {
    final char[] chars = new char[ numberOfDigits ] ;
    for( int i = 0 ; i < numberOfDigits ; i ++ ) {
      final int digit = random.nextInt( 10 ) ;
      chars[ i ] = ( char ) ( ( ( int ) '0' ) + digit ) ;
    }
    return new String( chars ) ;
  }

  public static String generateAlpha( final SecureRandom random, final int numberOfChars ) {
    final char[] chars = new char[ numberOfChars ] ;
    for( int i = 0 ; i < numberOfChars ; i ++ ) {
      final int number = random.nextInt( 62 ) ;
      final char digit ;
      if( number < 10 ) {
        digit = ( char ) ( ( ( int ) '0' ) + number ) ;
      } else if( number < 36 ) {
        digit = ( char ) ( ( ( int ) 'A' ) + number - 10 ) ;
      } else {
        digit = ( char ) ( ( ( int ) 'a' ) + number - 36 ) ;
      }
      chars[ i ] = digit ;
    }
    return new String( chars ) ;
  }

  public static void main( final String... arguments ) {
    final SecureRandom random = new SecureRandom() ;
    for( int i = 0 ; i < 1000 ; i ++ ) {
      System.out.println( generateAlpha( random, 100 ) ) ;
    }
  }
}
