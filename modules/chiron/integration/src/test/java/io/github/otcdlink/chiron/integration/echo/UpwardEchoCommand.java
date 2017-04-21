package io.github.otcdlink.chiron.integration.echo;

import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.command.Command;

import java.io.IOException;

@Command.Description( name = UpwardEchoCommand.NAME )
public class UpwardEchoCommand< ENDPOINT_SPECIFIC >
    extends Command< ENDPOINT_SPECIFIC, EchoUpwardDuty< ENDPOINT_SPECIFIC > >
{
  public static final String NAME = "echo" ;

  public final String message ;

  public UpwardEchoCommand( final ENDPOINT_SPECIFIC endpointSpecific, final String message ) {
    super( endpointSpecific ) ;
    this.message = message ;
  }

  @Override
  public void callReceiver( final EchoUpwardDuty< ENDPOINT_SPECIFIC > callableReceiver ) {
    callableReceiver.requestEcho( endpointSpecific, message ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter positionalFieldWriter ) throws IOException {
    positionalFieldWriter.writeDelimitedString( message ) ;
  }

  @Override
  protected String toStringBody() {
    return super.toStringBody() + ";message=" + message ;
  }
}
