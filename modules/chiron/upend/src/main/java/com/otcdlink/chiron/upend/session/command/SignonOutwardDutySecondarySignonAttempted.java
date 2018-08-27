package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;

public class SignonOutwardDutySecondarySignonAttempted< SESSION_PRIMER >
    extends TransientCommand< SignonOutwardDuty< SESSION_PRIMER > >
    implements SignonCommand
{

  private final SignonFailureNotice signonFailureNotice ;

  public SignonOutwardDutySecondarySignonAttempted(
      final Designator designatorInternal,
      final SignonFailureNotice signonFailureNotice
  ) {
    super( designatorInternal ) ;
    this.signonFailureNotice = signonFailureNotice ;
  }

  @Override
  public void callReceiver( final SignonOutwardDuty< SESSION_PRIMER > duty ) {
    duty.secondarySignonAttempted( endpointSpecific, signonFailureNotice ) ;
  }

}
