package io.github.otcdlink.chiron.middle;

import static com.google.common.base.Preconditions.checkArgument;

public interface EnumeratedMessageKind {

  /**
   * Default message for a {@link TypedNotice}.
   */
  String description() ;

  /**
   * Standard {@code Enum} method.
   */
  String name() ;

  /**
   * Standard {@code Enum} method.
   */
  int ordinal() ;

  /**
   * Useful to convert an Enum name into a description.
   */
  static String toDescription( final String javaName ) {
    checkArgument( ! javaName.isEmpty() ) ;
    final StringBuilder builder = new StringBuilder(
        javaName.replace( '_', ' ' ).toLowerCase() ) ;
    builder.setCharAt( 0, Character.toTitleCase( builder.charAt( 0 ) ) ) ;
    return builder.toString() ;
  }


}
