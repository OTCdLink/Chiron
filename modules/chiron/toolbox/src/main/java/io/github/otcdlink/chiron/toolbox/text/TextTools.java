package io.github.otcdlink.chiron.toolbox.text;

import com.google.common.base.Strings;

public final class TextTools {

  private TextTools() { }

  /**
   * A drop-in replamcement for
   * {@code org.apache.commons.lang3.StringUtils#isBlank(java.lang.CharSequence)} version 3.3.2.
   */
  public static boolean isBlank( final String string ) {
    return Strings.nullToEmpty( string ).trim().isEmpty() ;
  }


  /**
   * Copied from
   * {@code org.apache.commons.lang3.StringUtils#contains(java.lang.CharSequence, java.lang.CharSequence)}
   * version 3.3.2.
   */
  public static boolean contains( final CharSequence sequence, final CharSequence searched ) {
    if( sequence == null || searched == null ) {
      return false ;
    }
    return indexOf( sequence, searched, 0 ) >= 0 ;
  }

  /**
   * Copied from
   * {@code org.apache.commons.lang3.CharSequenceUtils#indexOf(java.lang.CharSequence, java.lang.CharSequence, int)}
   * version 3.3.2.
   */
  private static int indexOf(final CharSequence cs, final CharSequence searchChar, final int start ) {
    return cs.toString().indexOf( searchChar.toString(), start ) ;
  }


  /**
   * Copied from
   * {@code org.apache.commons.lang3.StringUtils#capitalize(java.lang.String)}
   * version 3.3.2.
   */
  public static String capitalize(final String string ) {
    final int length ;
    if( string == null || ( length = string.length() ) == 0 ) {
      return string ;
    }

    final char firstChar = string.charAt( 0 ) ;
    if( Character.isTitleCase( firstChar ) ) {
      // already capitalized
      return string ;
    }

    return String.valueOf( Character.toTitleCase( firstChar ) ) + string.substring( 1 ) ;
  }

  /**
   * Copied from {@code org.apache.commons.lang3.CharUtils#isAsciiPrintable(char)} version 3.3.2.
   */
  public static boolean isAsciiPrintable(final char ch) {
    return ch >= 32 && ch < 127;
  }

}
