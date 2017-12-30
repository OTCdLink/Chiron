package com.otcdlink.chiron.upend.session.twilio;

import com.otcdlink.chiron.middle.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Credentials for sending REST requests to Twilio online API.
 */
public class TwilioCredential {

  public final String accountSid ;
  public final String authorizationToken ;
  public final PhoneNumber callerId ;

  private static final Logger LOGGER = LoggerFactory.getLogger( TwilioCredential.class ) ;

  private static final Pattern PATTERN =
      Pattern.compile( "(AC[0-9a-zA-Z]+):([0-9a-zA-Z]+):(" + PhoneNumber.PATTERN + ")" ) ;
  static {
    LOGGER.trace( "Crafted regex " + PATTERN.pattern() ) ;
  }


  public TwilioCredential( final String allInOne ) {
    this( getGroups( allInOne, PATTERN.matcher( allInOne ) ) ) ;
  }

  private static String[] getGroups( final String originalSequence, final Matcher matcher ) {
    if( ! matcher.matches() ) {
      throw new TwilioCredentialDefinitionException( originalSequence ) ;
    }
    final String[] groups = new String[ matcher.groupCount() ] ;
    for( int i = 0 ; i < groups.length ; i ++ ) {
      groups[ i ] = matcher.group( i + 1 ) ;
    }
    return groups ;
  }

  private TwilioCredential( final String[] values ) {
    this( values[ 0 ], values[ 1 ], values[ 2 ] ) ;
  }

  public TwilioCredential(
      final String accountSid,
      final String authorizationToken,
      final String callerId
  ) {
    this( accountSid, authorizationToken, new PhoneNumber( callerId ) ) ;
  }

  public TwilioCredential(
      final String accountSid,
      final String authorizationToken,
      final PhoneNumber callerId
  ) throws TwilioCredentialDefinitionException {
    if( accountSid == null ) {
      throw new TwilioCredentialDefinitionException( "accountSid" ) ;
    }
    if( authorizationToken == null ) {
      throw new TwilioCredentialDefinitionException( "authorizationToken" ) ;
    }
    if( callerId == null ) {
      throw new TwilioCredentialDefinitionException( "callerId" ) ;
    }

    this.accountSid = accountSid;
    this.authorizationToken = authorizationToken ;
    this.callerId = callerId ;
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' + asString() + '}' ;
  }

  public String asString() {
    return accountSid + ":" + authorizationToken + ":" + callerId ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) return true ;
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final TwilioCredential that = ( TwilioCredential ) other ;

    if( !accountSid.equals( that.accountSid ) ) {
      return false ;
    }
    if( !authorizationToken.equals( that.authorizationToken ) ) {
      return false ;
    }
    if( !callerId.equals( that.callerId ) ) {
      return false ;
    }

    return true ;
  }

  @Override
  public int hashCode() {
    int result = accountSid.hashCode() ;
    result = 31 * result + authorizationToken.hashCode() ;
    result = 31 * result + callerId.hashCode() ;
    return result ;
  }
}
