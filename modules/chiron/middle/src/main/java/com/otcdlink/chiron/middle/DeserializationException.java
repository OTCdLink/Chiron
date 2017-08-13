package com.otcdlink.chiron.middle;

import com.otcdlink.chiron.codec.DecodeException;

/**
 * TODO merge into {@link DecodeException}.
 */
public class DeserializationException extends Exception {

  public DeserializationException( final String s ) {
    super( s ) ;
  }

  public DeserializationException( final String message, final Exception e ) {
    super( message, e ) ;
  }
}
