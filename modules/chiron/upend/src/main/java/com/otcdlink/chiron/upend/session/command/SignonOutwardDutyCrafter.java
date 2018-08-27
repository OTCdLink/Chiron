package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignableUser;
import com.otcdlink.chiron.middle.session.SignonDecision;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignonOutwardDutyCrafter< SESSION_PRIMER >
    implements SignonOutwardDuty< SESSION_PRIMER >
{

  private final CommandConsumer<
      Command< Designator, SignonOutwardDuty< SESSION_PRIMER > >
  > commandConsumer ;

  public SignonOutwardDutyCrafter(
      final CommandConsumer< Command< Designator, SignonOutwardDuty< SESSION_PRIMER > > > commandConsumer
  ) {
    this.commandConsumer = checkNotNull( commandConsumer ) ;
  }

  private void consume(
      final Command< Designator, SignonOutwardDuty< SESSION_PRIMER > > command
  ) {
    commandConsumer.accept( command ) ;
  }

  @Override
  public void primarySignonAttempted(
      final Designator designator, 
      final SignonDecision< SignableUser > signonDecision 
  ) {
    consume( new SignonOutwardDutyPrimarySignonAttempted<>( designator, signonDecision ) ) ;
  }

  @Override
  public void secondarySignonAttempted(
      final Designator designator,
      final SignonFailureNotice signonFailureNotice
  ) {
    consume( new SignonOutwardDutySecondarySignonAttempted<>( designator, signonFailureNotice ) ) ;
  }

  @Override
  public void sessionCreationFailed(
      final Designator designator,
      final SessionIdentifier sessionIdentifier,
      final SignonFailureNotice signonFailureNotice
  ) {
    consume( new SignonOutwardDutySessionCreationFailed<>(
        designator, sessionIdentifier, signonFailureNotice ) ) ;
  }

  @Override
  public void sessionCreated(
      final Designator designator,
      final SessionIdentifier sessionIdentifier,
      final String login,
      final SESSION_PRIMER sessionPrimer
  ) {
    consume( new SignonOutwardDutySessionCreated<>(
        designator, sessionIdentifier, sessionPrimer, login ) ) ;
  }

  @Override
  public void terminateSession( 
      final Designator designator, 
      final SessionIdentifier sessionIdentifier 
  ) {
    consume( new SignonOutwardDutyTerminateSession<>( designator, sessionIdentifier ) ) ;
  }
}
