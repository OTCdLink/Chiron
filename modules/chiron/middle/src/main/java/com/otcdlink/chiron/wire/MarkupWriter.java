package com.otcdlink.chiron.wire;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.html.HtmlEscapers;
import com.otcdlink.chiron.toolbox.text.LineBreak;

import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * An XML/HTML renderer with no constraint on element or attribute names.
 * Prints nested indents, avoids too many attributes on the same line,
 * and collapses elements/tags with no content.
 *
 * <h1>Design notes</h1>
 * <p>
 * Refactor to use a {@link io.netty.buffer.ByteBuf}, which is better than every other abstraction.
 */
public class MarkupWriter {

  protected final Appendable appendable ;
  private final int maxAttributesOnSameLine ;
  protected int indent = 0 ;
  protected final LineBreak lineBreak ;

  private boolean unclosedStartingElement = false ;

  public MarkupWriter( final Writer appendable ) {
    this( appendable, 2 ) ;
  }

  public MarkupWriter( final Writer appendable, final int maxAttributesOnSameLine ) {
    this( appendable, 0, maxAttributesOnSameLine, LineBreak.DEFAULT ) ;
  }

  public MarkupWriter(
      final Appendable appendable,
      final int startIndent,
      final int maxAttributesOnSameLine,
      final LineBreak lineBreak
  ) {
    this.appendable = checkNotNull( appendable ) ;
    checkArgument( startIndent >= 0 ) ;
    this.indent = startIndent ;
    checkArgument( maxAttributesOnSameLine >= 0 ) ;
    this.maxAttributesOnSameLine = maxAttributesOnSameLine ;
    this.lineBreak = checkNotNull( lineBreak ) ;
  }

  public final void startDocument( final String header ) throws IOException {
    appendable.append( header ).append( lineBreak.asString ) ;
  }

  public final void endDocument() throws IOException {
    checkState( elementStack.isEmpty(), "Unclosed elements, stack not empty: " + elementStack ) ;
    if( appendable instanceof Flushable ) {
      ( ( Flushable ) appendable ).flush() ;
    }
  }

  public final void singleElement(
      final String element,
      final String ... nameValueAttributes
  ) throws IOException {
    closeStartingElementIfNeeded( true ) ;
    element( element, ElementClosing.SINGLE, nameValueAttributes ) ;
  }

  public final LineBreak lineBreak() {
    return lineBreak ;
  }

  /**
   * Power to do just anything.
   */
  public final void freeformWrite(
      final boolean lineBreakBefore,
      final Freeform freeform
  )
      throws IOException
  {
    closeStartingElementIfNeeded( lineBreakBefore ) ;
    freeform.use( appendable ) ;
  }


  public final void elementWithText(
      final String element,
      final String text,
      final boolean htmlEscape,
      final boolean collapseEmptyTag,
      final String... attributes
  ) throws IOException {
    if( collapseEmptyTag && Strings.isNullOrEmpty( text ) ) {
      singleElement( element, attributes ) ;
    } else {
      closeStartingElementIfNeeded( true ) ;
      element( element, ElementClosing.STARTING_KEEP_ON_SAME_LINE, attributes ) ;
      appendable.append( htmlEscape ? htmlEscape( text ) : text ) ;
      appendable.append( "</" ).append( element ).append( '>' ).append( lineBreak.asString ) ;
      unclosedStartingElement = false ;
    }

  }

  private final LinkedList< String > elementStack = new LinkedList<>() ;

  public final void startElement(
      final String element,
      final String ... nameValueAttributes
  ) throws IOException {
    startElement( element, Lists.newArrayList( nameValueAttributes ) ) ;
  }

  public final void startElement(
      final String element,
      final List< String > nameValueAttributes
  ) throws IOException {
    element( element, ElementClosing.UNCLOSED, nameValueAttributes ) ;
    indent++ ;
    elementStack.push( element ) ;
  }

  private void element(
      final String element,
      final ElementClosing elementClosing,
      final String... nameValueAttributes
  ) throws IOException {
    element( element, elementClosing, Lists.newArrayList( nameValueAttributes ) ) ;
  }

  private void element(
      final String element,
      final ElementClosing elementClosing,
      final List< String > nameValueAttributes
  ) throws IOException {
    checkArgument( nameValueAttributes.size() % 2 == 0 ) ;
    closeStartingElementIfNeeded( true ) ;
    final StringBuilder stringBuilder = new StringBuilder() ;
    stringBuilder.append( '<' ) ;
    stringBuilder.append( element ) ;

    final boolean lotsOfAttributes = nameValueAttributes.size() > maxAttributesOnSameLine * 2 ;
    final String preAttributeSeparator = lotsOfAttributes ?
        lineBreak.asString + leftMargin( indent + 2 ) :
        ""
    ;

    if( nameValueAttributes.size() > 0 ) {
      stringBuilder.append( ' ' ) ;
      for( int i = 0 ; i < nameValueAttributes.size() ; i += 2 ) {
        stringBuilder.append( preAttributeSeparator ) ;
        stringBuilder.append( nameValueAttributes.get( i ) ) ;
        stringBuilder.append( "='" ) ;
        stringBuilder.append( htmlEscape( nameValueAttributes.get( i + 1 ) ) ) ;
        stringBuilder.append( "\' " ) ;
      }
    }
    if( lotsOfAttributes ) {
      stringBuilder.append( lineBreak.asString ).append( leftMargin( indent ) ) ;
    }
    switch( elementClosing ) {
      case STARTING_KEEP_ON_SAME_LINE :
        stringBuilder.append( '>' ) ;
        writeUnterminatedLine( stringBuilder.toString() ) ;
        unclosedStartingElement = false ;
        break;
      case STARTING :
        stringBuilder.append( '>' ) ;
        writeCompleteLine( stringBuilder.toString() ) ;
        unclosedStartingElement = false ;
        break;
      case SINGLE :
        stringBuilder.append( "/>" ) ;
        writeCompleteLine( stringBuilder.toString() ) ;
        unclosedStartingElement = false ;
        break ;
      case UNCLOSED :
        writeUnterminatedLine( stringBuilder.toString() ) ;
        unclosedStartingElement = true ;
        break ;
      default :
        throw new IllegalArgumentException( "Unsupported: " + elementClosing ) ;
    }
  }

  public final void endElement()
      throws IOException 
  {
    indent-- ;
    final String element = elementStack.pop() ;
    if( unclosedStartingElement ) {
      appendable.append( "/>" ).append( lineBreak.asString ) ;
      unclosedStartingElement = false ;
    } else {
      writeCompleteLine( "</" + element + '>' ) ;
    }
  }

  /**
   * We might leave starting element with no {@code >} so we can print {@code />}
   * if it turns out that we are closing it after adding nothing.
   */
  private void closeStartingElementIfNeeded( final boolean appendLineBreak ) throws IOException {
    if( unclosedStartingElement ) {
      appendable.append( ">" ) ;
      if( appendLineBreak ) {
        appendable.append( lineBreak.asString ) ;
      }
      unclosedStartingElement = false ;
    }
  }

  private void writeCompleteLine( final String line ) throws IOException {
    writeUnterminatedLine( line ) ;
    appendable.append( lineBreak.asString ) ;
  }

  private void writeUnterminatedLine( final String unindentedText ) throws IOException {
    for( int i = 0  ; i < indent ; i++ ) {
      appendable.append( "  " ) ;
    }
    appendable.append( unindentedText ) ;
  }

  private String leftMargin( final int indent ) {
    final StringBuilder builder = new StringBuilder( indent + 2 ) ;
    for( int i = 0  ; i < indent ; i++ ) {
      builder.append( "  " ) ;
    }
    return builder.toString() ;
  }

  private enum ElementClosing {
    STARTING_KEEP_ON_SAME_LINE,
    STARTING,
    SINGLE,
    UNCLOSED
  }


  public static final Charset CHARSET_UTF8 = Charsets.UTF_8 ;

  private static String htmlEscape( final String text ) {
    return HtmlEscapers.htmlEscaper().escape( text ) ;
  }

  /**
   * Wraps the usage of a, {@link Appendable} so we control what happens before and after using it.
   */
  public interface Freeform {
    void use( Appendable appendable ) throws IOException ;
  }
}
