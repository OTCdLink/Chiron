package com.otcdlink.chiron.integration.echo;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.codec.Codec;
import com.otcdlink.chiron.designator.Designator;

import java.io.IOException;

public interface EchoCodecFixture {
  
  final class PartialUpendDecoder
      implements
      CommandBodyDecoder< Designator, EchoUpwardDuty< Designator > >
  {
    @Override
    public
    Command< Designator, EchoUpwardDuty< Designator > >
    decodeBody(
        final Designator endpointSpecific,
        final String commandName,
        final PositionalFieldReader positionalFieldReader
    ) throws DecodeException {
      if( UpwardEchoCommand.NAME.equals( commandName ) ) {
        return new UpwardEchoCommand<>(
            endpointSpecific, positionalFieldReader.readDelimitedString() ) ;
      } else {
        return null ;
      }
    }
  }
  final class PartialDownendDecoder< ENDPOINT_SPECIFIC >
      implements
      CommandBodyDecoder<
                ENDPOINT_SPECIFIC,
                EchoDownwardDuty< ENDPOINT_SPECIFIC >
            >
  {
    @Override
    public
    Command< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > >
    decodeBody(
        final ENDPOINT_SPECIFIC endpointSpecific,
        final String commandName,
        final PositionalFieldReader positionalFieldReader
    ) throws DecodeException {
      if( DownwardEchoCommand.NAME.equals( commandName ) ) {
        return new DownwardEchoCommand<>(
            endpointSpecific, positionalFieldReader.readDelimitedString() ) ;
      } else {
        return null ;
      }
    }
  }

  class TagCodec implements Codec< Command.Tag >  {

    @Override
    public Command.Tag decodeFrom( final PositionalFieldReader positionalFieldReader ) throws IOException {
      final String string = positionalFieldReader.readNullableString() ;
      return string == null ? null : new Command.Tag( string ) ;
    }

    @Override
    public void encodeTo(
        final Command.Tag tag,
        final PositionalFieldWriter positionalFieldWriter
    ) throws IOException {
      positionalFieldWriter.writeNullableString( tag.asString() ) ;
    }
  }
}
