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

@Command.Description( name = "signoutQuiet" )
public class SignonInwardDutySignoutQuiet
    extends Command< Designator, SignonInwardDuty>
    implements SignonCommand
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
