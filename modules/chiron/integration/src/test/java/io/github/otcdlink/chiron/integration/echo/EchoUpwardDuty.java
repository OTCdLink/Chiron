package io.github.otcdlink.chiron.integration.echo;

public interface EchoUpwardDuty< ENDPOINT_SPECIFIC > {
  void requestEcho( final ENDPOINT_SPECIFIC endpointSpecific, final String message ) ;
}
