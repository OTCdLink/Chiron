package io.github.otcdlink.chiron.upend.twilio;

import io.github.otcdlink.chiron.middle.PhoneNumber;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;

/**
 * Holds various tokens used during secondary authentication.
 */
public interface TokenPack {
  /**
   * Identifies ongoing authentication request in a manner that User Agent may see.
   * @return a non-null, non-empty {@code String}.
   */
  SecondaryToken signonEnrichmentToken() ;

  /**
   * Returns the token that human user must enter after receiving Twilio call.
   * @return a non-null, non-empty {@code String}.
   */
  SecondaryCode tokenExpectedFromUser() ;

  /**
   * Returns the token that identifies this {@link TokenPack} in the URL that Twilio accesses
   * to with its callbacks. This must be different of {@link #signonEnrichmentToken()},
   * otherwise any user could find the code he must enter.
   * @return a non-null, non-empty {@code String}.
   */
  String urlToken() ;

  PhoneNumber userPhoneNumber() ;

  /**
   * Must be thread-safe.
   */
  interface Factory {
    TokenPack createNew( PhoneNumber phoneNumber ) ;
  }
}
