package io.github.otcdlink.chiron.upend.session.command;

import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.codec.DecodeException;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.upend.session.SignonInwardDuty;

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
