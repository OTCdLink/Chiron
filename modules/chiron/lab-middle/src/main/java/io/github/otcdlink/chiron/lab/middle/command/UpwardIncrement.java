package io.github.otcdlink.chiron.lab.middle.command;

import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.lab.middle.LabUpwardDuty;

import java.io.IOException;

@Command.Description( name = "increment" )
public class UpwardIncrement< ENDPOINT_SPECIFIC >
    extends Command< ENDPOINT_SPECIFIC, LabUpwardDuty< ENDPOINT_SPECIFIC > >
{
  private final int delta ;

  public UpwardIncrement( final ENDPOINT_SPECIFIC endpointSpecific, final int delta ) {
    super( endpointSpecific ) ;
    this.delta = delta ;
  }

  @Override
  public void callReceiver( final LabUpwardDuty< ENDPOINT_SPECIFIC > duty ) {
    duty.increment( endpointSpecific, delta ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter writer ) throws IOException {
    writer.writeIntegerPrimitive( delta ) ;
  }

  static UpwardIncrement decode(
      final Object endpointSpecific,
      final PositionalFieldReader reader
  ) throws IOException {
    return new UpwardIncrement<>(
        endpointSpecific,
        reader.readIntegerPrimitive()
    ) ;
  }

}
