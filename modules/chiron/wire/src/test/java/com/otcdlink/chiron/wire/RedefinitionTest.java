package com.otcdlink.chiron.wire;

import com.google.common.base.Converter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static com.google.common.collect.ImmutableSet.of;
import static org.assertj.core.api.Assertions.assertThat;

public class RedefinitionTest {

  @Test
  public void read() throws XMLStreamException, WireException {
    final XMLStreamReader xmlStreamReader = WireFixture.newStreamReader(
        "<terminal>",
        "  <t xxx='x' />",
        "</terminal>"
    ) ;
    final XmlNodeReader<WireFixture.MyNodeToken, WireFixture.MyLeafToken> nodeReader = new XmlNodeReader<>(
        xmlStreamReader,
        WireFixture.MyNodeToken.MAP,
        WireFixture.MyLeafToken.MAP
    ) ;
    final String[] capture = new String[] { null } ;
    nodeReader.singleNode(
        WireFixture.MyNodeToken.TERMINAL,
        nodeReader1 -> {
          nodeReader1.redefineWith( NodeToken1.MAP, LeafToken1.MAP )
              .singleNode(
                  NodeToken1.T,
                  nodeReader2 -> {
                    capture[ 0 ] = nodeReader2.leaf( LeafToken1.XXX ) ;
                    return null ;
                  }
              )
          ;
          return null ;
        }
    ) ;
    assertThat( capture[ 0 ] ).isEqualTo( "x" ) ;
  }

// =======
// Fixture
// =======

  public static class LeafToken1< ITEM > extends Wire.LeafToken.Autoconvert< ITEM > {

    public static final LeafToken1< String > XXX =
        new LeafToken1<>( Converter.identity() ) ;

    private LeafToken1( final Converter converter ) {
      super( converter ) ;
    }
    public static final ImmutableMap< String, LeafToken1 > MAP = valueMap( LeafToken1.class ) ;
  }

  public static class NodeToken1
      extends Wire.NodeToken.Auto< NodeToken1, LeafToken1 >
  {

    public static final NodeToken1 T = new NodeToken1(
        of(), of( LeafToken1.XXX ) ) ;

    public NodeToken1(
        final ImmutableSet<NodeToken1> nodeTokens,
        final ImmutableSet<LeafToken1> leafTokens
    ) {
      super( nodeTokens, leafTokens ) ;
    }

    public static final ImmutableMap< String, NodeToken1 > MAP = valueMap( NodeToken1.class ) ;
  }

}
