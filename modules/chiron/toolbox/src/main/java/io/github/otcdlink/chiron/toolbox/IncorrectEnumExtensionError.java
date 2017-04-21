package io.github.otcdlink.chiron.toolbox;

/**
 * Thrown when static initializer detects inconsistent enum extension.
 */
public class IncorrectEnumExtensionError extends Error {
  public IncorrectEnumExtensionError( final String s ) {
    super( s ) ;
  }
}
