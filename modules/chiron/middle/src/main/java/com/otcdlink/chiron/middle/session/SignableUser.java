package com.otcdlink.chiron.middle.session;

import com.otcdlink.chiron.middle.PhoneNumber;

/**
 * Everything {@code SessionSupervisor} should know
 * about the User to which we associate some {@link SessionIdentifier}.
 * <p>
 * This class appears in {@link com.otcdlink.chiron.middle} package for implementors who need
 * an implementation that goes to both ends (like a graphical user interface for User
 * administration).
 */
public interface SignableUser {

  String login() ;

  /**
   * Only {@code SecondaryAuthenticator} uses it.
   */
  PhoneNumber phoneNumber() ;

}
