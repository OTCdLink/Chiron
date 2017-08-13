package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignonInwardDutySecondarySignonAttempt extends TransientCommand<SignonInwardDuty>
{

  private final String login ;

  public SignonInwardDutySecondarySignonAttempt(
      final Designator designatorInternal,
      final String login
  ) {
    super( designatorInternal ) ;
    this.login = checkNotNull( login ) ;
  }

  @Override
  public void callReceiver( final SignonInwardDuty signonInwardDuty ) {
    signonInwardDuty.secondarySignonAttempt( endpointSpecific, login ) ;
  }
}
