package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.upend.session.SessionSupervisor;

import static com.google.common.base.Preconditions.checkNotNull;

public class SessionSupervisorCrafter< CHANNEL, ADDRESS >
    implements SessionSupervisor< CHANNEL, ADDRESS >
{

  private final CommandConsumer<
        Command< Designator, SessionSupervisor< CHANNEL, ADDRESS > >
    > commandConsumer ;

  public SessionSupervisorCrafter(
      final CommandConsumer<Command< Designator, SessionSupervisor< CHANNEL, ADDRESS > >>
          commandConsumer
  ) {
    this.commandConsumer = checkNotNull( commandConsumer ) ;
  }

  private void consume(
      final Command< Designator, SessionSupervisor< CHANNEL, ADDRESS > > command
  ) {
    commandConsumer.accept( command ) ;
  }

  @Override
  public void attemptPrimarySignon(
      final String userLogin,
      final String password,
      final CHANNEL channel,
      final ADDRESS remoteAddress,
      final PrimarySignonAttemptCallback callback
  ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public void attemptSecondarySignon(
      final CHANNEL channel,
      final ADDRESS remoteAddress,
      final SecondaryToken secondaryToken,
      final SecondaryCode secondaryCode,
      final SecondarySignonAttemptCallback callback
  ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public void tryReuse(
      final SessionIdentifier sessionIdentifier,
      final CHANNEL channel,
      final ReuseCallback callback
  ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public void closed(
      final CHANNEL channel,
      final SessionIdentifier sessionIdentifier,
      final boolean terminateSession
  ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public void kickoutAll() {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public void kickout( Designator designator, final SessionIdentifier sessionIdentifier ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }
}
