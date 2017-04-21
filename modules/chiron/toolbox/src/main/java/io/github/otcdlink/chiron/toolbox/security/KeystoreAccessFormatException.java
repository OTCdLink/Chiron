package io.github.otcdlink.chiron.toolbox.security;

public class KeystoreAccessFormatException extends Exception {
  public KeystoreAccessFormatException( final String parsed ) {
    super( "Incorrect format: '" + parsed
        + "'(must comply to '" + KeystoreAccess.COMPLETE_PATTERN.pattern() + "')") ;
  }
  public KeystoreAccessFormatException( final String parsed, final Exception exception ) {
    super( "Incorrect format: '" + parsed
        + "' caused by " + exception.getClass().getSimpleName() + ", " + exception.getMessage() ) ;
  }
}
