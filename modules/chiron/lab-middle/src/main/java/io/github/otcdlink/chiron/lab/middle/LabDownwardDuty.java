package io.github.otcdlink.chiron.lab.middle;

import io.github.otcdlink.chiron.middle.CommandFailureDuty;
import io.github.otcdlink.chiron.middle.TechnicalFailureNotice;

public interface LabDownwardDuty< ENDPOINT_SPECIFIC >
    extends CommandFailureDuty< ENDPOINT_SPECIFIC, TechnicalFailureNotice >
{

  void counter( ENDPOINT_SPECIFIC endpointSpecific, int value ) ;

}
