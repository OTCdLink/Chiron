package com.otcdlink.chiron.integration.echo;

import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.command.Command;

import java.io.IOException;

@Command.Description( name = DownwardEchoCommand.NAME )
public class DownwardEchoCommand< ENDPOINT_SPECIFIC >
    extends Command< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > >
{
  public static final String NAME = "echo" ;

  private final String message ;

  public DownwardEchoCommand( final ENDPOINT_SPECIFIC endpointSpecific, final String message ) {
    super( endpointSpecific ) ;
    this.message = message ;
  }

  @Override
  public void callReceiver( final EchoDownwardDuty< ENDPOINT_SPECIFIC > callableReceiver ) {
    callableReceiver.echoResponse( endpointSpecific, message ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter positionalFieldWriter ) throws IOException {
    positionalFieldWriter.writeDelimitedString( message ) ;
  }

  public < REPLACEMENT > DownwardEchoCommand< REPLACEMENT > withEndpointSpecific(
      final REPLACEMENT replacement
  ) {
    return new DownwardEchoCommand<>( replacement, message ) ;
  }

  @Override
  protected String toStringBody() {
    return super.toStringBody() + ";message=" + message ;
  }

}
