package com.otcdlink.chiron.lab.middle;

import com.otcdlink.chiron.middle.CommandFailureDuty;
import com.otcdlink.chiron.middle.TechnicalFailureNotice;

public interface LabDownwardDuty< ENDPOINT_SPECIFIC >
    extends CommandFailureDuty< ENDPOINT_SPECIFIC, TechnicalFailureNotice >
{

  void counter( ENDPOINT_SPECIFIC endpointSpecific, int value ) ;

}
