package com.otcdlink.chiron.toolbox.text;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;

public final class TextWrapTools {

  private TextWrapTools() { }

  private static final CharMatcher WHITESPACE_MATCHER = CharMatcher.whitespace() ;

  private static final CharMatcher LINEBREAK_MATCHER = new CharMatcher() {
    @Override
    public boolean matches( final char c ) {
      return ( c == '\n' || c == '\r' ) ;
    }
  } ;

  public static void writeWrapped(
      final Writer writer,
      final String text,
      final int indent,
      final int width
  ) throws IOException {
    final Iterable< String > lines = Splitter.on( LINEBREAK_MATCHER ).split( text ) ;
    boolean first = true ;
    for( final String line : lines ) {
      if( first ) {
        first = false ;
      } else {
        writer.append( "\n" ) ;
      }
      writeWrapped( writer, Splitter.on( WHITESPACE_MATCHER ).split( line ), indent, width ) ;
    }
  }

  /**
   * Inspired by <a href="http://stackoverflow.com/a/5689524" >StackOverflow</a>.
   */
  private static void writeWrapped(
      final Writer writer,
      final Iterable< String > words,
      final int indent,
      final int width
  ) throws IOException {
    int lineLength = 0 ;
    final String leftPadding = Strings.repeat( " ", indent ) ;
    writer.append( leftPadding ) ;
    final Iterator< String > iterator = words.iterator() ;
    if( iterator.hasNext() ) {
      final String next = iterator.next() ;
      writer.append( next ) ;
      lineLength += next.length() ;
      while( iterator.hasNext() ) {
        final String word = iterator.next() ;
        if( word.length() + 1 + lineLength > width ) {
          writer.append( '\n' ) ;
          writer.append( leftPadding ) ;
          lineLength = 0 ;
        } else {
          lineLength++ ;
          writer.append( ' ' ) ;
        }
        writer.append( word ) ;
        lineLength += word.length() ;
      }
    }
  }

  public static void writeWrapped(
      final StringBuilder stringBuilder,
      final String text,
      final int indent,
      final int width
  ) {
    final StringWriter writer = new StringWriter() ;
    try {
      writeWrapped( writer, text, indent, width ) ;
    } catch( final IOException e ) {
      throw new RuntimeException( "Can't happen", e ) ;
    }
    stringBuilder.append( writer.toString() ) ;
  }
}
