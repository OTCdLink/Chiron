package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignonOutwardDutySessionCreated< SESSION_PRIMER >
    extends TransientCommand< SignonOutwardDuty< SESSION_PRIMER > >
    implements SignonCommand
{

  private final SessionIdentifier sessionIdentifier ;
  private final SESSION_PRIMER sessionPrimer ;
  private final String login ;

  public SignonOutwardDutySessionCreated(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier,
      final SESSION_PRIMER sessionPrimer,
      final String login
  ) {
    super( designatorInternal ) ;
    this.sessionIdentifier = checkNotNull( sessionIdentifier ) ;
    this.login = checkNotNull( login ) ;
    this.sessionPrimer = sessionPrimer ;
  }

  @Override
  public void callReceiver( final SignonOutwardDuty< SESSION_PRIMER > duty ) {
    duty.sessionCreated( endpointSpecific, sessionIdentifier, login, sessionPrimer ) ;
  }

}
