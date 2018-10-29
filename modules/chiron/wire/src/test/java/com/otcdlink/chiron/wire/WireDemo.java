package com.otcdlink.chiron.wire;

import com.google.common.base.Converter;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.io.StringWriter;

public class WireDemo {

  private static final Logger LOGGER = LoggerFactory.getLogger( WireDemo.class ) ;

// ================
// (Kind of) schema
// ================

  /**
   * Declare all the tokens representing a Leaf.
   */
  public static class MyLeafToken< ITEM > extends Wire.LeafToken.Autoconvert< ITEM > {

    public static final MyLeafToken< String > X =
        new MyLeafToken<>( Converter.identity() ) ;

    public static final MyLeafToken< Integer > Y =
        new MyLeafToken<>( Ints.stringConverter() ) ;

    private MyLeafToken( Converter< String, ITEM > converter ) {
      super( converter ) ;
    }
    static final ImmutableMap< String, MyLeafToken> MAP = valueMap( MyLeafToken.class ) ;
  }

  /**
   * Declare all the tokens representing a Node.
   */
  public static class MyNodeToken extends Wire.NodeToken.Auto< MyNodeToken, MyLeafToken > {

    public static final MyNodeToken B = new MyNodeToken(
        ImmutableSet.of(), ImmutableSet.of( MyLeafToken.X, MyLeafToken.Y ) ) ;

    public static final MyNodeToken A = new MyNodeToken(
        ImmutableSet.of( MyNodeToken.B ), ImmutableSet.of() ) ;

    private MyNodeToken(
        final ImmutableSet< MyNodeToken > myNodes,
        final ImmutableSet< MyLeafToken > myLeaves
    ) {
      super( myNodes, myLeaves ) ;
    }

    static final ImmutableMap< String, MyNodeToken> MAP = valueMap( MyNodeToken.class ) ;
  }


// ================================
// Value objects to put on the wire
// ================================

  /**
   * What we want to serialize/deserialize. Contains a collection of {@link SomeB}.
   * Immutable (this guarantees we don't have any cycle).
   */
  public static class SomeA {
    public final ImmutableList< SomeB > children ;

    public SomeA( final ImmutableList< SomeB > children ) {
      this.children = children ;
    }
  }

  /**
   * What we want to serialize/deserialize.
   * Immutable (this guarantees we don't have any cycle).
   */
  public static class SomeB {
    public final String x ;
    public final Integer y ;

    public SomeB( final String x, final Integer y ) {
      this.x = x ;
      this.y = y ;
    }
  }


// ==================
// Writing primitives
// ==================

  private static void writeSomeA(
      final Wire.NodeWriter< MyNodeToken, MyLeafToken > nodeWriter,
      final SomeA someA
  ) throws WireException {
    nodeWriter.nodeSequence( MyNodeToken.B, someA.children, WireDemo::writeSomeB ) ;
  }

  private static void writeSomeB(
      final Wire.NodeWriter< MyNodeToken, MyLeafToken > nodeWriter,
      final SomeB someB
  ) throws WireException {
    nodeWriter.leaf( MyLeafToken.X, someB.x ) ;
    nodeWriter.leaf( MyLeafToken.Y, someB.y ) ;
  }


// ==================
// Reading primitives
// ==================

  private static SomeA readSomeA(
      final Wire.NodeReader< MyNodeToken, MyLeafToken > nodeReader
  ) throws WireException {
    final ImmutableList< SomeB > listOfB = nodeReader.nodeSequence(
        MyNodeToken.B, ImmutableList.toImmutableList(), WireDemo::readSomeB ) ;
    return new SomeA( listOfB ) ;
  }

  private static SomeB readSomeB(
      final Wire.NodeReader< MyNodeToken, MyLeafToken > nodeReader
  ) throws WireException {
    final String x = nodeReader.leaf( MyLeafToken.X ) ;
    final Integer y = nodeReader.leaf( MyLeafToken.Y ) ;
    return new SomeB( x, y ) ;
  }


// =================
// Some action, now!
// =================

  public static void main( final String... arguments ) throws XMLStreamException, WireException {
    final SomeA someA = new SomeA(
        ImmutableList.of(
            new SomeB( "one", 1 ),
            new SomeB( "two", 2 )
        )
    ) ;

    final String xml = Joiner.on( "\n" ).join(
        "<a>",
        "  <b x='one' y='1' />",
        "  <b x='two' y='2' />",
        "</a>"
    ) ;
    final XMLStreamReader xmlStreamReader = newStreamReader( xml ) ;

    final XmlNodeReader< MyNodeToken, MyLeafToken > nodeReader =
        new XmlNodeReader<>( xmlStreamReader, MyNodeToken.MAP, MyLeafToken.MAP ) ;

    final SomeA parsedA = nodeReader.singleNode( MyNodeToken.A, WireDemo::readSomeA ) ;

    @SuppressWarnings( "unused" )
    final SomeA parsedAgainWithMoreCompactCode = new XmlNodeReader<>(
        newStreamReader( xml ),  // Need a fresh one.
        MyNodeToken.MAP,
        MyLeafToken.MAP
    ).singleNode(
        MyNodeToken.A,
        nodeReader1 -> new SomeA( nodeReader1.nodeSequence(
            MyNodeToken.B,
            ImmutableList.toImmutableList(),
            nodeReader2 -> new SomeB(
                nodeReader2.leaf( MyLeafToken.X ),
                nodeReader2.leaf( MyLeafToken.Y )
            )
        ) )
    ) ;

    final StringWriter stringWriter = new StringWriter() ;
    final XmlNodeWriter< MyNodeToken, MyLeafToken > nodeWriter =
        new XmlNodeWriter<>( stringWriter ) ;
    nodeWriter.singleNode( MyNodeToken.A, parsedA, WireDemo::writeSomeA ) ;
    final String xmlWritten = stringWriter.toString() ;

    LOGGER.info( "Rewritten XML: \n" + xmlWritten ) ;


  }


// ======
// Boring
// ======

  static XMLStreamReader newStreamReader( final String xml ) throws XMLStreamException {
    final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance() ;
    final XMLStreamReader xmlStreamReader =
        xmlInputFactory.createXMLStreamReader( new StringReader( xml ) ) ;
    return xmlStreamReader ;
  }

}
