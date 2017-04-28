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

  public static String trimToLength(
      final String text,
      final int maximumLength
  ) {
    return trimToLength( text, maximumLength, "...[trimmed]" ) ;
  }


  public static String toPrintableAscii( final String string ) {
    out : {
      for( int i = 0 ; i < string.length() ; i ++ ) {
        final char c = string.charAt( i ) ;
        if( ! isPrintableAscii( c ) ) {
          break out ;
        }
      }
      return string ;
    }

    final StringBuilder stringBuilder = new StringBuilder() ;
    for( int i = 0 ; i < string.length() ; i ++ ) {
      final char c = string.charAt( i ) ;
      if( isPrintableAscii( c ) ) {
        stringBuilder.append( c ) ;
      } else {
        // http://stackoverflow.com/a/27359340/1923328
        stringBuilder.append( "\\u" ).append( String.format( "%04X", ( int ) c ) ) ;
      }
    }
    return stringBuilder.toString() ;
  }

  private static boolean isPrintableAscii( final char c ) {
    return c >= ' ' && c <= '~' ;
  }

  @SuppressWarnings( "SameParameterValue" )
  public static String trimToLength(
      final String text,
      final int maximumLength,
      final String trimMarker
  ) {
    if( text.length() > maximumLength ) {
      return text.substring( 0, maximumLength ) + trimMarker ;
    } else {
      return text ;
    }
  }

// ===============================
// Copied from Apache Commons-Lang
// ===============================

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
