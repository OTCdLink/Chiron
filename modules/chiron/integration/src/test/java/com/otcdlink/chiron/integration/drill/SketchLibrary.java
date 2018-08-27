package com.otcdlink.chiron.integration.drill;

import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.downend.CommandInFlightStatus;
import com.otcdlink.chiron.downend.CommandTransceiver;
import com.otcdlink.chiron.downend.Downend;
import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.downend.Tracker;
import com.otcdlink.chiron.downend.TrackerCurator;
import com.otcdlink.chiron.integration.echo.DownwardEchoCommand;
import com.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.toolbox.Credential;
import com.otcdlink.chiron.upend.session.SessionSupervisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.ForDownend.Kind.DOWNEND_CONNECTOR;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.SECONDARY_CODE;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.SECONDARY_TOKEN;
import static com.otcdlink.chiron.integration.drill.ConnectorDrill.SESSION_IDENTIFIER;
import static com.otcdlink.chiron.mockster.Mockster.any;
import static com.otcdlink.chiron.mockster.Mockster.exactly;
import static com.otcdlink.chiron.mockster.Mockster.withCapture;
import static com.otcdlink.chiron.mockster.Mockster.withNull;
import static org.assertj.core.api.Assertions.assertThat;

public enum SketchLibrary implements ConnectorDrill.Sketch {

  START_WITH_FAKE_UPEND(
      ConnectorDrill.ForUpend.Kind.FAKE,
      ConnectorDrill.ForDownend.Kind.ALL_REAL,
      ImmutableSet.of(
          ConnectorDrill.Authentication.NONE,
          ConnectorDrill.Authentication.ONE_FACTOR
      )
  ) {
    @Override
    public void run( final ConnectorDrill drill ) {
      final ConnectorDrill.ForFakeUpend forFakeUpend = drill.forFakeUpend() ;

      final ConnectorDrill.ForDownend forDownend ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant ;
      final DownendConnector.ChangeWatcher changeWatcherMock ;
      switch( drill.downendKind() ) {
        case DOWNEND_CONNECTOR :
          forDownend = drill.forSimpleDownend() ;
          changeAsConstant = drill.forSimpleDownend().changeAsConstant() ;
          changeWatcherMock = drill.forSimpleDownend().changeWatcherMock() ;
          break ;
        case COMMAND_TRANSCEIVER :
          forDownend = drill.forCommandTransceiver() ;
          changeAsConstant = drill.forCommandTransceiver().changeAsConstant() ;
          changeWatcherMock = drill.forCommandTransceiver().changeWatcherMock() ;
          break ;
        default :
          throw new FeatureUnavailableException( "No " + Downend.class.getSimpleName() ) ;
      }


      forFakeUpend.start().join() ;
      forDownend.start() ;
      changeWatcherMock.stateChanged( changeAsConstant.connecting ) ;

      final Consumer< Credential > credentialConsumer ;

      if( drill.authentication() == ConnectorDrill.Authentication.ONE_FACTOR ) {
        forDownend.signonMaterializerMock().readCredential( credentialConsumer = withCapture() ) ;
        changeWatcherMock.stateChanged( changeAsConstant.connected ) ;

        forDownend.signonMaterializerMock().setProgressMessage( withNull() ) ;

        drill.runOutOfVerifierThread( () ->
            credentialConsumer.accept( ConnectorDrill.GOOD_CREDENTIAL ) ) ;

        forDownend.signonMaterializerMock().setProgressMessage( "Signing in …" ) ;
        forFakeUpend.duplex().texting().assertThatNext().hasTextContaining( "PRIMARY_SIGNON" ) ;

        forFakeUpend.duplex().texting().emitPhase(
            SessionLifecycle.SessionValid.create( SESSION_IDENTIFIER ) ) ;

        forDownend.signonMaterializerMock().done() ;

        changeWatcherMock.stateChanged( changeAsConstant.signedIn ) ;

      } else {
        changeWatcherMock.stateChanged( changeAsConstant.connected ) ;
      }
    }
  },

  START_AUTHENTICATED(
      ConnectorDrill.ForUpend.Kind.REAL,
      ConnectorDrill.ForDownend.Kind.ALL_REAL,
      ConnectorDrill.Authentication.ALL_AUTHENTICATED
  ) {
    @Override
    public void run( final ConnectorDrill drill ) {
      final ConnectorDrill.ForUpendConnector forUpendConnector = drill.forUpendConnector() ;

      final ConnectorDrill.ForDownend forDownend ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant ;
      final DownendConnector.ChangeWatcher changeWatcherMock ;
      switch( drill.downendKind() ) {
        case DOWNEND_CONNECTOR :
          forDownend = drill.forSimpleDownend() ;
          changeAsConstant = drill.forSimpleDownend().changeAsConstant() ;
          changeWatcherMock = drill.forSimpleDownend().changeWatcherMock() ;
          break ;
        case COMMAND_TRANSCEIVER :
          forDownend = drill.forCommandTransceiver() ;
          changeAsConstant = drill.forCommandTransceiver().changeAsConstant() ;
          changeWatcherMock = drill.forCommandTransceiver().changeWatcherMock() ;
          break ;
        default :
          throw new FeatureUnavailableException( "No " + Downend.class.getSimpleName() ) ;
      }

      forUpendConnector.start().join() ;

      forDownend.start() ;

      changeWatcherMock.stateChanged( changeAsConstant.connecting ) ;

      final Consumer< Credential > credentialConsumer ;
      forDownend.signonMaterializerMock().readCredential( credentialConsumer = withCapture() ) ;

      changeWatcherMock.stateChanged( changeAsConstant.connected ) ;

      forDownend.signonMaterializerMock().setProgressMessage( withNull() ) ;

      drill.runOutOfVerifierThread( () ->
          credentialConsumer.accept( ConnectorDrill.GOOD_CREDENTIAL ) ) ;

      forDownend.signonMaterializerMock().setProgressMessage( "Signing in …" ) ;

      final SessionSupervisor.PrimarySignonAttemptCallback< DummySessionPrimer > primarySignonAttemptCallback ;
      forUpendConnector.sessionSupervisorMock().attemptPrimarySignon(
          exactly( ConnectorDrill.GOOD_CREDENTIAL.getLogin() ),
          exactly( ConnectorDrill.GOOD_CREDENTIAL.getPassword() ),
          any(),
          any(),
          primarySignonAttemptCallback = withCapture()
      ) ;

      if( drill.authentication() == ConnectorDrill.Authentication.TWO_FACTOR ) {
        drill.runOutOfVerifierThread( () -> primarySignonAttemptCallback.needSecondarySignon(
            ConnectorDrill.SIGNABLE_USER, SECONDARY_TOKEN ) ) ;
        forDownend.signonMaterializerMock().setProblemMessage( any() ) ;
        final Consumer< SecondaryCode > secondaryCodeConsumer ;
        forDownend.signonMaterializerMock().readSecondaryCode(
            secondaryCodeConsumer = withCapture() ) ;
        drill.runOutOfVerifierThread( () -> secondaryCodeConsumer.accept( SECONDARY_CODE ) ) ;
        forDownend.signonMaterializerMock().setProgressMessage( "Signing in …" ) ;

        final SessionSupervisor.SecondarySignonAttemptCallback< DummySessionPrimer >
            secondarySignonAttemptCallback ;
        forUpendConnector.sessionSupervisorMock().attemptSecondarySignon(
            any(),
            any(),
            exactly( SECONDARY_TOKEN ),
            exactly( SECONDARY_CODE ),
            secondarySignonAttemptCallback = withCapture()
        ) ;
        drill.runOutOfVerifierThread( () -> secondarySignonAttemptCallback.sessionAttributed(
            SESSION_IDENTIFIER, DummySessionPrimer.INSTANCE ) ) ;
      } else {
        drill.runOutOfVerifierThread( () -> primarySignonAttemptCallback.sessionAttributed(
            SESSION_IDENTIFIER, DummySessionPrimer.INSTANCE ) ) ;
      }

      forDownend.signonMaterializerMock().done() ;

      changeWatcherMock.stateChanged( changeAsConstant.signedIn ) ;
    }
  },

  STOP_AUTHENTICATED() {
    @Override
    public void run( final ConnectorDrill drill ) {
      final ConnectorDrill.ForUpendConnector forUpendConnector = drill.forUpendConnector() ;

      final ConnectorDrill.ForDownend forDownend ;
      final ConnectorDrill.ForSimpleDownend.ChangeAsConstant changeAsConstant ;
      final CompletableFuture< Void > downendStopFuture ;
      final DownendConnector.ChangeWatcher changeWatcherMock ;
      switch( drill.downendKind() ) {
        case DOWNEND_CONNECTOR :
          forDownend = drill.forSimpleDownend() ;
          changeAsConstant = drill.forSimpleDownend().changeAsConstant() ;
          changeWatcherMock = drill.forSimpleDownend().changeWatcherMock() ;
          break ;
        case COMMAND_TRANSCEIVER :
          forDownend = drill.forCommandTransceiver() ;
          changeAsConstant = drill.forCommandTransceiver().changeAsConstant() ;
          changeWatcherMock = drill.forCommandTransceiver().changeWatcherMock() ;
          break ;
        default :
          throw new FeatureUnavailableException( "No " + Downend.class.getSimpleName() ) ;
      }

      downendStopFuture = forDownend.stop() ;
      changeWatcherMock.stateChanged( changeAsConstant.stopping ) ;
      changeWatcherMock.stateChanged( changeAsConstant.stopped ) ;

      forUpendConnector.sessionSupervisorMock().closed(
          any(),
          exactly( ConnectorDrill.SESSION_IDENTIFIER ),
          exactly( true )
      ) ;

      downendStopFuture.join() ;
      forUpendConnector.stop().join() ;
    }
  },

  ECHO_ROUNDTRIP(
      ConnectorDrill.ForUpend.Kind.ALL_VALUES,
      ConnectorDrill.ForDownend.Kind.ALL_REAL,
      ConnectorDrill.Authentication.ALL_VALUES
  ) {
    @Override
    public void run( final ConnectorDrill drill ) {
      if( drill.upendKind() == ConnectorDrill.ForUpend.Kind.REAL &&
          ! drill.authentication().authenticating
      ) {
        // Can't express that in normal requirements.
        throw new FeatureConflictException(
            "Can't use " + drill.authentication() + " with " + drill.upendKind() ) ;
      }

      drill.timeKit().clock.increment( 1000 ) ;  // Useful for multiple calls.
      switch( drill.downendKind() ) {
        case DOWNEND_CONNECTOR :
          runForSimpleDownend( drill ) ;
          break ;
        case COMMAND_TRANSCEIVER :
          runForCommandTransceiver( drill ) ;
          break ;
        default : throw new IllegalArgumentException( "Unsupported: " + drill.downendKind() ) ;
      }
    }

    private void runForSimpleDownend( ConnectorDrill drill ) {
      final Command.Tag tag = new Command.Tag( "T0" ) ;

      drill.forSimpleDownend().upwardDuty().requestEcho( tag, "Yay" ) ;

      if( drill.upendKind() == ConnectorDrill.ForUpend.Kind.REAL ) {
        final Designator designatorUpward ;
        drill.forUpendConnector().upwardDutyMock().requestEcho(
            designatorUpward = withCapture(),
            exactly( "Yay" )
        ) ;
        logger( this ).info(
            "Got " + Designator.class.getSimpleName() + " " + designatorUpward + "." ) ;

        assertThat( designatorUpward.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;

        final Designator designatorDownward = DesignatorForger.newForger()
            .session( SESSION_IDENTIFIER )
            .downwardFrom( designatorUpward, 1 )
        ;
        drill.forUpendConnector().downwardDuty().echoResponse( designatorDownward, "Waz" ) ;

      } else {
        drill.forFakeUpend().duplex().texting().assertThatNext().hasTextContaining( "Yay" ) ;
        drill.forFakeUpend().duplex().texting().emitWithDutyCall().echoResponse( tag, "Waz" ) ;
      }

      drill.forSimpleDownend().downwardDutyMock().echoResponse( tag, "Waz" ) ;
    }

    private void runForCommandTransceiver( final ConnectorDrill drill ) {
      final Tracker trackerMock = drill.forCommandTransceiver().trackerMock() ;
      final CommandTransceiver.ChangeWatcher changeWatcherMock =
          drill.forCommandTransceiver().changeWatcherMock() ;

      drill.forCommandTransceiver().upwardDuty().requestEcho( trackerMock, "Yay" ) ;
      changeWatcherMock.inFlightStatusChange( CommandInFlightStatus.IN_FLIGHT ) ;

      if( drill.upendKind() == ConnectorDrill.ForUpend.Kind.REAL ) {
        final Designator designatorUpward ;
        drill.forUpendConnector().upwardDutyMock().requestEcho(
            designatorUpward = withCapture(),
            exactly( "Yay" )
        ) ;
        logger( this ).info(
            "Got " + Designator.class.getSimpleName() + " " + designatorUpward + "." ) ;

        assertThat( designatorUpward.sessionIdentifier ).isEqualTo( SESSION_IDENTIFIER ) ;

        final Designator designatorDownward = DesignatorForger.newForger()
            .session( SESSION_IDENTIFIER )
            .downwardFrom( designatorUpward, 1 )
        ;
        drill.forUpendConnector().downwardDuty().echoResponse( designatorDownward, "Waz" ) ;

      } else {
        drill.forFakeUpend().duplex().texting().next() ;
        drill.forFakeUpend().duplex().texting().emitWithDutyCall()
            .echoResponse( TAG_TR0, "Waz" ) ;
      }
      drill.forCommandTransceiver().downwardDutyMock().echoResponse( any(), exactly( "Waz" ) ) ;
      changeWatcherMock.inFlightStatusChange( CommandInFlightStatus.QUIET ) ;
      trackerMock.afterResponseHandled() ;

    }
  },


  ;

// ==============
// Some utilities
// ==============

  public static final Command.Tag TAG_TR0 = new Command.Tag( TrackerCurator.TAG_PREFIX + 0 ) ;
  public static final Command.Tag TAG_TR1 = new Command.Tag( TrackerCurator.TAG_PREFIX + 1 ) ;

  public static final SignonFailureNotice SIGNON_FAILURE_NOTICE_UNKNOWN_SESSION =
      new SignonFailureNotice( SignonFailure.UNKNOWN_SESSION ) ;

  public static final TimeBoundary.ForAll PASSIVE_TIME_BOUNDARY = TimeBoundary.newBuilder()
      .pingIntervalNever()
      .pongTimeoutNever()
      .reconnectImmediately()
      .pingTimeoutNever()
      .sessionInactivityForever()
      .build()
  ;

  public static final SignonFailureNotice INVALID_CREDENTIAL =
      new SignonFailureNotice( SignonFailure.INVALID_CREDENTIAL ) ;

  public static final SignonFailureNotice UNEXPECTED_FAILURE =
      new SignonFailureNotice( SignonFailure.UNEXPECTED ) ;

  public static Credential BAD_CREDENTIAL = new Credential( "Login", "BadPassw0rd" ) ;

  public interface Interceptor {

    String MAGIC_PLEASE_INTERCEPT = "intercept-me" ;
    String MAGIC_INTERCEPTED = "intercepted!" ;

    static CommandInterceptor commandInterceptor( final Logger logger )  {
      return new CommandInterceptor() {
        @Override
        public boolean interceptUpward( final Command command, final Sink sink ) {
          logger.info( "Intercepted " + command + ", now deciding ..." ) ;
          if( command instanceof UpwardEchoCommand ) {
            final UpwardEchoCommand echoCommand = ( UpwardEchoCommand ) command ;
            if( echoCommand.message.startsWith( MAGIC_PLEASE_INTERCEPT ) ) {
              final DownwardEchoCommand downwardEchoCommand = new DownwardEchoCommand<>(
                  echoCommand.endpointSpecific, MAGIC_INTERCEPTED ) ;
              sink.sendBackward( downwardEchoCommand ) ;
              logger.info( "Sent backward: " + downwardEchoCommand + "." ) ;
              return true ;
            }
          }
          logger.info( "Doing nothing with: " + command + " (returning false)." ) ;
          return false ;
        }
      } ;
    }
  }

  public static class DummySessionPrimer {
    private DummySessionPrimer() { }
    public static final DummySessionPrimer INSTANCE = new DummySessionPrimer() ;
  }



// ======
// Boring
// ======

  private final ImmutableSet< ConnectorDrill.ForUpend.Kind > upendKindRequirements;
  private final ImmutableSet< ConnectorDrill.ForDownend.Kind > downendKindRequirements ;
  private final ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements ;

  SketchLibrary() {
    this(
        ImmutableSet.of( ConnectorDrill.ForUpend.Kind.REAL ),
        ImmutableSet.of( DOWNEND_CONNECTOR ),
        ConnectorDrill.Authentication.ALL_AUTHENTICATED
    ) ;
  }

  SketchLibrary(
      final ConnectorDrill.ForUpend.Kind upendKindRequirements,
      final ImmutableSet< ConnectorDrill.ForDownend.Kind > downendKindRequirements,
      final ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements
  ) {
    this(
        ImmutableSet.of( upendKindRequirements ),
        downendKindRequirements,
        authenticationRequirements
    ) ;
  }
  SketchLibrary(
      final ImmutableSet< ConnectorDrill.ForUpend.Kind > upendKindRequirements,
      final ImmutableSet< ConnectorDrill.ForDownend.Kind > downendKindRequirements,
      final ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements
  ) {
    this.upendKindRequirements = checkNotNull( upendKindRequirements ) ;
    this.downendKindRequirements = checkNotNull( downendKindRequirements ) ;
    this.authenticationRequirements = checkNotNull( authenticationRequirements ) ;
  }

  public static < ENDPOINT_SPECIFIC > UpwardEchoCommand< ENDPOINT_SPECIFIC >
  upwardBrokenEchoCommand( final ENDPOINT_SPECIFIC endpointSpecific ) {
    return new UpwardEchoCommand< ENDPOINT_SPECIFIC >( endpointSpecific, "World" ) {
      @Override
      public void encodeBody( final PositionalFieldWriter positionalFieldWriter ) {
        throw new RuntimeException( "Boom" ) ;
      }
    } ;
  }

  @Override
  public final ImmutableSet< ConnectorDrill.ForUpend.Kind > upendKindRequirements() {
    return upendKindRequirements ;
  }

  @Override
  public final ImmutableSet< ConnectorDrill.ForDownend.Kind > downendKindRequirements() {
    return downendKindRequirements ;
  }

  @Override
  public final ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements() {
    return authenticationRequirements ;
  }

  private static Logger logger( final SketchLibrary sketchLibrary ) {
    return LoggerFactory.getLogger(
        sketchLibrary.getClass().getName() + "." + sketchLibrary.name() ) ;
  }



}
