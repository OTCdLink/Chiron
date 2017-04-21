package io.github.otcdlink.chiron.lab.middle.command;

import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.github.otcdlink.chiron.command.AbstractDownwardFailure;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.lab.middle.LabDownwardDuty;
import io.github.otcdlink.chiron.middle.TechnicalFailureNotice;

import java.io.IOException;

@Command.Description( name = "failure" )
public class LabAbstractDownwardFailure< ENDPOINT_SPECIFIC >
    extends
    AbstractDownwardFailure<
        ENDPOINT_SPECIFIC,
        LabDownwardDuty< ENDPOINT_SPECIFIC >,
        TechnicalFailureNotice
    >
{

  public LabAbstractDownwardFailure(
      final ENDPOINT_SPECIFIC endpointSpecific,
      final TechnicalFailureNotice commandFailureNotice
  ) {
    super( endpointSpecific, commandFailureNotice ) ;
  }

  static LabAbstractDownwardFailure decode(
      final Object endpointSpecific,
      final PositionalFieldReader reader
  ) throws IOException {
    return new LabAbstractDownwardFailure<>(
        endpointSpecific,
        new TechnicalFailureNotice(
            TechnicalFailureNotice.Kind.safeValueOf( reader.readIntegerPrimitive() ),
            reader.readDelimitedString()
        )
    ) ;
  }

}
