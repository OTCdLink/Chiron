package io.github.caillette.shoemaker.upend.twilio;

import io.github.otcdlink.chiron.fixture.http.TinyHttpServer;
import io.github.otcdlink.chiron.upend.session.SecondaryAuthenticator;
import io.github.otcdlink.chiron.upend.session.twilio.AuthenticationFailure;
import io.github.otcdlink.chiron.upend.session.twilio.AuthenticationFailureNotice;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import mockit.FullVerifications;
import mockit.Injectable;
import org.joda.time.Duration;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettySecondaryAuthenticatorTest
    extends AbstractTwilioSecondaryAuthenticatorTest
{


  @Test
  public void cleanup(
      @Injectable final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback,
      @Injectable final SecondaryAuthenticator.VerificationCallback verificationCallback
  ) throws Exception {

    final TwilioSecondaryAuthenticator authenticator = createTwilioSecondaryAuthenticator(
        new Duration( 1 ) ) ;

    try( final TinyHttpServer ignored = createAndStartFakeTwilioVoiceServer() ) {
      successfulVoiceCall( authenticator, secondaryTokenCallback ) ;

      timeKit.clock.increment( 2 ) ;

      LOGGER.info( "Now User enters the outdated " + TOKEN_PACK.tokenExpectedFromUser() + " ..." ) ;
      authenticator.verifySecondaryCode(
          TOKEN_PACK.signonEnrichmentToken(),
          TOKEN_PACK.tokenExpectedFromUser(),
          verificationCallback
      ) ;

      new FullVerifications() {{
        verificationCallback.secondaryAuthenticationResult(
            new AuthenticationFailureNotice( AuthenticationFailure.UNKNOWN_SECONDARY_TOKEN ) ) ;
      }} ;

      LOGGER.info( "Verification succeeded for the pair (" + TOKEN_PACK.urlToken() +
          ", " + TOKEN_PACK.tokenExpectedFromUser().asString() + ")." ) ;
    }

  }

// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( NettySecondaryAuthenticatorTest.class ) ;

  @Override
  protected TwilioSecondaryAuthenticator createTwilioSecondaryAuthenticator(
      final Duration secondaryCodeMaximumAge
  ) {
    return new NettySecondaryAuthenticator(
        timeKit.clock,
        eventLoopGroup,
        null,
        TOKEN_PACK_FACTORY,
        twilioVoiceBaseUrl,
        httpCallbackRadix,
        TWILIO_CREDENTIAL,
        Duration.standardSeconds( 10 ),
        secondaryCodeMaximumAge
    ) ;
  }

  private final EventLoopGroup eventLoopGroup =
      new NioEventLoopGroup( 0, new DefaultThreadFactory( "http-client", true ) ) ;

  @After
  public void tearDown() throws Exception {
    eventLoopGroup.shutdownGracefully() ;
  }
}