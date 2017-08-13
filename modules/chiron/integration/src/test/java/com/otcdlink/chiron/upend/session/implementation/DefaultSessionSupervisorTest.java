package com.otcdlink.chiron.upend.session.implementation;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.fixture.Monolist;
import com.otcdlink.chiron.integration.ReactiveSessionFixture.MyAddress;
import com.otcdlink.chiron.integration.ReactiveSessionFixture.MyChannel;
import com.otcdlink.chiron.integration.ReactiveSessionFixture.MyUser;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignonDecision;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.toolbox.Credential;
import com.otcdlink.chiron.toolbox.clock.Clock;
import com.otcdlink.chiron.upend.session.SecondaryAuthenticator;
import com.otcdlink.chiron.upend.session.SessionIdentifierGenerator;
import com.otcdlink.chiron.upend.session.SessionSupervisor;
import com.otcdlink.chiron.upend.session.SignonAttempt;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;
import com.otcdlink.chiron.upend.session.twilio.AuthenticationFailure;
import com.otcdlink.chiron.upend.session.twilio.AuthenticationFailureNotice;
import mockit.FullVerificationsInOrder;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.otcdlink.chiron.integration.ReactiveSessionFixture.ADDRESS_1;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.CHANNEL_A1;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.CHANNEL_B1;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.HasKind.hasKind;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.SECONDARY_CODE_1;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.SECONDARY_TOKEN_1;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.SESSION_1;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.T_1;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.T_2;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.T_3;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.T_5;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.USER_X;
import static com.otcdlink.chiron.integration.ReactiveSessionFixture.newStamp;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class DefaultSessionSupervisorTest {

  @Test
  public void simpleOneFactorAuthentication(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            null,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    assertThat( sessionSupervisor.toString() )
        .describedAs( "Increase mutation coverage score for cheap" )
        .contains( SessionSupervisor.class.getSimpleName()
    ) ;

    oneFactorAuthentication(
        clock,
        stampGenerator,
        sessionIdentifierGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor
    ) ;

  }

  /**
   * Increases mutation coverage using some twisted ways.
   */
  @Test
  @Ignore( "Let's run PiTest again before trying to fix this test")
  public void sessionCreationFailureNotification(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            null,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    assertThat( sessionSupervisor.toString() )
        .describedAs( "Increase mutation coverage score for cheap" )
        .contains( SessionSupervisor.class.getSimpleName()
    ) ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    final DefaultSessionSupervisor.SessionCreationDesignator sessionCreationDesignator =
        primarySignonAttempted(
            sessionSupervisor,
            clock,
            stampGenerator,
            sessionIdentifierGenerator,
            signonInwardDuty,
            designator
        )
    ;

    new StrictExpectations() {{
      clock.getCurrentDateTime() ; result = T_3 ;
      primarySignonAttemptCallback.signonResult(
          withArgThat( hasKind(  SignonFailure.UNMATCHED_NETWORK_ADDRESS ) ) ) ;
    }} ;

    final DefaultSessionSupervisor.SessionCreationDesignator< MyChannel, MyAddress >
        hackedDesignator =
            new DefaultSessionSupervisor.SessionCreationDesignator<>(
                sessionCreationDesignator.stamp,
                CHANNEL_A1,
                sessionCreationDesignator.signonAttemptCallback,
                false
            )
    ;

    sessionSupervisor.sessionCreated(
        hackedDesignator,
        SESSION_1,
        USER_X.login()
    ) ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notifying " +
        SessionSupervisor.SignonAttemptCallback.class.getSimpleName() +
        " of failed Signon."
    ) ;
  }


  @Test
  public void channelClosed(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

      ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            null,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    oneFactorAuthentication(
        clock,
        stampGenerator,
        sessionIdentifierGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor
    ) ;

    final Designator designatorInternalNoSession = DesignatorForger.newForger()
        .instant( T_2 ).internal() ;

    new StrictExpectations() {{
      clock.getCurrentDateTime() ; result = T_2 ;
      signonInwardDuty.signoutQuiet( ( Designator ) any, SESSION_1 ) ;
    }} ;

    sessionSupervisor.closed( CHANNEL_A1, SESSION_1, true ) ;

  }


  @Test
  public void terminateSession(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

      ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 2 ), sessionIdentifierGenerator,
            signonInwardDuty,
            null,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    oneFactorAuthentication(
        clock,
        stampGenerator,
        sessionIdentifierGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor
    ) ;

    final Designator designatorInternal =
        DesignatorForger.newForger().instant( T_1 ).internal() ;

    new StrictExpectations() {{
      channelCloser.close( CHANNEL_A1 ) ;
    }} ;

    sessionSupervisor.terminateSession( designatorInternal, SESSION_1 ) ;

    LOGGER.info( "Authenticating again to see if User's session was cleaned ..." ) ;

    oneFactorAuthentication(
        clock,
        stampGenerator,
        sessionIdentifierGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor
    ) ;


  }
  @Test
  public void closeChannel(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser
  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 2 ),
            sessionIdentifierGenerator,
            signonInwardDuty,
            null,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    oneFactorAuthentication(
        clock,
        stampGenerator,
        sessionIdentifierGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor
    ) ;

    new StrictExpectations() {{
      clock.getCurrentDateTime() ; result = T_3 ;
      signonInwardDuty.signoutQuiet( ( Designator ) any, ( SessionIdentifier ) any ) ;
      // Don't ask for the channel to close because Netty is already closing it.
    }} ;

    sessionSupervisor.closed( CHANNEL_A1, SESSION_1, true ) ;

    LOGGER.info( "Authenticating again to see if User's session was cleaned ..." ) ;

    oneFactorAuthentication(
        clock,
        stampGenerator,
        sessionIdentifierGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor
    ) ;

  }

  @Test
  public void kickout(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

      ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            null,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    oneFactorAuthentication(
        clock,
        stampGenerator,
        sessionIdentifierGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor
    ) ;

    new StrictExpectations() {{
      channelCloser.close( CHANNEL_A1 ) ;
    }} ;

    sessionSupervisor.kickoutAll() ;

  }





  @Test
  public void sessionReuse(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ReuseCallback reuseCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            null,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    oneFactorAuthentication(
        clock,
        stampGenerator,
        sessionIdentifierGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor
    ) ;

    new StrictExpectations() {{
      clock.getCurrentDateTime() ; result = T_3 ;
      designatorFactory.internal() ;
      result = new Designator(
          Designator.Kind.INTERNAL, newStamp( 3 ), newStamp( 2 ), null, SESSION_1 ){} ;
      signonInwardDuty.signoutQuiet( ( Designator ) any, ( SessionIdentifier ) any ) ;
      clock.getCurrentDateTime() ; result = T_3 ;
    }} ;
    sessionSupervisor.closed( CHANNEL_A1, SESSION_1, false ) ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified of " + CHANNEL_A1 + " closed " +
        ", while keeping " + SESSION_1 + "." ) ;

    final Monolist< DefaultSessionSupervisor.SessionCreationDesignator >
        sessionRegistrationDesignatorCaptor = new Monolist<>() ;

    new StrictExpectations() {{
      stampGenerator.generate() ; result = newStamp( 4 ) ;
      signonInwardDuty.registerSession(
          withCapture( sessionRegistrationDesignatorCaptor ), SESSION_1, USER_X.login() ) ;
      reuseCallback.reuseOutcome( null ) ;
    }} ;

    sessionSupervisor.tryReuse( SESSION_1, CHANNEL_B1, reuseCallback ) ;

    final DefaultSessionSupervisor.SessionCreationDesignator sessionCreationDesignator =
        sessionRegistrationDesignatorCaptor.get() ;

    sessionCreationDesignator.signonAttemptCallback.sessionAttributed( SESSION_1 ) ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified as " +
        SessionSupervisor.ReuseCallback.class.getSimpleName() +
        " that reuse happened."
    ) ;

  }


  @Test
  public void primaryAuthenticationFailedInvalidCredential(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            null,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    final Monolist< Designator >
        internalDesignatorCapture = new Monolist<>() ;
    final Designator internal = DesignatorForger.newForger().instant( T_2 ).internal() ;

    new StrictExpectations() {{
      designatorFactory.internalZero( designator ) ; result = internal ;
      signonInwardDuty.failedSignonAttempt(
          withCapture( internalDesignatorCapture ),
          USER_X.login(),
          SignonAttempt.PRIMARY
      ) ;
      primarySignonAttemptCallback.signonResult(
          new SignonFailureNotice( SignonFailure.INVALID_CREDENTIAL ) ) ;
    }} ;

    sessionSupervisor.primarySignonAttempted(
        designator,
        new SignonDecision<>(
            USER_X,
            new SignonFailureNotice( SignonFailure.INVALID_CREDENTIAL )
        )
    ) ;

    new FullVerificationsInOrder() {{ }} ;
    assertThat( internalDesignatorCapture.get() ).isSameAs( internal ) ;

    LOGGER.info( sessionSupervisor + " notified as " +
        SignonInwardDuty.class.getSimpleName() +
        " that " + Credential.class.getSimpleName() + " is invalid."
    ) ;

  }

  @Test
  public void primaryAuthenticationFailedUnknownUser(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            null,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    new StrictExpectations() {{
      primarySignonAttemptCallback.signonResult(
          new SignonFailureNotice( SignonFailure.INVALID_CREDENTIAL ) ) ;
    }} ;

    sessionSupervisor.primarySignonAttempted(
        designator,
        new SignonDecision<>( new SignonFailureNotice( SignonFailure.INVALID_CREDENTIAL ) )
    ) ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified as " +
        SignonInwardDuty.class.getSimpleName() +
        " that " + Credential.class.getSimpleName() + " is invalid."
    ) ;

  }



  @Test
  public void simpleTwoFactorAuthentication(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SecondaryAuthenticator secondaryAuthenticator,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.SecondarySignonAttemptCallback
          secondarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            secondaryAuthenticator,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback =
        primarySignonAttemptedRequestSecondary(
            sessionSupervisor,
            secondaryAuthenticator,
            designator
        )
    ;

    receiveSecondaryToken(
        sessionSupervisor, clock, primarySignonAttemptCallback, secondaryTokenCallback ) ;

    final DefaultSessionSupervisor.SecondarySignonAttemptDesignator
        secondarySignonAttemptDesignator = attemptSecondarySignon(
            sessionSupervisor,
            stampGenerator,
        signonInwardDuty,
            secondarySignonAttemptCallback
        )
    ;


    final SecondaryAuthenticator.VerificationCallback verificationCallback =
        receiveSecondaryCodeVerificationResult(
            sessionSupervisor,
            secondaryAuthenticator,
            secondarySignonAttemptDesignator
        )
    ;

    final DefaultSessionSupervisor.SessionCreationDesignator sessionCreationDesignator =
        notifySuccessfulSecondaryAuthentication(
            clock,
            stampGenerator,
            sessionIdentifierGenerator,
            signonInwardDuty,
            sessionSupervisor,
            verificationCallback,
            T_2
        )
    ;

    notifyOfSuccessfulSessionCreation(
        clock,
        secondarySignonAttemptCallback,
        sessionSupervisor,
        sessionCreationDesignator
    ) ;

  }

  @Test
  public void secondarySignonAttemptedWithGenericFailure(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SecondaryAuthenticator secondaryAuthenticator,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.SecondarySignonAttemptCallback
          secondarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            secondaryAuthenticator,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback =
        primarySignonAttemptedRequestSecondary(
            sessionSupervisor,
            secondaryAuthenticator,
            designator
        )
    ;

    receiveSecondaryToken(
        sessionSupervisor, clock, primarySignonAttemptCallback, secondaryTokenCallback ) ;

    final DefaultSessionSupervisor.SecondarySignonAttemptDesignator
        secondarySignonAttemptDesignator = attemptSecondarySignon(
            sessionSupervisor,
            stampGenerator,
        signonInwardDuty,
            secondarySignonAttemptCallback
        )
    ;


    final SignonFailureNotice signonFailureNotice =
        new SignonFailureNotice( SignonFailure.UNEXPECTED ) ;

    new StrictExpectations() {{
      secondarySignonAttemptCallback.signonResult(
          signonFailureNotice
      ) ;
    }} ;

    sessionSupervisor.secondarySignonAttempted(
        secondarySignonAttemptDesignator, signonFailureNotice ) ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified as " + SignonOutwardDuty. class.getSimpleName() +
        " that Secondary Signon is possible." ) ;

  }

  @Test
  public void secondaryAuthenticationFailedInvalidCode(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SecondaryAuthenticator secondaryAuthenticator,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.SecondarySignonAttemptCallback
          secondarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            secondaryAuthenticator,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback =
        primarySignonAttemptedRequestSecondary(
            sessionSupervisor,
            secondaryAuthenticator,
            designator
        )
    ;

    receiveSecondaryToken(
        sessionSupervisor, clock, primarySignonAttemptCallback, secondaryTokenCallback ) ;

    final DefaultSessionSupervisor.SecondarySignonAttemptDesignator
        secondarySignonAttemptDesignator = attemptSecondarySignon(
            sessionSupervisor,
            stampGenerator,
        signonInwardDuty,
            secondarySignonAttemptCallback
        )
    ;


    final SecondaryAuthenticator.VerificationCallback verificationCallback =
        receiveSecondaryCodeVerificationResult(
            sessionSupervisor,
            secondaryAuthenticator,
            secondarySignonAttemptDesignator
        )
    ;


    final Designator designatorInternal =
        DesignatorForger.newForger().instant( T_2 ).internal() ;

    new StrictExpectations() {{
      designatorFactory.internal() ; result = designatorInternal ;
      signonInwardDuty.failedSignonAttempt(
          designatorInternal,
          USER_X.login(),
          SignonAttempt.SECONDARY
      ) ;
      secondarySignonAttemptCallback.signonResult(
          new SignonFailureNotice( SignonFailure.INVALID_SECONDARY_CODE, "" ) ) ;
    }} ;

    verificationCallback.secondaryAuthenticationResult(
        new AuthenticationFailureNotice( AuthenticationFailure.INCORRECT_CODE, "" )
    ) ;
    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified as " +
        SecondaryAuthenticator.VerificationCallback.class.getSimpleName() +
        " that Secondary Code is invalid."
    ) ;

  }

  @Test
  public void secondaryAuthenticationFailedInvalidToken(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SecondaryAuthenticator secondaryAuthenticator,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.SecondarySignonAttemptCallback
          secondarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            secondaryAuthenticator,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback =
        primarySignonAttemptedRequestSecondary(
            sessionSupervisor,
            secondaryAuthenticator,
            designator
        )
    ;

    receiveSecondaryToken(
        sessionSupervisor, clock, primarySignonAttemptCallback, secondaryTokenCallback ) ;

    final DefaultSessionSupervisor.SecondarySignonAttemptDesignator
        secondarySignonAttemptDesignator = attemptSecondarySignon(
            sessionSupervisor,
            stampGenerator,
        signonInwardDuty,
            secondarySignonAttemptCallback
        )
    ;


    final SecondaryAuthenticator.VerificationCallback verificationCallback =
        receiveSecondaryCodeVerificationResult(
            sessionSupervisor,
            secondaryAuthenticator,
            secondarySignonAttemptDesignator
        )
    ;


    new StrictExpectations() {{
      secondarySignonAttemptCallback.signonResult( new SignonFailureNotice(
          SignonFailure.INVALID_SECONDARY_TOKEN, "Unknown Secondary Token" ) ) ;
    }} ;

    verificationCallback.secondaryAuthenticationResult(
        new AuthenticationFailureNotice( AuthenticationFailure.UNKNOWN_SECONDARY_TOKEN ) ) ;
    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified as " +
        SecondaryAuthenticator.VerificationCallback.class.getSimpleName() +
        " that Secondary Token pair is unknown."
    ) ;

  }


  @Test
  public void secondaryAuthenticationFailedForGenericReason(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SecondaryAuthenticator secondaryAuthenticator,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.SecondarySignonAttemptCallback
          secondarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser

  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            secondaryAuthenticator,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback =
        primarySignonAttemptedRequestSecondary(
            sessionSupervisor,
            secondaryAuthenticator,
            designator
        )
    ;

    receiveSecondaryToken(
        sessionSupervisor, clock, primarySignonAttemptCallback, secondaryTokenCallback ) ;

    final DefaultSessionSupervisor.SecondarySignonAttemptDesignator
        secondarySignonAttemptDesignator = attemptSecondarySignon(
            sessionSupervisor,
            stampGenerator,
        signonInwardDuty,
            secondarySignonAttemptCallback
        )
    ;


    final SecondaryAuthenticator.VerificationCallback verificationCallback =
        receiveSecondaryCodeVerificationResult(
            sessionSupervisor,
            secondaryAuthenticator,
            secondarySignonAttemptDesignator
        )
    ;


    new StrictExpectations() {{
      secondarySignonAttemptCallback.signonResult( new SignonFailureNotice(
          SignonFailure.SECONDARY_AUTHENTICATION_GENERIC_FAILURE,
          AuthenticationFailure.INTERNAL_ERROR.description()
      ) ) ;
    }} ;

    verificationCallback.secondaryAuthenticationResult(
        new AuthenticationFailureNotice( AuthenticationFailure.INTERNAL_ERROR ) ) ;
    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified as " +
        SecondaryAuthenticator.VerificationCallback.class.getSimpleName() +
        " that Secondary Authentication failed for a generic reason."
    ) ;

  }

  @Test
  public void secondaryAuthenticationFailedMissingPrimary(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SecondaryAuthenticator secondaryAuthenticator,
      @Injectable final SessionSupervisor.SecondarySignonAttemptCallback
          secondarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser
  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            secondaryAuthenticator,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    new StrictExpectations() {{
      secondarySignonAttemptCallback.signonResult(
          new SignonFailureNotice( SignonFailure.INVALID_SECONDARY_TOKEN ) ) ;
    }} ;

    sessionSupervisor.attemptSecondarySignon(
        CHANNEL_A1,
        ADDRESS_1,
        SECONDARY_TOKEN_1,
        SECONDARY_CODE_1,
        secondarySignonAttemptCallback
    ) ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " requested as " + SignonInwardDuty.class.getSimpleName() +
        " to perform a Secondary Signon attempt (but there was no matching Primary)." ) ;

  }

  /**
   * Needed to raise mutation testing score, unlikely to happen.
   */
  @Test
  public void unsollicitedSecondarySignonAttempted(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SecondaryAuthenticator secondaryAuthenticator,
      @Injectable final SessionSupervisor.SecondarySignonAttemptCallback
          secondarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser
  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            secondaryAuthenticator,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    final DefaultSessionSupervisor.SecondarySignonAttemptDesignator< MyChannel, MyAddress >
        designator = new DefaultSessionSupervisor.SecondarySignonAttemptDesignator<>(
            newStamp( 1 ),
        CHANNEL_A1,
        ADDRESS_1,
            SECONDARY_TOKEN_1,
            SECONDARY_CODE_1,
            secondarySignonAttemptCallback
        )
    ;

    new StrictExpectations() {{
      secondarySignonAttemptCallback.signonResult(
          new SignonFailureNotice(
              SignonFailure.INVALID_SECONDARY_TOKEN,
              "Secondary Token unknown or expired: " + SECONDARY_TOKEN_1
          )
      ) ;
    }} ;

    sessionSupervisor.secondarySignonAttempted(
        designator,
        null
    ) ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified " + SignonInwardDuty.class.getSimpleName() +
        " that a Secondary Signon was attempt (but there was no matching Primary)." ) ;

  }


  /**
   * This test mainly increases mutation coverage.
   */
  @Test
  public void callScavenge(
      @Injectable final Clock clock,
      @Injectable final Stamp.Generator stampGenerator,
      @Injectable final Designator.Factory designatorFactory,
      @Injectable final SessionIdentifierGenerator
          sessionIdentifierGenerator,
      @Injectable final SignonInwardDuty signonInwardDuty,
      @Injectable final SecondaryAuthenticator secondaryAuthenticator,
      @Injectable final SessionSupervisor.PrimarySignonAttemptCallback
          primarySignonAttemptCallback,
      @Injectable final SessionSupervisor.SecondarySignonAttemptCallback
          secondarySignonAttemptCallback,
      @Injectable final SessionSupervisor.ChannelCloser< MyChannel > channelCloser
  ) throws Exception {
    final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor =
        new DefaultSessionSupervisor<>(
            clock,
            stampGenerator,
            designatorFactory,
            Duration.millis( 1 ), sessionIdentifierGenerator,
            signonInwardDuty,
            secondaryAuthenticator,
            new Duration( 1 ),
            MyChannel::remoteAddress,
            channelCloser
        )
    ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
        > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback =
        primarySignonAttemptedRequestSecondary(
            sessionSupervisor,
            secondaryAuthenticator,
            designator
        )
    ;

    receiveSecondaryToken(
        sessionSupervisor, clock, primarySignonAttemptCallback, secondaryTokenCallback ) ;

    forceScavenge( sessionSupervisor, clock, T_5 ) ;

    new StrictExpectations() {{
      secondarySignonAttemptCallback.signonResult(
          withArgThat( hasKind( SignonFailure.INVALID_SECONDARY_TOKEN ) ) ) ;
    }} ;

    sessionSupervisor.attemptSecondarySignon(
        CHANNEL_A1,
        ADDRESS_1,
        SECONDARY_TOKEN_1,
        SECONDARY_CODE_1,
        secondarySignonAttemptCallback
    ) ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified " + secondarySignonAttemptCallback +
        " that Signon did fail." ) ;

  }


// ==========================
// Factored test subsequences
// ==========================


  private static void oneFactorAuthentication(
      final Clock clock,
      final Stamp.Generator stampGenerator,
      final SessionIdentifierGenerator sessionIdentifierGenerator,
      final SignonInwardDuty signonInwardDuty,
      final SessionSupervisor.PrimarySignonAttemptCallback primarySignonAttemptCallback,
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor
  ) {
    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > designator = requestPrimaryAuthentication(
        stampGenerator,
        signonInwardDuty,
        primarySignonAttemptCallback,
        sessionSupervisor,
        USER_X
    ) ;


    final DefaultSessionSupervisor.SessionCreationDesignator sessionCreationDesignator =
        primarySignonAttempted(
            sessionSupervisor,
            clock,
            stampGenerator,
            sessionIdentifierGenerator,
            signonInwardDuty,
            designator
        )
    ;

    notifyOfSuccessfulSessionCreation(
        clock,
        primarySignonAttemptCallback,
        sessionSupervisor,
        sessionCreationDesignator
    ) ;
  }

  private static DefaultSessionSupervisor.SessionCreationDesignator primarySignonAttempted(
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor,
      final Clock clock,
      final Stamp.Generator stampGenerator,
      final SessionIdentifierGenerator sessionIdentifierGenerator,
      final SignonInwardDuty signonInwardDuty,
      final DefaultSessionSupervisor.PrimarySignonAttemptDesignator< MyChannel, MyAddress >
          designator
  ) {
    final Monolist< DefaultSessionSupervisor.SessionCreationDesignator >
        sessionCreationDesignatorCapture = new Monolist<>() ;

    new StrictExpectations() {{
      clock.getCurrentDateTime() ; result = T_2 ;  // Scavenging needs this.
      sessionIdentifierGenerator.generate() ; result = SESSION_1;
      clock.getCurrentDateTime() ; result = T_2 ;
      stampGenerator.generate() ; result = newStamp( 2 ) ;
      signonInwardDuty.registerSession(
          withCapture( sessionCreationDesignatorCapture ),
          SESSION_1,
          USER_X.login()
      ) ;
    }} ;

    sessionSupervisor.primarySignonAttempted( designator, new SignonDecision<>( USER_X ) ) ;

    new FullVerificationsInOrder() {{ }} ;

    final DefaultSessionSupervisor.SessionCreationDesignator sessionCreationDesignator =
        sessionCreationDesignatorCapture.get() ;

    LOGGER.info( sessionSupervisor + " notified as " +
        SecondaryAuthenticator.VerificationCallback.class.getSimpleName() +
        " that Secondary Token-Code pair is valid."
    ) ;
    return sessionCreationDesignator;
  }


  private static DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
      MyChannel,
      MyAddress
  > requestPrimaryAuthentication(
      final Stamp.Generator stampGenerator,
      final SignonInwardDuty signonInwardDuty,
      final SessionSupervisor.PrimarySignonAttemptCallback primarySignonAttemptCallback,
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor,
      final MyUser user
  ) {
    final Monolist< DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
        MyChannel,
        MyAddress
    > > designatorCapture = new Monolist<>() ;

    new StrictExpectations() {{
      stampGenerator.generate() ; result = newStamp( 0 ) ;
      signonInwardDuty.primarySignonAttempt(
          withCapture( designatorCapture ),
          user.login(),
          user.password()
      ) ;
    }} ;

    sessionSupervisor.attemptPrimarySignon( USER_X.login(), USER_X.password(),
        CHANNEL_A1, ADDRESS_1, primarySignonAttemptCallback ) ;

    final DefaultSessionSupervisor.PrimarySignonAttemptDesignator< MyChannel, MyAddress >
        primarySignonAttemptDesignator = designatorCapture.get() ;
    assertThat( primarySignonAttemptDesignator.stamp ).isNotNull() ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " requested to perform Primary Signon, it forwarded to " +
        signonInwardDuty + " passing " + primarySignonAttemptCallback + "." ) ;

    LOGGER.info( "(Done with " +
        // Thread.currentThread().getStackTrace()[ 1 ].getMethodName() +
        "method " +
        "factoring some test stuff.)" ) ;

    return primarySignonAttemptDesignator ;
  }


  private static SecondaryAuthenticator.SecondaryTokenCallback
  primarySignonAttemptedRequestSecondary(
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor,
      final SecondaryAuthenticator secondaryAuthenticator,
      final DefaultSessionSupervisor.PrimarySignonAttemptDesignator<
          MyChannel,
          MyAddress
          > designator
  ) {
    final Monolist< SecondaryAuthenticator.SecondaryTokenCallback >
        secondaryTokenCallbackCapture = new Monolist<>() ;
    new StrictExpectations() {{
      secondaryAuthenticator.requestAuthentication(
          USER_X.phoneNumber(), withCapture( secondaryTokenCallbackCapture ) ) ;
    }} ;


    sessionSupervisor.primarySignonAttempted( designator, new SignonDecision<>( USER_X ) ) ;

    new FullVerificationsInOrder() {{ }} ;

    final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback =
        secondaryTokenCallbackCapture.get() ;
    assertThat( secondaryTokenCallback ).isNotNull() ;

    LOGGER.info( sessionSupervisor + " notified as " + SignonOutwardDuty.class.getSimpleName() +
        " that Primary Signon is allowed. This has triggered one call to " +
        SecondaryAuthenticator.class.getSimpleName() + " with a callback."
    ) ;
    return secondaryTokenCallback ;
  }

  /**
   * Scavenging can happen at any time because of some other User attempting
   * a signon. Instead of triggering it through a Secondary Signon attempt with another User,
   * we call the method directly.
   */
  private static void forceScavenge(
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor,
      final Clock clock,
      final DateTime now
  ) {
    new StrictExpectations() {{
      clock.getCurrentDateTime() ;
      result = now ;
    }} ;
    sessionSupervisor.scavenge() ;
    new FullVerificationsInOrder() {{ }} ;
  }

  private static void receiveSecondaryToken(
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor,
      final Clock clock,
      final SessionSupervisor.PrimarySignonAttemptCallback primarySignonAttemptCallback,
      final SecondaryAuthenticator.SecondaryTokenCallback secondaryTokenCallback
  ) {
    new StrictExpectations() {{
      clock.getCurrentDateTime() ; result = T_1 ;
      primarySignonAttemptCallback.needSecondarySignon( USER_X, SECONDARY_TOKEN_1 ) ;
    }} ;

    secondaryTokenCallback.secondaryToken( SECONDARY_TOKEN_1 ) ;
    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " got notified by " +
        SecondaryAuthenticator.class.getSimpleName() +
        " that " + SecondaryToken.class.getSimpleName() + " is ready." )
    ;
  }



  private static SecondaryAuthenticator.VerificationCallback
  receiveSecondaryCodeVerificationResult(
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor,
      final SecondaryAuthenticator secondaryAuthenticator,
      final DefaultSessionSupervisor.SecondarySignonAttemptDesignator secondarySignonAttemptDesignator
  ) {
    final Monolist< SecondaryAuthenticator.VerificationCallback >
        verificationCallbackCapture = new Monolist<>() ;

    new StrictExpectations() {{
      secondaryAuthenticator.verifySecondaryCode(
          SECONDARY_TOKEN_1,
          SECONDARY_CODE_1,
          withCapture( verificationCallbackCapture )
      ) ;
    }} ;

    sessionSupervisor.secondarySignonAttempted( secondarySignonAttemptDesignator, null ) ;

    new FullVerificationsInOrder() {{ }} ;

    final SecondaryAuthenticator.VerificationCallback verificationCallback =
        verificationCallbackCapture.get() ;

    LOGGER.info( sessionSupervisor + " notified as " + SignonOutwardDuty. class.getSimpleName() +
        " that Secondary Signon is possible." ) ;
    return verificationCallback;
  }

  private static DefaultSessionSupervisor.SecondarySignonAttemptDesignator attemptSecondarySignon(
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor,
      final Stamp.Generator stampGenerator,
      final SignonInwardDuty signonInwardDuty,
      final SessionSupervisor.SecondarySignonAttemptCallback secondarySignonAttemptCallback
  ) {
    final Monolist< DefaultSessionSupervisor.SecondarySignonAttemptDesignator >
        secondarySignonAttemptDesignatorCapture = new Monolist<>() ;
    new StrictExpectations() {{
      stampGenerator.generate() ; result = newStamp( 1 ) ;
      signonInwardDuty.secondarySignonAttempt(
          withCapture( secondarySignonAttemptDesignatorCapture ), USER_X.login() ) ;
    }} ;

    sessionSupervisor.attemptSecondarySignon(
        CHANNEL_A1,
        ADDRESS_1,
        SECONDARY_TOKEN_1,
        SECONDARY_CODE_1,
        secondarySignonAttemptCallback
    ) ;

    new FullVerificationsInOrder() {{ }} ;

    final DefaultSessionSupervisor.SecondarySignonAttemptDesignator
        secondarySignonAttemptDesignator = secondarySignonAttemptDesignatorCapture.get() ;

    LOGGER.info( sessionSupervisor + " requested as " + SignonInwardDuty.class.getSimpleName() +
        " to perform a Secondary Signon attempt." ) ;
    return secondarySignonAttemptDesignator;
  }



  private static DefaultSessionSupervisor.SessionCreationDesignator
  notifySuccessfulSecondaryAuthentication(
      final Clock clock,
      final Stamp.Generator stampGenerator,
      final SessionIdentifierGenerator sessionIdentifierGenerator,
      final SignonInwardDuty signonInwardDuty,
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor,
      final SecondaryAuthenticator.VerificationCallback verificationCallback,
      final DateTime now
  ) {
    final Monolist< DefaultSessionSupervisor.SessionCreationDesignator >
        sessionCreationDesignatorCapture = new Monolist<>() ;

    new StrictExpectations() {{
      clock.getCurrentDateTime() ; result = now ;
      sessionIdentifierGenerator.generate() ; result = SESSION_1;
      clock.getCurrentDateTime() ; result = now ;
      stampGenerator.generate() ; result = newStamp( 2 ) ;
      signonInwardDuty.registerSession(
          withCapture( sessionCreationDesignatorCapture ),
          SESSION_1,
          USER_X.login()
      ) ;
    }} ;

    verificationCallback.secondaryAuthenticationResult( null ) ;
    new FullVerificationsInOrder() {{ }} ;

    final DefaultSessionSupervisor.SessionCreationDesignator sessionCreationDesignator =
        sessionCreationDesignatorCapture.get() ;

    LOGGER.info( sessionSupervisor + " notified as " +
        SecondaryAuthenticator.VerificationCallback.class.getSimpleName() +
        " that Secondary Token-Code pair is valid."
    ) ;
    return sessionCreationDesignator ;
  }


  private static void notifyOfSuccessfulSessionCreation(
      final Clock clock,
      final SessionSupervisor.SignonAttemptCallback signonAttemptCallback,
      final DefaultSessionSupervisor< MyChannel, MyAddress > sessionSupervisor,
      final DefaultSessionSupervisor.SessionCreationDesignator sessionCreationDesignator
  ) {
    new StrictExpectations() {{
      clock.getCurrentDateTime() ; result = T_3 ;
      signonAttemptCallback.sessionAttributed( SESSION_1 ) ;
    }} ;

    sessionSupervisor.sessionCreated(
        sessionCreationDesignator,
        SESSION_1,
        USER_X.login()
    ) ;

    new FullVerificationsInOrder() {{ }} ;

    LOGGER.info( sessionSupervisor + " notified as " + SignonOutwardDuty.class.getSimpleName() +
        " of successful Session creation." ) ;
    LOGGER.info( sessionSupervisor + " finally notifying " +
        SessionSupervisor.SignonAttemptCallback.class.getSimpleName() +
        " of successful Signon."
    ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( DefaultSessionSupervisorTest.class ) ;

}