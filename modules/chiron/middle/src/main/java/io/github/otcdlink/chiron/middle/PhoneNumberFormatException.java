package io.github.otcdlink.chiron.middle;

/**
 * Thrown at {@link PhoneNumber} construction.
 */
public class PhoneNumberFormatException extends RuntimeException {

  public PhoneNumberFormatException( final String incorrectNumber ) {
    super( "Incorrect: '" + incorrectNumber + "'" ) ;
  }
}
