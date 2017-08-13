package com.otcdlink.chiron.codec;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.command.Command;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public class AbstractCommandResolver< ENDPOINT_SPECIFIC, DUTY >
    implements CommandBodyDecoder< ENDPOINT_SPECIFIC, DUTY >
{

  private final ImmutableMap< String, SingleDecoder > decoders ;

  public AbstractCommandResolver( final ImmutableMap< String, SingleDecoder > decoders ) {
    this.decoders = checkNotNull( decoders ) ;
  }

  public interface SingleDecoder {
    Command from( Object endpointSpecific, PositionalFieldReader reader ) throws IOException ;
  }


  @Override
  public final Command< ENDPOINT_SPECIFIC, DUTY > decodeBody(
      final ENDPOINT_SPECIFIC endpointSpecific,
      final String commandName,
      final PositionalFieldReader reader
  ) throws IOException {
    final SingleDecoder targettedDecoder = decoders.get( commandName ) ;
    if( targettedDecoder == null ) {
      return null ;
    } else {
      return targettedDecoder.from( endpointSpecific, reader ) ;
    }

  }
}
