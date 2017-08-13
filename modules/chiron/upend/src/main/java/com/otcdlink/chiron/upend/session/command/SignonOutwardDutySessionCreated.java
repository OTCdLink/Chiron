package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignonOutwardDutySessionCreated
    extends TransientCommand< SignonOutwardDuty >
{

  private final SessionIdentifier sessionIdentifier ;
  private final String login ;

  public SignonOutwardDutySessionCreated(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier,
      final String login
  ) {
    super( designatorInternal ) ;
    this.sessionIdentifier = checkNotNull( sessionIdentifier ) ;
    this.login = checkNotNull( login ) ;
  }

  @Override
  public void callReceiver( final SignonOutwardDuty duty ) {
    duty.sessionCreated( endpointSpecific, sessionIdentifier, login ) ;
  }

}
