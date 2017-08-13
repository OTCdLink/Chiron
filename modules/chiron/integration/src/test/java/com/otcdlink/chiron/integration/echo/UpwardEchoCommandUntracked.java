package com.otcdlink.chiron.integration.echo;

import com.otcdlink.chiron.command.Command;

@Command.Description( name = UpwardEchoCommandUntracked.NAME, tracked = false )
public class UpwardEchoCommandUntracked< ENDPOINT_SPECIFIC >
    extends UpwardEchoCommand< ENDPOINT_SPECIFIC >
{
  public static final String NAME = "echoUntracked" ;

  public UpwardEchoCommandUntracked( final ENDPOINT_SPECIFIC endpointSpecific, final String message ) {
    super( endpointSpecific, message ) ;
  }
}
