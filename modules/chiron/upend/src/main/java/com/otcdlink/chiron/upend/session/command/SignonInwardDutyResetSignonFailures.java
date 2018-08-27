package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

@Command.Description( name = "resetSignonFailures" )
public class SignonInwardDutyResetSignonFailures
    extends Command< Designator, SignonInwardDuty>
    implements SignonCommand
{
  private final String login ;

  public SignonInwardDutyResetSignonFailures(
      final Designator designatorInternal,
      final String login
  ) {
    super( designatorInternal ) ;
    this.login = checkNotNull( login ) ;
  }

  @Override
  public void callReceiver( final SignonInwardDuty signonInwardDuty ) {
    signonInwardDuty.resetSignonFailures( endpointSpecific, login ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter writer ) throws IOException {
    writer.writeDelimitedString( login ) ;
  }

  public static SignonInwardDutyResetSignonFailures decode(
      final Designator designator,
      final PositionalFieldReader reader
  ) throws DecodeException {
    return new SignonInwardDutyResetSignonFailures(
        ( Designator ) designator,
        reader.readDelimitedString()
    ) ;
  }

}
