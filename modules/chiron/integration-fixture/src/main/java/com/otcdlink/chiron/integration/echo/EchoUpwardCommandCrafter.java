package com.otcdlink.chiron.integration.echo;

import com.google.common.base.Preconditions;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;

public class EchoUpwardCommandCrafter< DESIGNATOR > implements EchoUpwardDuty< DESIGNATOR > {

  private final CommandConsumer< Command< DESIGNATOR, EchoUpwardDuty< DESIGNATOR > > >
  commandConsumer ;

  public EchoUpwardCommandCrafter(
      final CommandConsumer< Command< DESIGNATOR, EchoUpwardDuty< DESIGNATOR > > > commandConsumer
  ) {
    this.commandConsumer = Preconditions.checkNotNull( commandConsumer ) ;
  }

  @Override
  public void requestEcho( final DESIGNATOR designator, final String message ) {
    commandConsumer.accept( new UpwardEchoCommand<>( designator, message ) ) ;
  }
}
