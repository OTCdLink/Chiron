package io.github.otcdlink.chiron.upend.session.command;

import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.upend.session.SessionSupervisor;

import static com.google.common.base.Preconditions.checkNotNull;

public class SessionSupervisorAttemptPrimarySignon< CHANNEL, ADDRESS >
    extends TransientCommand< SessionSupervisor< CHANNEL, ADDRESS > >
{
  private final String userLogin ;
  private final String password ;
  private final CHANNEL channel ;
  private final ADDRESS remoteAddress ;
  private final SessionSupervisor.PrimarySignonAttemptCallback callback ;

  public SessionSupervisorAttemptPrimarySignon(
      final Designator designator,
      final String userLogin,
      final String password,
      final CHANNEL channel,
      final ADDRESS remoteAddress,
      final SessionSupervisor.PrimarySignonAttemptCallback callback

  ) {
    super( designator ) ;
    this.userLogin = checkNotNull( userLogin ) ;
    this.password = checkNotNull( password ) ;
    this.channel = checkNotNull( channel ) ;
    this.remoteAddress = checkNotNull( remoteAddress ) ;
    this.callback = checkNotNull( callback ) ;
  }

  @Override
  public void callReceiver( final SessionSupervisor< CHANNEL, ADDRESS > sessionSupervisor ) {
    sessionSupervisor.attemptPrimarySignon(
        userLogin,
        password,
        channel,
        remoteAddress,
        callback
    ) ;
  }

}
