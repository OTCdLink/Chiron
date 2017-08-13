package com.otcdlink.chiron.toolbox.text;

import com.google.common.base.Strings;
import com.google.common.io.CharSink;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class LineWriter implements Closeable {

  private final Writer writer ;
  private final LineBreak lineBreak ;
  private int lineCount = 0 ;

  public LineWriter( final CharSink charSink ) throws IOException {
    this( charSink, LineBreak.CR_UNIX ) ;
  }

  public LineWriter( final CharSink charSink, final LineBreak lineBreak ) throws IOException {
    this( charSink.openStream(), lineBreak ) ;
  }

  public LineWriter( final Writer writer, final LineBreak lineBreak ) {
    this.writer = checkNotNull( writer ) ;
    this.lineBreak = checkNotNull( lineBreak ) ;
  }

  public void write( final String line ) throws IOException {
    writer.append( line ).append( lineBreak.asString ) ;
    lineCount ++ ;
  }

  public int lineCount() {
    return lineCount ;
  }

  @Override
  public void close() throws IOException {
    writer.close() ;
  }

  public static class Indented extends LineWriter {

    public Indented( final Writer writer ) {
      super( writer, LineBreak.CR_UNIX ) ;
    }

    public Indented( final CharSink charSink ) throws IOException {
      super( charSink ) ;
    }

    int indent = 0 ;

    public void indent() {
      indent ++ ;
    }

    public void outdent() {
      indent -- ;
      checkState( indent >= 0 ) ;
    }

    public void blankLine() throws IOException {
      write( "" ) ;
    }

    public void writeIndented( final String line ) throws IOException {
      write( Strings.repeat( "  ", indent ) + line ) ;
    }
  }
}
