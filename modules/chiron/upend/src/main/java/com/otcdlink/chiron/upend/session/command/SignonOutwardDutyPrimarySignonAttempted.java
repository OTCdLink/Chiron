package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SignonDecision;
import com.otcdlink.chiron.upend.session.SignableUser;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignonOutwardDutyPrimarySignonAttempted
    extends TransientCommand<SignonOutwardDuty>
{

  private final SignonDecision< SignableUser > signonDecision ;

  public SignonOutwardDutyPrimarySignonAttempted(
      final Designator designatorInternal,
      final SignonDecision< SignableUser > signonDecision
  ) {
    super( designatorInternal ) ;
    this.signonDecision = checkNotNull( signonDecision ) ;
  }

  @Override
  public void callReceiver( final SignonOutwardDuty signonOutwardDuty ) {
    signonOutwardDuty.primarySignonAttempted( endpointSpecific, signonDecision ) ;
  }

}
