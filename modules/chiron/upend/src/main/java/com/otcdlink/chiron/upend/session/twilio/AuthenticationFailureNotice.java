package com.otcdlink.chiron.upend.session.twilio;

import com.otcdlink.chiron.middle.TypedNotice;

public class AuthenticationFailureNotice extends TypedNotice< AuthenticationFailure > {

  public AuthenticationFailureNotice( final AuthenticationFailure authenticationFailure ) {
    super( authenticationFailure ) ;
  }

  public AuthenticationFailureNotice(
      final AuthenticationFailure authenticationFailure,
      final String message
  ) {
    super( authenticationFailure, message ) ;
  }
}
