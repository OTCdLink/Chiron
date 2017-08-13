package com.otcdlink.chiron.upend.twilio;

import io.github.caillette.shoemaker.upend.twilio.TwilioSecondaryAuthenticator;

/**
 * What {@link TwilioHttpCallback} sees from {@link TwilioSecondaryAuthenticator}.
 */
public interface TwilioHttpCallback {

  String URL_SEGMENT_TWIML = "/twiml" ;
  String URL_SEGMENT_STATUS = "/status" ;
  String URL_SEGMENT_FALLBACK = "/fallback" ;

  void updateTwilioCallStatus( String urlToken, TwilioStatus status ) ;

  String createTwiml( String urlToken ) ;

}
