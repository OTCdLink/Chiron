package io.github.otcdlink.chiron.upend.session;

import io.github.otcdlink.chiron.middle.PhoneNumber;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;

/**
 * Everything {@link SessionSupervisor} should know
 * about the User to which we associate some {@link SessionIdentifier}.
 */
public interface SignableUser {

  String login() ;

  /**
   * Only {@code SecondaryAuthenticator} uses it.
   */
  PhoneNumber phoneNumber() ;

}
