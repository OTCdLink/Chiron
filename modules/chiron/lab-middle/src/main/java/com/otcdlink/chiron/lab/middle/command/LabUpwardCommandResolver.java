package com.otcdlink.chiron.lab.middle.command;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.codec.AbstractCommandResolver;
import com.otcdlink.chiron.lab.middle.LabUpwardDuty;

public class LabUpwardCommandResolver< ENDPOINT_SPECIFIC >
    extends AbstractCommandResolver< ENDPOINT_SPECIFIC, LabUpwardDuty< ENDPOINT_SPECIFIC > >
{
  private static final ImmutableMap< String, SingleDecoder > DECODERS =
      ImmutableMap.< String, SingleDecoder >builder()
          .put( "increment", UpwardIncrement::decode )
          .build()
  ;

  public LabUpwardCommandResolver() {
    super( DECODERS ) ;
  }

}
