package io.github.caillette.shoemaker.upend.twilio;

import com.google.common.collect.ImmutableMultimap;
import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.fixture.http.TinyHttpServer;
import io.github.otcdlink.chiron.fixture.http.VerifyingResponder;
import io.github.otcdlink.chiron.middle.PhoneNumber;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;
import io.github.otcdlink.chiron.toolbox.TcpPortBooker;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.clock.UpdateableClock;
import io.github.otcdlink.chiron.toolbox.internet.Hostname;
import io.github.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import io.github.otcdlink.chiron.toolbox.netty.Hypermessage;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;
import io.github.otcdlink.chiron.upend.TimeKit;
import io.github.otcdlink.chiron.upend.session.SecondaryAuthenticator;
import io.github.otcdlink.chiron.upend.session.twilio.TwilioCredential;
import io.github.otcdlink.chiron.upend.twilio.TokenPack;
import io.github.otcdlink.chiron.upend.twilio.TwilioHttpCallback;
import io.github.otcdlink.chiron.upend.twilio.TwilioStatus;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import mockit.FullVerifications;
import mockit.Injectable;
import org.joda.time.Duration;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractTwilioSecondaryAuthenticatorTest {

  @Test
  public void fullAuthenticationOk(
      @Injectable final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback,
      @Injectable final SecondaryAuthenticator.VerificationCallback verificationCallback
  ) throws Exception {

    final TwilioSecondaryAuthenticator authenticator = createTwilioSecondaryAuthenticator(
        new Duration( 1000 ) ) ;

    try( final TinyHttpServer ignored = createAndStartFakeTwilioVoiceServer() ) {
      successfulVoiceCall( authenticator, secondaryTokenCallback ) ;

      LOGGER.info( "Now User enters the " + TOKEN_PACK.tokenExpectedFromUser() + " ..." ) ;
      authenticator.verifySecondaryCode(
          TOKEN_PACK.signonEnrichmentToken(),
          TOKEN_PACK.tokenExpectedFromUser(),
          verificationCallback
      ) ;

      new FullVerifications() {{
        verificationCallback.secondaryAuthenticationResult( null ) ;
      }} ;

      LOGGER.info( "Verification succeeded for the pair (" + TOKEN_PACK.urlToken() +
          ", " + TOKEN_PACK.tokenExpectedFromUser().asString() + ")." ) ;
    }
  }



// ====================
// Factorised test code
// ====================

  protected void successfulVoiceCall(
      final TwilioSecondaryAuthenticator authenticator,
      final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback
  ) throws VerifyingResponder.ResponderException {
    responder.plan(
        createPostRequest( TWILIO_CREDENTIAL, TOKEN_PACK ),
        new Hypermessage.Response( HttpResponseStatus.CREATED, ImmutableMultimap.of(), "" )
    ) ;
    LOGGER.info( "Triggering Secondary authentication on " + authenticator + " ..." );
    authenticator.requestAuthentication( TOKEN_PACK.userPhoneNumber(), secondaryTokenCallback ) ;

    LOGGER.info( "Now waiting for fake Twilio Voice HTTP server to respond ..." ) ;
    responder.waitForCompletion() ;

    LOGGER.info(
        "Verifying that " + SecondaryAuthenticator.SecondaryTokenCallback.class.getSimpleName() +
        " receives " + TOKEN_PACK.signonEnrichmentToken() + " ... We don't wait for HTTP " +
        "request to complete, any failure should be handled as a timeout."
    ) ;
    new FullVerifications() {{
      secondaryTokenCallback.secondaryToken( TOKEN_PACK.signonEnrichmentToken() ) ;
    }} ;
    LOGGER.info( "Successfully obtained " + TOKEN_PACK.signonEnrichmentToken() + "." ) ;

    LOGGER.info( "Now requesting Twiml creation" +
        MESSAGE_ABOUT_MIMICKING_HTTP_CALLBACK + " ..." ) ;

    final String twiml = authenticator.createTwiml( TOKEN_PACK.urlToken() ) ;
    assertThat( twiml ).contains( "your secondary code" ) ;

    LOGGER.info( "Jumping to next state: " + TwilioStatus.CALL_IN_PROGRESS +
        MESSAGE_ABOUT_MIMICKING_HTTP_CALLBACK + " ..." ) ;
    authenticator.updateTwilioCallStatus( TOKEN_PACK.urlToken(), TwilioStatus.CALL_IN_PROGRESS ) ;

    LOGGER.info( "Jumping to next state: " + TwilioStatus.CALL_COMPLETED +
        MESSAGE_ABOUT_MIMICKING_HTTP_CALLBACK + " ..." ) ;
    authenticator.updateTwilioCallStatus( TOKEN_PACK.urlToken(), TwilioStatus.CALL_COMPLETED ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( AbstractTwilioSecondaryAuthenticatorTest.class ) ;

  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.info( "=== Netty classes loaded, tests begin here ===" ) ;
  }

  protected abstract TwilioSecondaryAuthenticator createTwilioSecondaryAuthenticator( Duration secondaryCodeMaximumAge ) ;

  protected final TimeKit< UpdateableClock > timeKit = TimeKit.instrumentedTimeKit( Stamp.FLOOR ) ;

  protected static final TwilioCredential TWILIO_CREDENTIAL =
      new TwilioCredential( "myAccountSid", "myAuthorizationToken", "+36789012" ) ;

  private final SchemeHostPort twilioVoiceSchemeHostPort = SchemeHostPort.create(
      SchemeHostPort.Scheme.HTTP,
      Hostname.LOCALHOST,
      TcpPortBooker.THIS.find()
  ) ;
  protected final URL twilioVoiceBaseUrl =
      twilioVoiceSchemeHostPort.asCanonicalUrlQuiet( "/twilio-calls" ) ;

  private final SchemeHostPort twilioCallbackSchemeHostPort = SchemeHostPort.create(
      SchemeHostPort.Scheme.HTTP,
      Hostname.LOCALHOST,
      TcpPortBooker.THIS.find()
  ) ;

  protected final URL httpCallbackRadix =
      twilioCallbackSchemeHostPort.asCanonicalUrlQuiet( "/twilio" ) ;

  protected static final TokenPack TOKEN_PACK = new TokenPack() {
    private final SecondaryToken secondaryToken = new SecondaryToken( "my2aryToken" ) ;
    private final SecondaryCode secondaryCode = new SecondaryCode( "my2aryCode" ) ;
    private final String urlToken = "myUrlToken" ;
    private final PhoneNumber userPhoneNumber = new PhoneNumber( "+367890" ) ;

    @Override
    public SecondaryToken signonEnrichmentToken() {
      return secondaryToken ;
    }

    @Override
    public SecondaryCode tokenExpectedFromUser() {
      return secondaryCode ;
    }

    @Override
    public String urlToken() {
      return urlToken ;
    }

    @Override
    public PhoneNumber userPhoneNumber() {
      return userPhoneNumber ;
    }
  } ;
  protected static final TokenPack.Factory TOKEN_PACK_FACTORY = phoneNumber -> {
    assertThat( phoneNumber ).isEqualTo( TOKEN_PACK.userPhoneNumber() );
    return TOKEN_PACK;
  };

  protected static final Duration PHONE_CALL_TIMEOUT_S = Duration.standardSeconds( 20 ) ;

  private static final String MESSAGE_ABOUT_MIMICKING_HTTP_CALLBACK =
      " -- this simulates the job of an HTTP responder calling " +
          TwilioHttpCallback.class.getSimpleName()
  ;

  private final VerifyingResponder responder = new VerifyingResponder() ;


  protected TinyHttpServer createAndStartFakeTwilioVoiceServer() {
    final TinyHttpServer httpServer =
        new TinyHttpServer( twilioVoiceSchemeHostPort.port(), responder ) ;
    httpServer.start().join() ;
    return httpServer ;
  }

  private Hypermessage.Request.Post createPostRequest(
      final TwilioCredential twilioCredential,
      final TokenPack tokenPack
  ) {
    final String twimlCallbackUrl = twilioCallbackSchemeHostPort.asCanonicalUrlQuiet(
        "/twilio/twiml/" + tokenPack.urlToken() ).toExternalForm() ;

    final String statusCallbackUrl = twilioCallbackSchemeHostPort.asCanonicalUrlQuiet(
        "/twilio/status/" + tokenPack.urlToken() ).toExternalForm() ;

    return new Hypermessage.Request.Post(
        twilioVoiceSchemeHostPort,

        UrxTools.parseUriQuiet(
            twilioVoiceBaseUrl.toExternalForm() +
            "/" + twilioCredential.accountSid + "/" + "Calls.json"
        ),

        ImmutableMultimap.of(
            HttpHeaderNames.AUTHORIZATION.toString(),
                "Basic bXlBY2NvdW50U2lkOm15QXV0aG9yaXphdGlvblRva2Vu",
            HttpHeaderNames.HOST.toString(), twilioVoiceSchemeHostPort.hostPort.asString(),
            HttpHeaderNames.CONTENT_TYPE.toString(), "application/x-www-form-urlencoded",
            HttpHeaderNames.CONTENT_LENGTH.toString(), "182"
        ),
        ImmutableMultimap.of(
            "From", twilioCredential.callerId.getAsString(),
            "To", tokenPack.userPhoneNumber().getAsString(),
            "Url", twimlCallbackUrl,
            "StatusCallback", statusCallbackUrl,
            "Timeout",
            Long.toString( PHONE_CALL_TIMEOUT_S.getStandardSeconds() )
        )
    ) ;
  }


}