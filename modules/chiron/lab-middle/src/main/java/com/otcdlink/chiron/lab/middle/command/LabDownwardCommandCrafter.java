package com.otcdlink.chiron.lab.middle.command;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.lab.middle.LabDownwardDuty;
import com.otcdlink.chiron.middle.TechnicalFailureNotice;

import static com.google.common.base.Preconditions.checkNotNull;

public class LabDownwardCommandCrafter< ENDPOINT_SPECIFIC >
    implements LabDownwardDuty< ENDPOINT_SPECIFIC >
{
  private final CommandConsumer< Command<
      ENDPOINT_SPECIFIC,
      LabDownwardDuty< ENDPOINT_SPECIFIC >
  > > commandConsumer ;

  public LabDownwardCommandCrafter(
      final CommandConsumer< Command<
          ENDPOINT_SPECIFIC,
          LabDownwardDuty< ENDPOINT_SPECIFIC >
      > > commandConsumer
  ) {
    this.commandConsumer = checkNotNull( commandConsumer ) ;
  }

  @Override
  public void counter( final ENDPOINT_SPECIFIC endpointSpecific, final int delta ) {
    commandConsumer.accept( new DownwardCounterUpdate<>( endpointSpecific, delta ) ) ;
  }

  @Override
  public void failure(
      final ENDPOINT_SPECIFIC endpointSpecific,
      final TechnicalFailureNotice technicalFailureNotice
  ) {
    commandConsumer.accept( new LabAbstractDownwardFailure<>(
        endpointSpecific, technicalFailureNotice ) ) ;
  }
}
