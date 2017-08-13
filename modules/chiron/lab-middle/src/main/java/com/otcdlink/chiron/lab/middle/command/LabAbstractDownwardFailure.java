package com.otcdlink.chiron.lab.middle.command;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.command.AbstractDownwardFailure;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.lab.middle.LabDownwardDuty;
import com.otcdlink.chiron.middle.TechnicalFailureNotice;

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
