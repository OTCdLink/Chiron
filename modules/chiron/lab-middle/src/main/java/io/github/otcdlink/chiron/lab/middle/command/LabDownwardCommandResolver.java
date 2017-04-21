package io.github.otcdlink.chiron.lab.middle.command;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.codec.AbstractCommandResolver;
import io.github.otcdlink.chiron.lab.middle.LabDownwardDuty;

public class LabDownwardCommandResolver< ENDPOINT_SPECIFIC >
    extends AbstractCommandResolver< ENDPOINT_SPECIFIC, LabDownwardDuty< ENDPOINT_SPECIFIC > >
{
  private static final ImmutableMap< String, SingleDecoder > DECODERS =
      ImmutableMap.< String, SingleDecoder >builder()
          .put( "counterUpdate", DownwardCounterUpdate::decode )
          .put( "failure", LabAbstractDownwardFailure::decode )
          .build()
  ;

  public LabDownwardCommandResolver() {
    super( DECODERS ) ;
  }

}
