package io.github.otcdlink.chiron.upend.session.twilio;

import io.github.otcdlink.chiron.middle.PhoneNumber;

/**
 * Thrown by {@link TwilioCredential#TwilioCredential(String, String, PhoneNumber)}.
 */
public class TwilioCredentialDefinitionException extends RuntimeException {

  public TwilioCredentialDefinitionException( final String message ) {
    super( message ) ;
  }
}
