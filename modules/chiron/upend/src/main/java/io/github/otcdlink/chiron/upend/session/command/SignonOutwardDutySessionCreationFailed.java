package io.github.otcdlink.chiron.upend.session.command;

import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.session.SignonFailureNotice;
import io.github.otcdlink.chiron.upend.session.SignonOutwardDuty;

public class SignonOutwardDutySessionCreationFailed
    extends TransientCommand< SignonOutwardDuty >
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
  public void callReceiver( final SignonOutwardDuty duty ) {
    duty.sessionCreationFailed( endpointSpecific, sessionIdentifier, signonFailureNotice ) ;
  }

}
