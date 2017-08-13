package io.github.caillette.shoemaker.upend.twilio;

import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.upend.session.SecondaryAuthenticator;
import com.otcdlink.chiron.upend.twilio.TwilioHttpCallback;
import org.joda.time.Duration;

import java.net.URL;


/**
 * Stateful implementation of {@link SecondaryAuthenticator} that calls Twilio with asynchronous
 * HTTP requests. There is some Servlet calling this class upon Twilio's HTTP callbacks.
 */
public interface TwilioSecondaryAuthenticator
    extends SecondaryAuthenticator, TwilioHttpCallback
{


  URL URL_BASE_TWILIO_API = UrxTools.parseUrlQuiet(
      "https://api.twilio.com/2010-04-01/Accounts" ) ;


  /**
   * Twilio will leaves callee's phone ring for this delay.
   */
  int TIMEOUT_PHONE_CALL_S = 20 ;


  Duration DEFAULT_HTTP_REQUEST_TIMEOUT = Duration.standardSeconds( 10 ) ;

  Duration DEFAULT_SECONDARYCODE_LIFETIME = Duration.standardMinutes( 2 ) ;

  Duration ABSOLUTE_MINIMUM_CLEANUP_PERIOD = Duration.standardSeconds( 1 ) ;

}
