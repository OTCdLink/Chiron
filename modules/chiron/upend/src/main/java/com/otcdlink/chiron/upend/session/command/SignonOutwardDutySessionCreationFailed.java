package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;

public class SignonOutwardDutySessionCreationFailed< SESSION_PRIMER >
    extends TransientCommand<SignonOutwardDuty< SESSION_PRIMER > >
    implements SignonCommand
{

  private final SessionIdentifier sessionIdentifier ;
  private final SignonFailureNotice signonFailureNotice ;

  public SignonOutwardDutySessionCreationFailed(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier,
      final SignonFailureNotice signonFailureNotice
  ) {
    super( designatorInternal ) ;
    this.sessionIdentifier = sessionIdentifier ;
    this.signonFailureNotice = signonFailureNotice ;
  }

  @Override
  public void callReceiver( final SignonOutwardDuty< SESSION_PRIMER > duty ) {
    duty.sessionCreationFailed( endpointSpecific, sessionIdentifier, signonFailureNotice ) ;
  }

}
