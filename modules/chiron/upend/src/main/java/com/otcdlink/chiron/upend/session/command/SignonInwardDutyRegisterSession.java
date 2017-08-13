package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

@Command.Description( name = "registerSession" )
public class SignonInwardDutyRegisterSession
    extends Command< Designator, SignonInwardDuty>
{

  private final SessionIdentifier sessionIdentifier ;
  private final String login ;

  public SignonInwardDutyRegisterSession(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier,
      final String login
  ) {
    super( designatorInternal ) ;
    this.sessionIdentifier = checkNotNull( sessionIdentifier ) ;
    this.login = checkNotNull( login ) ;
  }

  @Override
  public void callReceiver( final SignonInwardDuty signonInwardDuty ) {
    signonInwardDuty.registerSession( endpointSpecific, sessionIdentifier, login ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter writer ) throws IOException {
    writer.writeDelimitedString( sessionIdentifier.asString() ) ;
    writer.writeDelimitedString( login ) ;
  }


  public static SignonInwardDutyRegisterSession decode(
      final Designator designator,
      final PositionalFieldReader reader
  ) throws DecodeException {
    return new SignonInwardDutyRegisterSession(
        ( Designator ) designator,
        new SessionIdentifier( reader.readDelimitedString() ),
        reader.readDelimitedString()
    ) ;
  }

}
