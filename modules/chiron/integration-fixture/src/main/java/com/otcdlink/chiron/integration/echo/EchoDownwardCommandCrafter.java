package com.otcdlink.chiron.integration.echo;

import com.google.common.base.Preconditions;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;

public class EchoDownwardCommandCrafter< ENDPOINT_SPECIFIC >
    implements EchoDownwardDuty< ENDPOINT_SPECIFIC > {

  private final CommandConsumer<
      Command< ENDPOINT_SPECIFIC,
      EchoDownwardDuty< ENDPOINT_SPECIFIC >
  > > commandConsumer ;

  public EchoDownwardCommandCrafter(
      final CommandConsumer< Command< ENDPOINT_SPECIFIC, EchoDownwardDuty< ENDPOINT_SPECIFIC > > >
          commandConsumer
  ) {
    this.commandConsumer = Preconditions.checkNotNull( commandConsumer ) ;
  }

  @Override
  public void echoResponse( ENDPOINT_SPECIFIC designator, String message ) {
    commandConsumer.accept( new DownwardEchoCommand<>( designator, message ) );
  }

}
