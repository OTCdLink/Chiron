package io.github.otcdlink.chiron.configuration;

/**
 * Base class for exceptions thrown when something goes bad.
 */
public class ConfigurationException extends Exception {

  public ConfigurationException( final String message ) {
    super( message ) ;
  }

  public ConfigurationException( final String message, final Throwable cause ) {
    super( message, cause ) ;
  }


}
