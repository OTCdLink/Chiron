package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;

public class SignonOutwardDutySecondarySignonAttempted
    extends TransientCommand<SignonOutwardDuty>
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
  public void callReceiver( final SignonOutwardDuty duty ) {
    duty.secondarySignonAttempted( endpointSpecific, signonFailureNotice ) ;
  }

}
