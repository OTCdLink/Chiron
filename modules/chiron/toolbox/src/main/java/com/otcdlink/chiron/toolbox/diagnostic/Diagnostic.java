package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import com.otcdlink.chiron.toolbox.text.TextWrapTools;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public interface Diagnostic {

  ImmutableList< Diagnostic > subDiagnostics() ;

  /**
   * Prints with reasonable defaults.
   */
  default void print( final Writer writer ) throws IOException {
    print( writer, 0, "  ", " = ", 80, WrapStyle.NONE, LineBreak.DEFAULT ) ;
  }

  String name() ;


  /**
   * Lines of text to print to a character-oriented output.
   */
  ImmutableMultimap< String, String > detail() ;

  /**
   * Associate this key (as an object reference) to values for which a key makes no sense.
   */
  String NO_KEY = "<no-key>" ;

  /**
   * Associate this value (as an object reference) to a key with {@code null} value.
   */
  String NO_VALUE = "<null>" ;

  /**
   * Convenience method for printing to a character-oriented output.
   */
  default void print(
      final Appendable appendable,
      final int depth,
      final String oneIndent,
      final String keyValueSeparator,
      final int preferredMaximumLineLength,
      final WrapStyle wrapStyle,
      final LineBreak lineBreak
  ) throws IOException {
    checkArgument( preferredMaximumLineLength > 0 ) ;
    final String currentIndent = Strings.repeat( oneIndent, depth ) ;
    final int nextDepth = depth + 1 ;
    final String lineBreakAsString = lineBreak.asString ;
    appendable.append( currentIndent ).append( name() ).append( lineBreakAsString ) ;
    for( final Map.Entry< String, String > entry : detail().entries() ) {

      final String key = entry.getKey() ;
      final String value = entry.getValue() ;
      final String line = renderLine( key, keyValueSeparator, value ) ;
      switch( wrapStyle ) {
        case NONE :
          appendable.append( currentIndent ) ;
          appendable.append( oneIndent ) ;
          appendable.append( line ) ;
          break ;
        case SOFT :
          TextWrapTools.writeWrapped(
              appendable,
              line,
              nextDepth * oneIndent.length(),
              preferredMaximumLineLength
          ) ;
          break ;
        default :
          throw new IllegalArgumentException( "Unsupported: " + wrapStyle ) ;
      }
      appendable.append( lineBreakAsString ) ;
    }
    for( final Diagnostic subdiagnostic : subDiagnostics() ) {
      subdiagnostic.print(
          appendable,
          nextDepth,
          oneIndent,
          keyValueSeparator,
          preferredMaximumLineLength,
          wrapStyle,
          lineBreak
      ) ;
    }
  }

  /**
   * Creates a text line with no indention and no word wrap.
   * It should be {@code private static}, this will happen with Java 9.
   */
  static String renderLine(
      final String key,
      final String keyValueSeparator,
      final String value
  ) {
    final StringBuilder lineBuilder = new StringBuilder() ;
    @SuppressWarnings( "StringEquality" )
    final boolean noValue = value == null || value == NO_VALUE ;
    @SuppressWarnings( "StringEquality" )
    final boolean noKey = key == null || key == NO_KEY ;
    if( ! noKey ) {
      lineBuilder.append( key ) ;
      if( ! noValue ) {
        lineBuilder.append( keyValueSeparator ) ;
      }
    }
    if( noValue ) {
      lineBuilder.append( " null" ) ;
    } else {
      lineBuilder.append( value ) ;
    }
    return lineBuilder.toString() ;
  }

  enum WrapStyle { NONE, SOFT }

  Diagnostic NULL = new BaseDiagnostic(
      "Null diagnostic (for tests only)", ImmutableMultimap.of(), ImmutableList.of() ) ;


}
