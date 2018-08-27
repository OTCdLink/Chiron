package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignonOutwardDutyTerminateSession< SESSION_PRIMER >
    extends TransientCommand< SignonOutwardDuty< SESSION_PRIMER > >
    implements SignonCommand
{

  private final SessionIdentifier sessionIdentifier ;

  public SignonOutwardDutyTerminateSession(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier
  ) {
    super( designatorInternal ) ;
    this.sessionIdentifier = checkNotNull( sessionIdentifier ) ;
  }

  @Override
  public void callReceiver( final SignonOutwardDuty< SESSION_PRIMER > duty ) {
    duty.terminateSession( endpointSpecific, sessionIdentifier ) ;
  }

}
