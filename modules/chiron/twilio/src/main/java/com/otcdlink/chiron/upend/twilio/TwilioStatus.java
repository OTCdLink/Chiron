package com.otcdlink.chiron.upend.twilio;

/**
 * Status emitted by Twilio.
 *
 * Elements starting by {@code CALL_} are
 * <a href="https://www.twilio.com/docs/api/twiml/twilio_request#synchronous-request-parameters" >call status</a>.
 *
 */
public enum TwilioStatus {
  /**
   * The call is ready and waiting in line before going out.
   */
  CALL_QUEUED( "queued" ),

  /**
   * The call is currently ringing.
   */
  CALL_RINGING( "ringing" ),

  /**
   * The call was answered and is currently in progress.
   */
  CALL_IN_PROGRESS( "in-progress" ),

  /**
   * The call was answered and has ended normally.
   */
  CALL_COMPLETED( "completed" ),

  /**
   * The caller received a busy signal.
   */
  CALL_BUSY( "busy" ),

  /**
   * The call could not be completed as dialed, most likely because the phone number was
   * non-existent.
   */
  CALL_FAILED( "failed" ),

  /**
   * The call ended without being answered.
   */
  CALL_NO_ANSWER( "no-answer" ),

  /**
   * The call was canceled via the REST API while queued or ringing.
   */
  CALL_CANCELED( "canceled" ),


  /**
   * Means that Twilio issued a call to get the TwiML from our application.
   */
  TWIML_REQUESTED( null ),

  ;

  public final String specifiedName ;

  private TwilioStatus( final String specifiedName ) {
    this.specifiedName = specifiedName ;
  }

  public static TwilioStatus fromSpecifiedName( final String value ) {
    for( final TwilioStatus callStatus : values() ) {
      if( callStatus.specifiedName.equals( value ) ) {
        return callStatus ;
      }
    }
    return null ;
  }
}
