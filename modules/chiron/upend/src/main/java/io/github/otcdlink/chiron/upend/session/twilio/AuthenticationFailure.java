package io.github.otcdlink.chiron.upend.session.twilio;

import io.github.otcdlink.chiron.middle.EnumeratedMessageKind;
import io.github.otcdlink.chiron.toolbox.EnumTools;

public enum AuthenticationFailure implements EnumeratedMessageKind {

  PHONE_CALL_NOT_EVEN_STARTED( TwilioAuthenticationFailure.PHONE_CALL_NOT_EVEN_STARTED ),
  PHONE_STILL_RINGING( TwilioAuthenticationFailure.PHONE_STILL_RINGING ),
  SECONDARY_AUTHENTICATION_STILL_IN_PROGRESS(
      TwilioAuthenticationFailure.SECONDARY_AUTHENTICATION_STILL_IN_PROGRESS ),
  USER_PHONE_BUSY( TwilioAuthenticationFailure.USER_PHONE_BUSY ),
  PHONE_CALL_FAILED( TwilioAuthenticationFailure.PHONE_CALL_FAILED ),
  USER_DID_NOT_ANSWER( TwilioAuthenticationFailure.USER_DID_NOT_ANSWER ),
  SECONDARY_AUTHENTICATION_CANCELLED(
      TwilioAuthenticationFailure.SECONDARY_AUTHENTICATION_CANCELLED ),
  INTERNAL_ERROR( TwilioAuthenticationFailure.INTERNAL_ERROR ),
  SECONDARY_AUTHENTICATION_NOT_HAPPENED_YET(
      TwilioAuthenticationFailure.SECONDARY_AUTHENTICATION_NOT_HAPPENED_YET ),

  UNKNOWN_SECONDARY_TOKEN( "Unknown Secondary Token" ),
  INCORRECT_CODE( "Incorrect code" ),

  ;

  private final EnumeratedMessageKind ancestor ;
  private final String description ;

  AuthenticationFailure( final String description ) {
    this.description = description ;
    this.ancestor = null ;
  }

  AuthenticationFailure( final TwilioAuthenticationFailure ancestor ) {
    this.ancestor = ancestor ;
    this.description = ancestor.description() ;
  }

  @Override
  public String description() {
    return description ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' + ( ancestor == null ? description : ancestor ) + '}' ;
  }

  static {
    EnumTools.checkEnumExtender(
        TwilioAuthenticationFailure.values(),
        AuthenticationFailure.values()
    ) ;
  }

  public static AuthenticationFailure from(
      final TwilioAuthenticationFailure twilioAuthenticationFailure
  ) {
    // Safe because of enum inheritance makes that possible.
    return valueOf( twilioAuthenticationFailure.name() ) ;
  }

}
