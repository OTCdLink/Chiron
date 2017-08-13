package com.otcdlink.chiron.conductor;

/**
 * Define how to plug (possibly pre-defined) responses into some component receiving requests.
 */
public interface Responder< INBOUND, OUTBOUND > {

  OUTBOUND respond( INBOUND inbound ) ;

}
