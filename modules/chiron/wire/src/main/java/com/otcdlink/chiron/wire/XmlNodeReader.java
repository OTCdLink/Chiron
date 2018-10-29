package com.otcdlink.chiron.wire;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.toolbox.collection.StreamTools;
import com.otcdlink.chiron.wire.Wire.NodeToken;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

public class XmlNodeReader<
    NODE extends NodeToken< NODE, LEAF >,
    LEAF extends Wire.LeafToken
> extends AbstractXmlNodeReader< NODE, LEAF > {

  public XmlNodeReader(
      final XMLStreamReader xmlStreamReader,
      final ImmutableCollection< NODE > nodes,
      final ImmutableCollection< LEAF > leaves
  ) {
    super( xmlStreamReader, nodes, leaves ) ;
    this.eventSource = new EventSource() ;
  }

  public XmlNodeReader(
      final XMLStreamReader xmlStreamReader,
      final ImmutableMap< String, NODE > nodeNames,
      final ImmutableMap< String, LEAF > leafNames
  ) {
    super( xmlStreamReader, nodeNames, leafNames ) ;
    this.eventSource = new EventSource() ;
  }


// ========
// Cloaking
// ========

  protected XmlNodeReader(
      XmlNodeReader parent,
      final ImmutableMap< String, NODE > overridingNodeNames,
      final ImmutableMap< String, LEAF > overridingLeafNames
  ) {
    super( parent.xmlStreamReader, overridingNodeNames, overridingLeafNames ) ;
    this.eventSource = parent.eventSource ;
  }

  public static XMLStreamReader newXmlStreamReaderQuiet( final String xml ) {
    try {
      return newXmlStreamReader( xml ) ;
    } catch( XMLStreamException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public static XMLStreamReader newXmlStreamReader( final String xml ) throws XMLStreamException {
    final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance() ;
    return xmlInputFactory.createXMLStreamReader( new StringReader( xml ) ) ;
  }

  @Override
  public <
      NODE2 extends NodeToken< NODE2, LEAF2 >,
      LEAF2 extends Wire.LeafToken
  > XmlNodeReader< NODE2, LEAF2 > redefineWith(
      final ImmutableMap< String, NODE2 > nodes,
      final ImmutableMap< String, LEAF2 > leaves
  ) {
    checkState( eventSource.pushedBack == null,
        "Redefinition supported only within a single node" ) ;
    return new XmlNodeReader<>( this, nodes, leaves ) ;
  }


// ===============
// Node and Leaves
// ===============

  @Override
  public < ITEM_LEAF extends Wire.LeafToken< ITEM >, ITEM > ITEM leaf(
      final ITEM_LEAF leafWithTypedItem
  ) throws WireException {
    try {
      final String attributeValue = xmlStreamReader.getAttributeValue(
          "", leafWithTypedItem.xmlName() ) ;
      if( attributeValue == null ) {
        throw wireExceptionGenerator.throwWireException(
            "No value defined for attribute '" + leafWithTypedItem.xmlName() + "'" ) ;
      } else {
        if( XmlEscaping.MAGIC_NULL.equals( attributeValue ) ) {
          debug( () -> "Extracted Attribute '" + leafWithTypedItem.xmlName() + "' null." ) ;
          return null ;
        } else {
          if( XmlEscaping.ACCOLADE_UNESCAPER.needsTransformation( attributeValue ) ) {
            final StringBuilder stringBuilder = new StringBuilder() ;
            XmlEscaping.ACCOLADE_UNESCAPER.transform( attributeValue, stringBuilder ) ;
            attributeAsString = stringBuilder.toString() ;
          } else {
            attributeAsString = attributeValue ;
          }
          final ITEM item = leafWithTypedItem.fromWire( crudeReader ) ;
          debug( () -> "Extracted Attribute '" + leafWithTypedItem.xmlName() + "' " + item + "." ) ;
          return item ;
        }

      }
    } catch( final Exception e ) {
      throw ensureWireException( e ) ;
    }
  }

  @Override
  public < ITEM > ITEM singleNode(
      final NODE node,
      final ReadingAction< NODE, LEAF, ITEM > readingAction
  ) throws WireException {
    final StaxEvent startEvent = eventSource.next() ;
    if( ! startEvent.isStartFor( node.xmlName() ) ) {
      throw wireExceptionGenerator.throwWireException(
          "Expecting " + StaxEvent.Kind.START_ELEMENT + " for " + node ) ;
    }
    debug( () -> "Entering singleNode '" + node.xmlName() + "'." ) ;
    final ITEM read = readingAction.read( this ) ;
    final StaxEvent endEvent = eventSource.next() ;
    if( endEvent.kind != StaxEvent.Kind.END_ELEMENT ) {
      throw wireExceptionGenerator.throwWireException(
          "Expecting " + StaxEvent.Kind.END_ELEMENT + " for " + node ) ;
    }
    debug( () -> "Exiting singleNode '" + node.xmlName() + "'." ) ;
    return read ;
  }

  @Override
  public < ITEM, COLLECTION > COLLECTION nodeSequence(
      final NODE node,
      final Collector< ITEM, ?, COLLECTION > collector,
      final ReadingAction< NODE, LEAF, ITEM > readingAction
  ) throws WireException {
    final Object container = collector.supplier().get() ;
    final BiConsumer< Object, ITEM > accumulator =
        ( BiConsumer< Object, ITEM > ) collector.accumulator() ;
    final Function< ?, COLLECTION > finisher = collector.finisher() ;
    debug( () -> "Entering nodeSequence '" + node.xmlName() + "'." ) ;
    while( true ) {
      final StaxEvent staxEvent = eventSource.next() ;
      if( staxEvent.kind == StaxEvent.Kind.START_ELEMENT ) {
        if( staxEvent.xmlName.equals( node.xmlName() ) ) {
          final ITEM item = readingAction.read( this ) ;
          accumulator.accept( container, item ) ;
          if( eventSource.next().kind != StaxEvent.Kind.END_ELEMENT ) {
            throw wireExceptionGenerator.throwWireException( "Parsing consistency problem, " +
                "expecting " + StaxEvent.Kind.START_ELEMENT + " for " + node ) ;
          }
        } else {
          // End of sequence, beginning of a sibling element, so we should not consume it.
          eventSource.pushback( staxEvent ) ;
          break ;
        }
      } else if( staxEvent.kind == StaxEvent.Kind.END_ELEMENT ) {
        // End of sequence and end of containing element, so we should not consume what belongs
        // to the containing element.
        eventSource.pushback( staxEvent ) ;
        break ;
      } else {
        throw new UnsupportedOperationException( "Unsupported: " + staxEvent.kind ) ;
      }

    }
    debug( () -> "Exiting nodeSequence '" + node.xmlName() + "'." ) ;
    final COLLECTION collection = ( COLLECTION ) ( ( Function ) finisher ).apply( container ) ;
    return collection ;
  }

  @Override
  public < ITEM > void nodeSequence(
      final NODE node,
      final ReadingAction< NODE, LEAF, ITEM > readingAction
  ) throws WireException {
    nodeSequence( node, StreamTools.nullCollector(), readingAction ) ;
  }


// ===========
// EventSource
// ===========

  private final EventSource eventSource ;

  static class StaxEvent {
    public enum Kind { START_ELEMENT, END_ELEMENT }
    private final Kind kind ;
    private final String xmlName ;

    public StaxEvent( final Kind kind, final String xmlName ) {
      this.kind = checkNotNull( kind ) ;
      checkArgument( ! isNullOrEmpty( xmlName ) ) ;
      this.xmlName = xmlName;
    }

    public boolean isStartFor( final String xmlName ) {
      return kind == Kind.START_ELEMENT && this.xmlName.equals( xmlName ) ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{" +
          "kind=" + kind +
          ";xmlName='" + xmlName + '\'' +
          '}'
      ;
    }
  }

  class EventSource {

    private StaxEvent pushedBack = null ;

    public StaxEvent next() throws WireException {
      if( pushedBack == null ) {
        final StaxEvent purifiedStaxEvent = nextPurifiedStaxEvent() ;
        debug( () -> EventSource.class.getSimpleName() + " returns " + purifiedStaxEvent ) ;
        return purifiedStaxEvent ;
      } else {
        final StaxEvent returned = pushedBack ;
        pushedBack = null ;
        debug( () -> EventSource.class.getSimpleName() + " gives back " + returned  + ".") ;
        return returned ;
      }
    }

    public void pushback( final StaxEvent staxEvent ) {
      checkState( pushedBack == null ) ;
      pushedBack = checkNotNull( staxEvent ) ;
      debug( () -> EventSource.class.getSimpleName() + " pushed back " + pushedBack + "." ) ;
    }


    private final List< StaxEvent > breadcrumbs = new ArrayList<>() ;

    ImmutableList< StaxEvent > breadcrumbs() {
      return ImmutableList.copyOf( breadcrumbs ) ;
    }

  }

  private StaxEvent nextPurifiedStaxEvent() throws WireException {
    while( true ) {
      final int next ;
      try {
        next = xmlStreamReader.next() ;  // Consume anything, could be comments or whatever.
      } catch( XMLStreamException e ) {
        throw wireExceptionGenerator.throwWireException( e ) ;
      }
      switch( next ) {
        case XMLEvent.START_ELEMENT :
          return new StaxEvent( StaxEvent.Kind.START_ELEMENT, xmlStreamReader.getLocalName() ) ;
        case XMLEvent.END_ELEMENT :
          return new StaxEvent( StaxEvent.Kind.END_ELEMENT, xmlStreamReader.getLocalName() ) ;
        case XMLEvent.END_DOCUMENT :
          throw wireExceptionGenerator.throwWireException( "Premature document end" ) ;
      }
    }
  }


  /**
   * Includes {@link Wire.Location} so it helps debugging.
   */
  private WireException ensureWireException( final Exception e ) throws WireException {
    if( e instanceof WireException ) {
      throw ( WireException ) e ;
    } else {
      throw wireExceptionGenerator.throwWireException( e ) ;
    }
  }

  @Override
  protected Wire.Location location() {
    final javax.xml.stream.Location location = xmlStreamReader.getLocation() ;

    final String allBreadcrumbs = eventSource.breadcrumbs().stream()
        .map( staxEvent -> staxEvent.xmlName ).collect( Collectors.joining( "/" ) ) ;

    return new Wire.Location(
        location.getSystemId(),
        location.getLineNumber(),
        location.getColumnNumber(),
        location.getCharacterOffset(),
        allBreadcrumbs
    ) ;
  }
}
