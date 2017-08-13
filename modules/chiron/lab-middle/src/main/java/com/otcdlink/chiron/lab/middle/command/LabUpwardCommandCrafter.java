package com.otcdlink.chiron.lab.middle.command;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.lab.middle.LabUpwardDuty;

import static com.google.common.base.Preconditions.checkNotNull;

public class LabUpwardCommandCrafter< ENDPOINT_SPECIFIC >
    implements LabUpwardDuty< ENDPOINT_SPECIFIC >
{
  private final CommandConsumer< Command<
        ENDPOINT_SPECIFIC,
        LabUpwardDuty< ENDPOINT_SPECIFIC >
    > > commandConsumer ;

  public LabUpwardCommandCrafter(
      final CommandConsumer<Command<
                ENDPOINT_SPECIFIC,
                LabUpwardDuty< ENDPOINT_SPECIFIC >
            >> commandConsumer
  ) {
    this.commandConsumer = checkNotNull( commandConsumer ) ;
  }

  @Override
  public void increment( final ENDPOINT_SPECIFIC endpointSpecific, final int delta ) {
    commandConsumer.accept( new UpwardIncrement<>( endpointSpecific, delta ) ) ;
  }
}
