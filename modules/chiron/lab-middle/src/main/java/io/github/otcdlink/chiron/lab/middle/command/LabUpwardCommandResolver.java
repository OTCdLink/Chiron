package io.github.otcdlink.chiron.lab.middle.command;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.codec.AbstractCommandResolver;
import io.github.otcdlink.chiron.lab.middle.LabUpwardDuty;

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
