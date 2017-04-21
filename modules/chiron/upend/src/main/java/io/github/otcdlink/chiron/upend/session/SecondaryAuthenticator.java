package io.github.otcdlink.chiron.upend.session;

import io.github.otcdlink.chiron.middle.PhoneNumber;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;
import io.github.otcdlink.chiron.upend.session.twilio.AuthenticationFailureNotice;
import io.netty.channel.Channel;

/**
 * Describes the behavior of a secondary authentication system in two-factor authentication.
 *
 * <h2>Threading</h2>
 * Callbacks (namely {@link SecondaryTokenCallback} and {@link VerificationCallback}) happen
 * in any thread (probably a Netty's {@link Channel}) so caller should take care of wrapping
 * them in some {@code Executor}-like call.
 */
public interface SecondaryAuthenticator {

  /**
   * Requests a phone call.
   * @param userPhoneNumber to get in touch with a human user.
   * @return a non-{@code null} value.
   */
  void requestAuthentication(
      PhoneNumber userPhoneNumber,
      SecondaryTokenCallback callback
  ) ;

  interface SecondaryTokenCallback {
    void secondaryToken( SecondaryToken secondaryToken ) ;
  }


  /**
   *
   * @param secondaryToken the value returned by
   *     {@link #requestAuthentication(PhoneNumber, SecondaryTokenCallback)}.
   * @param secondaryCode what user did enter as additional authentification token.
   */
  void verifySecondaryCode(
      SecondaryToken secondaryToken,
      SecondaryCode secondaryCode,
      VerificationCallback callback
  ) ;

  interface VerificationCallback {

    /**
     * @param authenticationFailureNotice null if {@code userEnteredToken} matches with
     *     the one sent, the reason for which the match didn't happen otherwise (can be Twilio
     *     failure or whatever).
     */
    void secondaryAuthenticationResult( AuthenticationFailureNotice authenticationFailureNotice ) ;
  }
}
