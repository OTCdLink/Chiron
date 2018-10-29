package com.otcdlink.chiron.wire;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.CrudeWriter;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import io.netty.handler.codec.EncoderException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class XmlNodeWriter<
    NODE extends Wire.NodeToken< NODE, LEAF >,
    LEAF extends Wire.LeafToken
> implements Wire.NodeWriter< NODE, LEAF > {

  private final MarkupWriter markupWriter ;

  public XmlNodeWriter( final Appendable appendable ) {
    this( appendable, 0 ) ;
  }

  public XmlNodeWriter( final Appendable appendable, final int startIndent ) {
    this( appendable, startIndent, 2, LineBreak.CR_UNIX ) ;
  }

  public XmlNodeWriter(
      final Appendable appendable,
      final int startIndent,
      final int maxAttributesOnSameLine,
      final LineBreak lineBreak
  ) {
    this.markupWriter = new MarkupWriter(
        appendable,
        startIndent,
        maxAttributesOnSameLine,
        lineBreak
    ) ;
  }

  private final StringBuilder attributeSink = new StringBuilder() ;

  private final CrudeWriter wireWriter = new CrudeWriter() {

    @Override
    public void writeIntegerPrimitive( final int i ) {
      attributeSink.append( Integer.toString( i ) ) ;
    }

    @Override
    public void writeIntegerObject( final Integer integer ) {
      if( integer == null ) {
        writeDelimitedString( null ) ;
      } else {
        writeIntegerPrimitive( integer ) ;
      }
    }

    @Override
    public void writeDelimitedString( final String string ) throws EncoderException {
      attributeSink.append( string ) ;
    }

    @Override
    public void writeNullableString( String string ) {
      if( string == null ) {
        attributeSink.append( XmlEscaping.MAGIC_NULL ) ;
      } else {
        try {
          XmlEscaping.ACCOLADE_ESCAPER.transform( string, attributeSink ) ;
        } catch( final IOException e ) {
          throw new EncoderException( "Cound not write '" + string + "'" ) ;
        }
      }
    }

    @Override
    public void writeLongPrimitive( long longPrimitive ) {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public void writeLongObject( Long longObject ) {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public void writeFloatPrimitive( float floatPrimitive ) {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public void writeFloatObject( Float floatObject ) {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public void writeBooleanPrimitive( boolean booleanPrimitive ) {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public void writeBooleanObject( Boolean booleanObject ) {
      throw new UnsupportedOperationException( "TODO" );
    }
  } ;

  private final LinkedList< OpeningElement > elementStack = new LinkedList<>() ;

  private static class OpeningElement {
    private final String elementName ;

    /**
     * Odd items are attribute names, even items are attribute values.
     */
    private final List< Object > attributes = new ArrayList<>() ;

    public OpeningElement( final String elementName ) {
      checkArgument( ! Strings.isNullOrEmpty( elementName ) ) ;
      this.elementName = elementName ;
    }

    private boolean startElementWritten = false ;

    public void startElementWasWritten() {
      checkState( ! startElementWritten ) ;
      startElementWritten = true ;
    }

    public boolean wasStartElementWritten() {
      return startElementWritten ;
    }

    private int nestedElementCount = 0 ;

    public void incrementNestedElementCount() {
      nestedElementCount ++ ;
    }

    public boolean hasNestedElement() {
      return nestedElementCount > 0 ;
    }

    @Override
    public String toString() {
      return OpeningElement.class.getSimpleName() + "{" +
          "elementName='" + elementName + '\'' +
          ";attributes=" + attributes.size() +
          ";startElementWritten=" + startElementWritten +
          ";nestedElementCount=" + nestedElementCount +
          '}'
      ;
    }
  }


  @Override
  public < ITEM_LEAF extends Wire.LeafToken< ITEM >, ITEM > void leaf(
      final ITEM_LEAF leafForItem,
      final ITEM item
  ) {
    checkState( ! elementStack.isEmpty(), "No Element defined" ) ;
    final OpeningElement openingElement = elementStack.getFirst() ;
    openingElement.attributes.add( leafForItem ) ;
    openingElement.attributes.add( item ) ;
  }

  @Override
  public < ITEM > void singleNode(
      final NODE node,
      final ITEM item,
      final WritingAction< NODE, LEAF, ITEM > writingAction
  ) throws WireException {
    start( node.xmlName() ) ;
    writingAction.write( this, item ) ;
    end() ;
  }

  @Override
  public < ITEM > void nodeSequence(
      final NODE node,
      final int count,
      final Iterable< ITEM > items,
      final WritingAction< NODE, LEAF, ITEM > writingAction
  ) throws WireException {
    checkArgument( count >= 0 ) ;
    final Iterator< ITEM > iterator = items.iterator() ;
    for( int i = 0 ; i < count ; i ++ ) {
      final ITEM next = iterator.next() ;
      start( node.xmlName() ) ;
      writingAction.write( this, next ) ;
      end() ;
    }
  }

  @Override
  public <
      NODE2 extends Wire.NodeToken< NODE2, LEAF2 >,
      LEAF2 extends Wire.LeafToken
  > XmlNodeWriter< NODE2, LEAF2 > redefineWith(
      final ImmutableMap< String, NODE2 > newNodeTokens,
      final ImmutableMap< String, LEAF2 > newLeafTokens
  ) {
    return ( XmlNodeWriter< NODE2, LEAF2 > ) this ;
  }

  private void start( final String elementName ) throws WireException {
    if( ! elementStack.isEmpty() ) {
      elementStack.getFirst().incrementNestedElementCount() ;
    }
    openElementWithAttributes() ;
    elementStack.push( new OpeningElement( elementName ) ) ;
  }

  private void openElementWithAttributes() throws WireException {
    if( ! elementStack.isEmpty() ) {
      final OpeningElement openingElement = elementStack.getFirst() ;
      if( ! openingElement.wasStartElementWritten() ) {
        openingElement.startElementWasWritten() ;
        if( openingElement.attributes.isEmpty() ) {
          startElementQuiet( openingElement.elementName, ImmutableList.of() ) ;
        } else {
          final List< String > attributeKeyPairs =
              new ArrayList<>( openingElement.attributes.size() ) ;
          for( int i = 0 ; i < openingElement.attributes.size() ; ) {
            final Wire.LeafToken leafToken = ( Wire.LeafToken )
                openingElement.attributes.get( i ) ;
            attributeKeyPairs.add( i ++, leafToken.xmlName() ) ;
            leafToken.toWire( openingElement.attributes.get( i ), wireWriter ) ;
            attributeKeyPairs.add( i ++, attributeSink.toString() ) ;
            attributeSink.setLength( 0 ) ;
          }
          startElementQuiet( openingElement.elementName, attributeKeyPairs ) ;
        }
      }
    }
  }


  private void end() throws WireException {
    final OpeningElement openingElement = elementStack.getFirst() ;
    if( ! openingElement.hasNestedElement() ) {
      openElementWithAttributes() ;
    }
    elementStack.pop() ;
    endElementQuiet() ;
  }



// ======
// Boring
// ======

  private void startElementQuiet( final String elementName, final List< String > attributes )
      throws WireException
  {
    try {
      markupWriter.startElement( elementName, attributes ) ;
    } catch( IOException e ) {
      throw wireExceptionGenerator.throwWireException( e ) ;
    }
  }

  private void endElementQuiet() throws WireException {
    try {
      markupWriter.endElement() ;
    } catch( IOException e ) {
      throw wireExceptionGenerator.throwWireException( e ) ;
    }
  }


  private Wire.Location location() {
    return new Wire.Location( null, -1, -1, -1, null ) ;
  }

  private final WireException.Generator wireExceptionGenerator =
      new WireException.Generator( this::location ) ;


  public static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" ;
}
