package io.github.caillette.shoemaker.upend.twilio;

import com.google.common.util.concurrent.Uninterruptibles;
import io.github.otcdlink.chiron.middle.PhoneNumber;
import io.github.otcdlink.chiron.toolbox.clock.Clock;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;
import io.github.otcdlink.chiron.toolbox.security.DefaultSslEngineFactory;
import io.github.otcdlink.chiron.toolbox.security.KeystoreTools;
import io.github.otcdlink.chiron.upend.session.twilio.TwilioCredential;
import io.github.otcdlink.chiron.upend.twilio.DefaultTokenPackFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.concurrent.TimeUnit;

public class NettySecondaryAuthenticatorDemo {

  private static final Logger LOGGER =
      LoggerFactory.getLogger( NettySecondaryAuthenticatorDemo.class ) ;

  private final EventLoopGroup eventLoopGroup =
      new NioEventLoopGroup( 0, new DefaultThreadFactory( "http-client", true ) ) ;

  public void run() throws Exception {
    final NettySecondaryAuthenticator secondaryAuthenticator = new NettySecondaryAuthenticator(
        Clock.SYSTEM_CLOCK,
        eventLoopGroup,
        DefaultSslEngineFactory.ForClient.createQuiet( KeystoreTools.truststoreFromJreCacerts() ),
        new DefaultTokenPackFactory(),
        TwilioSecondaryAuthenticator.URL_BASE_TWILIO_API,
        new URL( "https://0.0.0.0" ),
        new TwilioCredential( /* It's hiding on the right ----> */                                                                  "ACe71d66bf5baf040cca95c584849e7676:512d8f557012c5f71e6b2f65e502b31f:+33987679545" ),
        Duration.standardSeconds( 10 ),
        Duration.standardSeconds( 20 )
    ) ;
    secondaryAuthenticator.requestAuthentication(
        new PhoneNumber( "+36 3 06 11 77 30" ),
        secondaryToken -> LOGGER.info( "Made available: " + secondaryToken )
    ) ;

    Uninterruptibles.sleepUninterruptibly( 10, TimeUnit.SECONDS ) ;
  }

  public static void main( final String... arguments ) throws Exception {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.info( "=== Netty classes loaded, demo begins ===" ) ;
    new NettySecondaryAuthenticatorDemo().run() ;
  }

}