package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;

import java.io.IOException;

@Command.Description( name = "signoutAll" )
public class SignonInwardDutySignoutAll
    extends Command< Designator, SignonInwardDuty>
    implements SignonCommand
{

  public SignonInwardDutySignoutAll( final Designator designatorInternal ) {
    super( designatorInternal ) ;
  }

  @Override
  public void callReceiver( final SignonInwardDuty signonInwardDuty ) {
    signonInwardDuty.signoutAll( endpointSpecific ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter positionalFieldWriter ) throws IOException {
    // No field to encode.
  }

  @SuppressWarnings( "UnusedParameters" )
  public static SignonInwardDutySignoutAll decode(
      final Designator designator,
      final PositionalFieldReader reader
  ) throws DecodeException {
    return new SignonInwardDutySignoutAll(
        ( Designator ) designator
    ) ;
  }

}

