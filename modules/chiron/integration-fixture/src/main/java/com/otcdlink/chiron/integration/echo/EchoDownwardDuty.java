package com.otcdlink.chiron.integration.echo;

public interface EchoDownwardDuty< ENDPOINT_SPECIFIC > {
  void echoResponse( final ENDPOINT_SPECIFIC endpointSpecific, final String message ) ;
}
