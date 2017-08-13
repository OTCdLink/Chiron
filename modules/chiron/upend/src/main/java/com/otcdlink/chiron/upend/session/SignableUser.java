package com.otcdlink.chiron.upend.session;

import com.otcdlink.chiron.middle.PhoneNumber;
import com.otcdlink.chiron.middle.session.SessionIdentifier;

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
