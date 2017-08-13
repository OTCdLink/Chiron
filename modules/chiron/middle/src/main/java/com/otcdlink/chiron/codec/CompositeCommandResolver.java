package com.otcdlink.chiron.codec;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.command.Command;

import java.io.IOException;

public class CompositeCommandResolver< ENDPOINT_SPECIFIC, DUTY >
    implements CommandBodyDecoder< ENDPOINT_SPECIFIC, DUTY >
{
  private final ImmutableList< CommandBodyDecoder< ENDPOINT_SPECIFIC, DUTY > > decoders ;

  public CompositeCommandResolver(
      final CommandBodyDecoder< ENDPOINT_SPECIFIC, DUTY >... decoders
  ) {
    this( ImmutableList.copyOf( decoders ) ) ;
  }

  public CompositeCommandResolver(
      final ImmutableList< CommandBodyDecoder< ENDPOINT_SPECIFIC, DUTY > > decoders
  ) {
    this.decoders = Preconditions.checkNotNull( decoders ) ;
  }

  @Override
  public Command< ENDPOINT_SPECIFIC, DUTY > decodeBody(
      final ENDPOINT_SPECIFIC endpointSpecific,
      final String commandName,
      final PositionalFieldReader reader
  ) throws IOException {
    for( final CommandBodyDecoder< ENDPOINT_SPECIFIC, DUTY > decoder : decoders ) {
      final Command< ENDPOINT_SPECIFIC, DUTY > command = decoder.decodeBody(
          endpointSpecific, commandName, reader ) ;
      if( command != null ) {
        return command ;
      }
    }
    return null ;
  }

}
