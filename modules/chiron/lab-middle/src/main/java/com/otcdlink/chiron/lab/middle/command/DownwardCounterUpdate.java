package com.otcdlink.chiron.lab.middle.command;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.lab.middle.LabDownwardDuty;

import java.io.IOException;

@Command.Description( name = "counterUpdate" )
public class DownwardCounterUpdate< ENDPOINT_SPECIFIC >
    extends Command< ENDPOINT_SPECIFIC, LabDownwardDuty< ENDPOINT_SPECIFIC >>
{
  private final int value ;

  public DownwardCounterUpdate( final ENDPOINT_SPECIFIC endpointSpecific, final int value ) {
    super( endpointSpecific ) ;
    this.value = value ;
  }

  @Override
  public void callReceiver( final LabDownwardDuty< ENDPOINT_SPECIFIC > duty ) {
    duty.counter( endpointSpecific, value ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter writer ) throws IOException {
    writer.writeIntegerPrimitive( value ) ;
  }

  static DownwardCounterUpdate decode(
      final Object endpointSpecific,
      final PositionalFieldReader reader
  ) throws IOException {
    return new DownwardCounterUpdate<>(
        endpointSpecific,
        reader.readIntegerPrimitive()
    ) ;
  }

}
