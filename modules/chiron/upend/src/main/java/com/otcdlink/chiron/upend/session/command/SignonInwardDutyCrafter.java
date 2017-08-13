package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.upend.session.SignonAttempt;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignonInwardDutyCrafter implements SignonInwardDuty {

  private final CommandConsumer<
        Command< Designator, SignonInwardDuty>
    > commandConsumer ;

  public SignonInwardDutyCrafter(
      final CommandConsumer< Command< Designator, SignonInwardDuty> > commandConsumer
  ) {
    this.commandConsumer = checkNotNull( commandConsumer ) ;
  }

  private void consume(
      final Command< Designator, SignonInwardDuty > command
  ) {
    commandConsumer.accept( command ) ;
  }



  @Override
  public void primarySignonAttempt(
      final Designator designatorInternal,
      final String login,
      final String password
  ) {
    consume( new SignonInwardDutyPrimarySignonAttempt( designatorInternal, login, password ) ) ;
  }

  @Override
  public void secondarySignonAttempt(
      final Designator designatorInternal,
      final String login
  ) {
    consume( new SignonInwardDutySecondarySignonAttempt( designatorInternal, login ) ) ;
  }

  @Override
  public void failedSignonAttempt(
      final Designator designatorInternal,
      final String login,
      final SignonAttempt signonAttempt
  ) {
    consume( new SignonInwardDutyFailedSignonAttempt( designatorInternal, login, signonAttempt ) ) ;
  }

  @Override
  public void registerSession( final Designator designatorInternal, final SessionIdentifier sessionIdentifier, final String login ) {
    consume( new SignonInwardDutyRegisterSession( designatorInternal, sessionIdentifier, login ) ) ;
  }

  @Override
  public void signout( final Designator designatorInternal ) {
    consume( new SignonInwardDutySignout( designatorInternal ) ) ;
  }

  @Override
  public void signoutQuiet(
      final Designator designator,
      final SessionIdentifier sessionIdentifier
  ) {
    consume( new SignonInwardDutySignoutQuiet( designator, sessionIdentifier ) ) ;
  }

  @Override
  public void signoutAll( final Designator designatorInternal ) {
    consume( new SignonInwardDutySignoutAll( designatorInternal ) ) ;
  }

  @Override
  public void resetSignonFailures(
      final Designator designatorInternal,
      final String login
  ) {
    consume( new SignonInwardDutyResetSignonFailures( designatorInternal, login ) ) ;
  }
}
