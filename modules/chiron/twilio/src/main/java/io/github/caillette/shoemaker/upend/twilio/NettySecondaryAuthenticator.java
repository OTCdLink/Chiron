package io.github.caillette.shoemaker.upend.twilio;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Sets;
import io.github.otcdlink.chiron.middle.PhoneNumber;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.clock.Clock;
import io.github.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import io.github.otcdlink.chiron.toolbox.netty.Hypermessage;
import io.github.otcdlink.chiron.toolbox.netty.InputOutputLifecycled;
import io.github.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import io.github.otcdlink.chiron.toolbox.security.SslEngineFactory;
import io.github.otcdlink.chiron.upend.session.SecondaryAuthenticator;
import io.github.otcdlink.chiron.upend.session.twilio.AuthenticationFailure;
import io.github.otcdlink.chiron.upend.session.twilio.AuthenticationFailureNotice;
import io.github.otcdlink.chiron.upend.session.twilio.TwilioAuthenticationFailure;
import io.github.otcdlink.chiron.upend.session.twilio.TwilioCredential;
import io.github.otcdlink.chiron.upend.twilio.TokenPack;
import io.github.otcdlink.chiron.upend.twilio.TwilioStatus;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Stateful implementation of {@link SecondaryAuthenticator} that calls Twilio with asynchronous
 * HTTP requests. There is some Servlet calling this class upon Twilio's HTTP callbacks.
 */
public class NettySecondaryAuthenticator implements TwilioSecondaryAuthenticator {
  private static final Logger LOGGER =
      LoggerFactory.getLogger( NettySecondaryAuthenticator.class ) ;

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

  private final Clock clock ;

  private final URL httpCallbackRadix ;
  private final URL urlBaseTwilioApi ;
  private final TwilioCredential twilioCredential ;

  /**
   * Delegates all its state to the {@link EventLoopGroup} so it doesn't need to be
   * {@link InputOutputLifecycled#stop()}'ed.
   */
  private final NettyHttpClient nettyHttpClient ;

  private final Duration secondaryCodeMaximumAge ;


  public NettySecondaryAuthenticator(
      final Clock clock,
      final EventLoopGroup eventLoopGroup,
      final SslEngineFactory.ForClient sslEngineFactory,
      final TokenPack.Factory tokenPackFactory,
      final URL urlBaseTwilioApi,
      final URL httpCallbackRadix,
      final TwilioCredential twilioCredential,
      final Duration httpRequestTimeout,
      final Duration secondaryCodeMaximumAge
  ) {
    this.clock = checkNotNull( clock ) ;
    this.urlBaseTwilioApi = checkNotNull( urlBaseTwilioApi ) ;
    this.ongoingCallFactory = checkNotNull( tokenPackFactory ) ;
    this.httpCallbackRadix = checkNotNull( httpCallbackRadix ) ;
    this.twilioCredential = checkNotNull( twilioCredential ) ;
    this.secondaryCodeMaximumAge = checkNotNull( secondaryCodeMaximumAge ) ;


    checkHttpsConsistency( urlBaseTwilioApi, sslEngineFactory ) ;

    this.nettyHttpClient = new NettyHttpClient(
        eventLoopGroup,
        ( int ) httpRequestTimeout.getMillis(),
        sslEngineFactory
    ) ;
    this.nettyHttpClient.start() ;  // No networking involved, no need to join().
  }

  private static void checkHttpsConsistency(
      final URL urlBaseTwilioApi,
      final SslEngineFactory.ForClient sslEngineFactory
  ) {
    final SchemeHostPort.Scheme scheme =
        SchemeHostPort.Scheme.resolve( urlBaseTwilioApi.getProtocol() ) ;

    if( scheme.secure ) {
      checkNotNull(
          sslEngineFactory,
          "Use of HTTPS for " + urlBaseTwilioApi.toExternalForm() + " implies an " +
              SslEngineFactory.class.getSimpleName()
      ) ;
    } else {
      checkArgument(
          sslEngineFactory == null,
          "Use of HTTP for " + urlBaseTwilioApi.toExternalForm() + " implies no " +
              SslEngineFactory.class.getSimpleName()
      ) ;
    }
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

// =================================
// Send HTTP request to Twilio Voice
// =================================

  /**
   * {@inheritDoc}
   */
  @Override
  public void requestAuthentication(
      final PhoneNumber userPhoneNumber,
      final SecondaryTokenCallback callback
  ) {

    final TokenPack tokenPack = ongoingCallFactory.createNew( userPhoneNumber ) ;
    final CallStatus callStatus = new CallStatus( tokenPack, clock.getCurrentDateTime() ) ;

    LOGGER.info( "Requesting authentication for " + userPhoneNumber +
        " using " + tokenPack + " ..." ) ;

    synchronized( callStatuses ) {
      callStatuses.add( callStatus ) ;
    }

    final Hypermessage.Request.Post postRequest = new Hypermessage.Request.Post(
        createRestUrl(),
        createHeaderMap(),
        createParameterMap( tokenPack )
    ) ;
    final CompletableFuture< NettyHttpClient.CompleteResponse > closeFuture =
        nettyHttpClient.httpRequest( postRequest ) ;
    closeFuture.handle( ( completeResponse, throwable ) -> {
      if( throwable != null ) {
        LOGGER.warn( "Failed to send " + postRequest + ".", throwable ) ;
      } else {
        callback.secondaryToken( tokenPack.signonEnrichmentToken() ) ;
      }
      return completeResponse ;
    } ) ;

    callback.secondaryToken( tokenPack.signonEnrichmentToken() ) ;
  }

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

  private ImmutableMultimap< String, String > createHeaderMap() {
    final String userPasswordClearText =
        twilioCredential.accountSid + ":" + twilioCredential.authorizationToken ;
    final String usePasswordBase64 = Base64.getEncoder().encodeToString(
        userPasswordClearText.getBytes( Charsets.US_ASCII ) ) ;
    return ImmutableMultimap.< String, String >builder()
        .put( HttpHeaderNames.AUTHORIZATION.toString(), "Basic " + usePasswordBase64 )
        .put( HttpHeaderNames.HOST.toString(), Hypermessage.Request.MAGIC_HEADER_HOST )
        .build()
    ;
  }

  private ImmutableMultimap< String, String > createParameterMap( final TokenPack tokenPack ) {
    return ImmutableMultimap.< String, String >builder()
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


// ============
// Create Twiml
// ============


  /**
   * The servlet in charge for handling Twilio's HTTP synchronous calls calls this method.
   * TODO: make Twiml generation pluggable.
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

// =====================
// Verify Secondary Code
// =====================

  /**
   * {@inheritDoc}
   */
  @Override
  public void verifySecondaryCode(
      final SecondaryToken secondaryToken,
      final SecondaryCode secondaryCode,
      final VerificationCallback callback
  ) {
    callback.secondaryAuthenticationResult(
        verifyUserEnteredToken( secondaryToken, secondaryCode ) ) ;
  }

  private AuthenticationFailureNotice verifyUserEnteredToken(
      final SecondaryToken signonEnrichmentToken,
      final SecondaryCode userEnteredSecondaryCode
  ) {
    synchronized( callStatuses ) {
      cleanup() ;
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


// ===========
// Miscellanea
// ===========

  /**
   * This method updates a {@link CallStatus} at Twiml creation, and when Twilio calls
   * our HTTP responder for updating call status.
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
   * Remove expired {@link CallStatus}s.
   */
  void cleanup() {
    final DateTime earliestAllowedStartTime =
        clock.getCurrentDateTime().minus( secondaryCodeMaximumAge ) ;
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
