package io.github.otcdlink.chiron.upend.session.command;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.session.SignonDecision;
import io.github.otcdlink.chiron.middle.session.SignonFailureNotice;
import io.github.otcdlink.chiron.upend.session.SignableUser;
import io.github.otcdlink.chiron.upend.session.SignonOutwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignonOutwardDutyCrafter implements SignonOutwardDuty {

  private final CommandConsumer<
      Command< Designator, SignonOutwardDuty>
  > commandConsumer ;

  public SignonOutwardDutyCrafter(
      final CommandConsumer< Command< Designator, SignonOutwardDuty> > commandConsumer
  ) {
    this.commandConsumer = checkNotNull( commandConsumer ) ;
  }

  private void consume(
      final Command< Designator, SignonOutwardDuty > command
  ) {
    commandConsumer.accept( command ) ;
  }

  @Override
  public void primarySignonAttempted(
      final Designator designator, 
      final SignonDecision< SignableUser > signonDecision 
  ) {
    consume( new SignonOutwardDutyPrimarySignonAttempted( designator, signonDecision ) ) ;
  }

  @Override
  public void secondarySignonAttempted(
      final Designator designator,
      final SignonFailureNotice signonFailureNotice
  ) {
    consume( new SignonOutwardDutySecondarySignonAttempted( designator, signonFailureNotice ) ) ;
  }

  @Override
  public void sessionCreationFailed(
      final Designator designator,
      final SessionIdentifier sessionIdentifier,
      final SignonFailureNotice signonFailureNotice
  ) {
    consume( new SignonOutwardDutySessionCreationFailed(
        designator, sessionIdentifier, signonFailureNotice ) ) ;
  }

  @Override
  public void sessionCreated(
      final Designator designator,
      final SessionIdentifier sessionIdentifier,
      final String login
  ) {
    consume( new SignonOutwardDutySessionCreated(
        designator, sessionIdentifier, login ) ) ;
  }

  @Override
  public void terminateSession( 
      final Designator designator, 
      final SessionIdentifier sessionIdentifier 
  ) {
    consume( new SignonOutwardDutyTerminateSession( designator, sessionIdentifier ) ) ;
  }
}
