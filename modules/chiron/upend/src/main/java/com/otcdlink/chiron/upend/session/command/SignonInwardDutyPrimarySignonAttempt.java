package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

@Command.Description( name="primarySignonAttempt" )
public class SignonInwardDutyPrimarySignonAttempt
    extends TransientCommand< SignonInwardDuty >
    implements SignonCommand
{

  private final String login ;
  private final String password ;

  public SignonInwardDutyPrimarySignonAttempt(
      final Designator designatorInternal,
      final String login,
      final String password
  ) {
    super( designatorInternal ) ;
    this.login = checkNotNull( login ) ;
    this.password = checkNotNull( password ) ;
  }

  @Override
  public void callReceiver( final SignonInwardDuty signonInwardDuty ) {
    signonInwardDuty.primarySignonAttempt( endpointSpecific, login, password ) ;
  }

}
