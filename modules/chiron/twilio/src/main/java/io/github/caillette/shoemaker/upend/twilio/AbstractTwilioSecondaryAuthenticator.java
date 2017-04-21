package io.github.caillette.shoemaker.upend.twilio;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.github.otcdlink.chiron.middle.PhoneNumber;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.clock.Clock;
import io.github.otcdlink.chiron.upend.session.SecondaryAuthenticator;
import io.github.otcdlink.chiron.upend.session.twilio.AuthenticationFailure;
import io.github.otcdlink.chiron.upend.session.twilio.AuthenticationFailureNotice;
import io.github.otcdlink.chiron.upend.session.twilio.TwilioAuthenticationFailure;
import io.github.otcdlink.chiron.upend.session.twilio.TwilioCredential;
import io.github.otcdlink.chiron.upend.twilio.TokenPack;
import io.github.otcdlink.chiron.upend.twilio.TwilioHttpCallback;
import io.github.otcdlink.chiron.upend.twilio.TwilioStatus;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Stateful implementation of {@link SecondaryAuthenticator} that calls Twilio with asynchronous
 * HTTP requests. There is some Servlet calling this class upon Twilio's HTTP callbacks.
 */
public abstract class AbstractTwilioSecondaryAuthenticator
    implements SecondaryAuthenticator, TwilioHttpCallback
{
  private static final Logger LOGGER =
      LoggerFactory.getLogger( AbstractTwilioSecondaryAuthenticator.class ) ;

  /**
   * Synchronize access to the collection and its element on the collection itself.
   * This is not a {@code Map} since we perform lookups on {@link TokenPack#urlToken()}
   * and {@link TokenPack#signonEnrichmentToken()}.
   * Using a {@link com.google.common.collect.Table} would probably be clearer.
   *
   * @see #findByLoginEnrichmentToken(Set, SecondaryToken)
   * @see #findByUrlToken(String)
   */
  private final Set< CallStatus > callStatuses = Sets.newHashSet() ;

  private final TokenPack.Factory ongoingCallFactory ;

  /**
   * Also used for running asynchronous callbacks of {@link SecondaryAuthenticator}.
   */
  private final Executor restRequestExecutor ;

  private final Clock clock ;


  private final URL httpCallbackRadix ;
  private final URL urlBaseTwilioApi ;
  private final TwilioCredential twilioCredential ;


  /**
   *
   * @param restRequestExecutor an {@code Executor} allowing many, many threads (100 or more)
   *     for blocking calls.
   *
   */
  public AbstractTwilioSecondaryAuthenticator(
      final Clock clock,
      final TokenPack.Factory tokenPackFactory,
      final URL urlBaseTwilioApi,
      final URL httpCallbackRadix,
      final Executor restRequestExecutor,
      final TwilioCredential twilioCredential
  ) {
    this.clock = checkNotNull( clock ) ;
    this.urlBaseTwilioApi = checkNotNull( urlBaseTwilioApi ) ;
    this.ongoingCallFactory = checkNotNull( tokenPackFactory ) ;
    this.restRequestExecutor = checkNotNull( restRequestExecutor ) ;
    this.httpCallbackRadix = checkNotNull( httpCallbackRadix ) ;
    this.twilioCredential = checkNotNull( twilioCredential ) ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' +
        "twilioCredential.accountSid=" + twilioCredential.accountSid + ';' +
        "urlBaseTwilioApi=" + urlBaseTwilioApi.toExternalForm() + ';' +
        "httpCallbackRadix=" + httpCallbackRadix.toExternalForm() +
        '}'
    ;
  }


  @Override
  public void requestAuthentication(
      final PhoneNumber userPhoneNumber,
      final SecondaryTokenCallback callback
  ) {
    restRequestExecutor.execute( () -> callback.secondaryToken(
        requestAuthentication( userPhoneNumber ) ) ) ;
  }


  private SecondaryToken requestAuthentication( final PhoneNumber userAddress ) {

    final TokenPack tokenPack = ongoingCallFactory.createNew( userAddress ) ;
    final CallStatus callStatus = new CallStatus( tokenPack, clock.getCurrentDateTime() ) ;

    LOGGER.info( "Requesting authentication for " + userAddress + " using " + tokenPack + " ..." ) ;

    synchronized( callStatuses ) {
      callStatuses.add( callStatus ) ;
    }

    restRequestExecutor.execute( () -> executeRestCallWithApacheClient( callStatus ) ) ;

    return tokenPack.signonEnrichmentToken() ;
  }

  protected abstract void executeRestCallWithApacheClient( final CallStatus callStatus ) ;

  private URL createRestUrl() {
    try {
      return new URL(
          urlBaseTwilioApi.toExternalForm() + "/"
          + twilioCredential.accountSid + "/"
          + "Calls.json"
      ) ;
    } catch( final MalformedURLException e ) {
      throw new RuntimeException( "Should not happen", e ) ;
    }
  }

  private ImmutableMap< String, String > createParameterMap( final TokenPack tokenPack ) {
    return ImmutableMap.< String, String >builder()
        .put( "From", twilioCredential.callerId.getAsString() )
        .put( "To", tokenPack.userPhoneNumber().getAsString() )
        .put(
            "Url",
            httpCallbackRadix.toExternalForm() + hack( URL_SEGMENT_TWIML )
                + "/" + tokenPack.urlToken()
        )
//        .put( "FallbackUrl", httpCallbackRadix.toExternalForm() + URL_FRAGMENT_FALLBACK )
        .put( "StatusCallback", httpCallbackRadix.toExternalForm() + URL_SEGMENT_STATUS
            + "/" + tokenPack.urlToken() )
        .put( "Timeout", "" + TIMEOUT_PHONE_CALL_S )
        .build()
    ;
  }

  /**
   * Useful for interactive testing: Twimlets don't follow our URL convention.
   */
  private String hack( final String urlFragmentTwiml ) {
    return httpCallbackRadix.getHost().equals( "twimlets.com" ) ? "" : urlFragmentTwiml ;
  }

  @Override
  public void verifySecondaryCode(
      final SecondaryToken secondaryToken,
      final SecondaryCode secondaryCode,
      final VerificationCallback callback
  ) {
    restRequestExecutor.execute( () -> callback.secondaryAuthenticationResult(
        verifyUserEnteredToken( secondaryToken, secondaryCode ) ) ) ;
  }

  private AuthenticationFailureNotice verifyUserEnteredToken(
      final SecondaryToken signonEnrichmentToken,
      final SecondaryCode userEnteredSecondaryCode
  ) {
    synchronized( callStatuses ) {
      final CallStatus callStatus =
          findByLoginEnrichmentToken( callStatuses, signonEnrichmentToken ) ;
      if( callStatus == null ) {
        LOGGER.warn( "Unknown signon token: '" + signonEnrichmentToken + "'." ) ;
        return new AuthenticationFailureNotice( AuthenticationFailure.UNKNOWN_SECONDARY_TOKEN ) ;
      }
      final TwilioAuthenticationFailure twilioAuthenticationFailure =
          callStatus.computeFailure() ;
      final String message = "user-entered code " + userEnteredSecondaryCode
          + " (paired with signon enrichment token " + signonEnrichmentToken + ") " ;
      if( twilioAuthenticationFailure == null ) {
        if( callStatus.tokenPack.tokenExpectedFromUser().equals( userEnteredSecondaryCode ) ) {
          LOGGER.info( "Successfully verified " + message + "." ) ;
          return null ;
        } else {
          LOGGER.info( "Could not verify " + message + " because of incorrect Secondary token." ) ;
          return new AuthenticationFailureNotice( AuthenticationFailure.INCORRECT_CODE ) ;
        }
      } else {
        LOGGER.info(
            "Verification of " + message + " failed: " + twilioAuthenticationFailure + "." ) ;
        return new AuthenticationFailureNotice(
            AuthenticationFailure.from( twilioAuthenticationFailure ) ) ;
      }
    }
  }

  /**
   * The servlet in charge for handling Twilio's HTTP synchronous calls calls this method.
   */
  @Override
  public void updateTwilioCallStatus( final String urlToken, final TwilioStatus status ) {
    LOGGER.info( "Updating Twilio call status for " + urlToken + " with " + status + "." ) ;
    synchronized( callStatuses ) {
      final CallStatus callStatus = findByUrlToken( urlToken ) ;
      if( callStatus != null ) {
        callStatus.setTwilioStatus( status ) ;
      }
    }
  }

  /**
   * The servlet in charge for handling Twilio's HTTP synchronous calls calls this method.
   */
  @Override
  public String createTwiml( final String urlToken ) {
    final CallStatus callStatus;
    final String twiml ;
    synchronized( callStatuses ) {
      callStatus = findByUrlToken( urlToken ) ;
      updateTwilioCallStatus( urlToken, TwilioStatus.TWIML_REQUESTED ) ;
    }
    if( callStatus == null ) {
      twiml = null ;
    } else {
      // One <Say> element per digit slows the voice down to an acceptable speed.
      final String slowToken = callStatus.tokenPack.tokenExpectedFromUser().asString()
          .replaceAll( "(\\d)", "</Say>" + SAY + "$1</Say>" + SAY ) ;

      twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n"
          + "<Response> \n"
          + SAY
          + "Hello, your secondary code for O-T-C-D-Link is: "
          + slowToken + " \n"
          + "Let me repeat: your O-T-C-D-Link secondary code is "
          + slowToken + " \n"
          + "Again: your O-T-C-D-Link secondary code is "
          + slowToken + " \n"
          + "</Say>\n"
          + "</Response>"
      ;

    }
    LOGGER.debug( "Computed TwiML: " + twiml.replaceAll( "\\n", "" ) ) ;
    return twiml ;
  }

  private static final String SAY = "<Say voice=\"woman\" language=\"en\" >" ;

  /**
   * Remove expired {@link CallStatus}s. Public for tests.
   */
  public void cleanup( final Duration maximumAge ) {
    final DateTime earliestAllowedStartTime = clock.getCurrentDateTime().minus( maximumAge ) ;
    synchronized( callStatuses ) {
      final Iterator< CallStatus > iterator = callStatuses.iterator() ;
      while( iterator.hasNext() ) {
        final CallStatus callStatus = iterator.next() ;
        if( callStatus.startTime.isBefore( earliestAllowedStartTime ) ) {
          LOGGER.info( "Cleaned up " + callStatus + "." ) ;
          iterator.remove() ;
        }
      }
    }
  }

  /**
   * Callers must take care of synchronization.
   */
  private CallStatus findByUrlToken(
      final String urlToken
  ) {
    for( final CallStatus callStatus : callStatuses ) {
      if( callStatus.tokenPack.urlToken().equals( urlToken ) ) {
        return callStatus ;
      }
    }
    LOGGER.warn( "Unknown URL token: '" + urlToken
        + "' (this may happen after restarting the server "
        + "with ongoing Twilio-based authentications)."
    ) ;
    return null ;
  }

  private static CallStatus findByLoginEnrichmentToken(
      final Set< CallStatus > callStatuses,
      final SecondaryToken loginEnrichmentToken
  ) {
    for( final CallStatus callStatus : callStatuses ) {
      if( callStatus.tokenPack.signonEnrichmentToken().equals( loginEnrichmentToken ) ) {
        return callStatus ;
      }
    }
    return null ;
  }

  public static final URL URL_BASE_TWILIO_API = UrxTools.parseUrlQuiet(
      "https://api.twilio.com/2010-04-01/Accounts" ) ;


  /**
   * Twilio will leaves callee's phone ring for this delay.
   */
  private static final int TIMEOUT_PHONE_CALL_S = 20 ;


  public static final Duration DEFAULT_DURATION = Duration.standardMinutes( 2 ) ;

  private static final Duration ABSOLUTE_MINIMUM_CLEANUP_PERIOD = Duration.standardSeconds( 1 ) ;


}
