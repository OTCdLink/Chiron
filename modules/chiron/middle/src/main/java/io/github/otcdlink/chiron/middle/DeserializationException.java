package io.github.otcdlink.chiron.middle;

/**
 * TODO merge into {@link io.github.otcdlink.chiron.codec.DecodeException}.
 */
public class DeserializationException extends Exception {

  public DeserializationException( final String s ) {
    super( s ) ;
  }

  public DeserializationException( final String message, final Exception e ) {
    super( message, e ) ;
  }
}
