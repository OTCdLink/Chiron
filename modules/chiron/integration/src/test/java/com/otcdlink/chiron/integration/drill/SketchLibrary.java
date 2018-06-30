package com.otcdlink.chiron.integration.drill;

import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.downend.Downend;
import com.otcdlink.chiron.downend.DownendConnector;
import com.otcdlink.chiron.middle.session.SecondaryCode;
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
          changeAsConstant = drill.forSimpleDownend().changesAsConstant() ;
          changeWatcherMock = drill.forSimpleDownend().changeWatcherMock() ;
          break ;
        case COMMAND_TRANSCEIVER :
          forDownend = drill.forCommandTransceiver() ;
          changeAsConstant = drill.forCommandTransceiver().changesAsConstants() ;
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

      final SessionSupervisor.PrimarySignonAttemptCallback primarySignonAttemptCallback ;
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

        final SessionSupervisor.SecondarySignonAttemptCallback secondarySignonAttemptCallback ;
        forUpendConnector.sessionSupervisorMock().attemptSecondarySignon(
            any(),
            any(),
            exactly( SECONDARY_TOKEN ),
            exactly( SECONDARY_CODE ),
            secondarySignonAttemptCallback = withCapture()
        ) ;
        drill.runOutOfVerifierThread( () ->
            secondarySignonAttemptCallback.sessionAttributed( SESSION_IDENTIFIER ) ) ;
      } else {
        drill.runOutOfVerifierThread( () ->
            primarySignonAttemptCallback.sessionAttributed( SESSION_IDENTIFIER ) ) ;
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
          changeAsConstant = drill.forSimpleDownend().changesAsConstant() ;
          changeWatcherMock = drill.forSimpleDownend().changeWatcherMock() ;
          break ;
        case COMMAND_TRANSCEIVER :
          forDownend = drill.forCommandTransceiver() ;
          changeAsConstant = drill.forCommandTransceiver().changesAsConstants() ;
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

  ECHO_ROUNDTRIP() {
    @Override
    public void run( final ConnectorDrill drill ) {
      drill.timeKit().clock.increment( 1000 ) ;  // Useful for multiple calls.
      final Command.Tag tag = new Command.Tag( "T0" ) ;

      drill.forSimpleDownend().upwardDuty().requestEcho( tag, "Yay" ) ;

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
      drill.forSimpleDownend().downwardDutyMock().echoResponse( tag, "Waz" ) ;

    }
  }

  ;



// ======
// Boring
// ======

  private final ConnectorDrill.ForUpend.Kind upendKindRequirement ;
  private final ImmutableSet< ConnectorDrill.ForDownend.Kind > downendKindRequirements ;
  private final ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements ;

  SketchLibrary() {
    this(
        ConnectorDrill.ForUpend.Kind.REAL,
        ImmutableSet.of( DOWNEND_CONNECTOR ),
        ConnectorDrill.Authentication.ALL_AUTHENTICATED
    ) ;
  }

  SketchLibrary(
      final ConnectorDrill.ForUpend.Kind upendKindRequirement,
      final ImmutableSet< ConnectorDrill.ForDownend.Kind > downendKindRequirements,
      final ImmutableSet< ConnectorDrill.Authentication > authenticationRequirements
  ) {
    this.upendKindRequirement = checkNotNull( upendKindRequirement ) ;
    this.downendKindRequirements = checkNotNull( downendKindRequirements ) ;
    this.authenticationRequirements = checkNotNull( authenticationRequirements ) ;
  }

  @Override
  public final ConnectorDrill.ForUpend.Kind upendKindRequirement() {
    return upendKindRequirement ;
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
