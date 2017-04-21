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

@Command.Description( name = "signoutQuiet" )
public class SignonInwardDutySignoutQuiet
    extends Command< Designator, SignonInwardDuty>
{
  private final SessionIdentifier sessionIdentifier ;
  public SignonInwardDutySignoutQuiet(
      final Designator designatorInternal,
      final SessionIdentifier sessionIdentifier
  ) {
    super( designatorInternal ) ;
    this.sessionIdentifier = checkNotNull( sessionIdentifier ) ;
  }

  @Override
  public void callReceiver( final SignonInwardDuty signonInwardDuty ) {
    signonInwardDuty.signoutQuiet( endpointSpecific, sessionIdentifier ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter writer ) throws IOException {
    writer.writeDelimitedString( sessionIdentifier.asString() ) ;
  }

  @SuppressWarnings( "UnusedParameters" )
  public static SignonInwardDutySignoutQuiet decode(
      final Designator designator,
      final PositionalFieldReader reader
  ) throws DecodeException {
    final String sessionIdentifierAsString = reader.readDelimitedString() ;
    return new SignonInwardDutySignoutQuiet(
        designator, new SessionIdentifier( sessionIdentifierAsString ) ) ;
  }

}
