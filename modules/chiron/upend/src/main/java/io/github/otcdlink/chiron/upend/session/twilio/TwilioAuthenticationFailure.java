package io.github.otcdlink.chiron.upend.session.twilio;

import io.github.otcdlink.chiron.middle.EnumeratedMessageKind;

public enum TwilioAuthenticationFailure implements EnumeratedMessageKind {

  PHONE_CALL_NOT_EVEN_STARTED( "Phone call not even started" ),
  PHONE_STILL_RINGING( "Phone still ringing" ),
  SECONDARY_AUTHENTICATION_STILL_IN_PROGRESS( "Secondary Authentication still in progress" ),
  USER_PHONE_BUSY( "User phone busy" ),
  PHONE_CALL_FAILED( "Phone call failed" ),
  USER_DID_NOT_ANSWER( "User didn't answer" ),
  SECONDARY_AUTHENTICATION_CANCELLED( "Secondary Authentication cancelled." ),
  INTERNAL_ERROR( "Internal error. Contact maintenance for further details" ),
  SECONDARY_AUTHENTICATION_NOT_HAPPENED_YET( "Secondary Authentication did not happen yet" ),
  ;

  private final String description ;
  TwilioAuthenticationFailure( final String description ) {
    this.description = description ;
  }

  @Override
  public String description() {
    return description ;
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' + description + '}' ;
  }


}
