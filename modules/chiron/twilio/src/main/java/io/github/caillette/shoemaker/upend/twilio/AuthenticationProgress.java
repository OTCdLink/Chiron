package io.github.caillette.shoemaker.upend.twilio;

import io.github.otcdlink.chiron.upend.session.twilio.TwilioAuthenticationFailure;
import io.github.otcdlink.chiron.upend.twilio.TwilioStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the general state of a {@link CallStatus#twilioStatus} as a superset of
 * {@link TwilioStatus}.
 *
 * <pre>
 * STARTED  --> BROKEN
 *    |
 *    v
 * CALL_COMPLETE
 * </pre>
 */
enum AuthenticationProgress {

  /**
   * Means {@link CallStatus#twilioStatus} may be null.
   */
  STARTED {
    @Override
    public TwilioAuthenticationFailure getValidity( final TwilioStatus twilioStatus ) {
      if( twilioStatus == null ) {
        return TwilioAuthenticationFailure.SECONDARY_AUTHENTICATION_NOT_HAPPENED_YET ;
      }
      switch( twilioStatus ) {
        case CALL_COMPLETED:
          /** We should never get here because we jumped to {@link #CALL_COMPLETE} before. */
          return null ;
        case CALL_QUEUED :
          return TwilioAuthenticationFailure.PHONE_CALL_NOT_EVEN_STARTED ;
        case CALL_RINGING :
          return TwilioAuthenticationFailure.PHONE_STILL_RINGING ;
        case CALL_IN_PROGRESS :
          return TwilioAuthenticationFailure.SECONDARY_AUTHENTICATION_STILL_IN_PROGRESS ;
        case CALL_BUSY :
          return TwilioAuthenticationFailure.USER_PHONE_BUSY ;
        case CALL_FAILED :
          return TwilioAuthenticationFailure.PHONE_CALL_FAILED ;
        case CALL_NO_ANSWER :
          return TwilioAuthenticationFailure.USER_DID_NOT_ANSWER ;
        case CALL_CANCELED :
          return TwilioAuthenticationFailure.SECONDARY_AUTHENTICATION_CANCELLED ;
        default :
          LOGGER.error( "Unsupported value: " + twilioStatus ) ;
          return TwilioAuthenticationFailure.INTERNAL_ERROR ;
      }
    }
  },

  /**
   * Means {@link CallStatus#twilioStatus} is not null.
   */
  CALL_COMPLETE {
    @Override
    public TwilioAuthenticationFailure getValidity( final TwilioStatus twilioStatus ) {
      return null ;
    }
  },

  /**
   * Means {@link CallStatus#twilioStatus} may be null, some kind of error happend.
   */
  BROKEN {
    @Override
    public TwilioAuthenticationFailure getValidity( final TwilioStatus twilioStatus ) {
      return TwilioAuthenticationFailure.INTERNAL_ERROR ;
    }
  } ;

  private static final Logger LOGGER = LoggerFactory.getLogger( AuthenticationProgress.class ) ;

  public abstract TwilioAuthenticationFailure getValidity( final TwilioStatus twilioStatus ) ;

}
