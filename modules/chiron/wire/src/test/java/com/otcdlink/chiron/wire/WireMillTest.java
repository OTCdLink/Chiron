package com.otcdlink.chiron.wire;

import com.google.common.base.Converter;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class WireMillTest {

  @Test
  public void singleEmptyNode() throws XMLStreamException, WireException {
    final WireMill.NodeDeclarationStep builder = WireMill.newBuilder() ;

    final WireMill.Molder molder = builder
        .node( "A" )
        .beginReusableBlock( "r0" )
        .endReusableBlock()
        .root( "A", "r0" )
        .build()
    ;

    apply( molder, "<A/>" ) ;
  }

  @Test
  public void singleNodeWithLeaves() throws XMLStreamException, WireException {
    final WireMill.NodeDeclarationStep builder = WireMill.newBuilder() ;

    final WireMill.Molder molder = builder
        .node( "A" )
            .leaf( "x1", INT )
            .leaf( "x2", INT )
        .beginReusableBlock( "r0" )
        .endReusableBlock()
        .root( "A", "r0" )
        .build()
    ;

    apply( molder, "<A x1='1' x2='1' />" ) ;
  }

  @Test
  public void singleNestedNode() throws XMLStreamException, WireException {
    final WireMill.NodeDeclarationStep builder = WireMill.newBuilder() ;

    final WireMill.Molder molder = builder
        .node( "A" )
            .subnode( "B" )
        .node( "B" )
        .beginReusableBlock( "r0" )
            .single( "B" )
        .endReusableBlock()
        .root( "A", "r0" )
        .build()
    ;

    final WireMill.PivotNode pivotNode = apply( molder,
        "<A>",
        "  <B/>",
        "</A>"
    ) ;

    assertThat( pivotNode.subnodes.get( molder.nodeByName( "B" ) ) ).hasSize( 1 ) ;
  }

  @Test
  public void singleNestedNodeWithAttributes() throws XMLStreamException, WireException {

    final WireMill.Molder molder = WireMill.newBuilder()
        .node( "A" )
            .leaf( "x1", INT )
            .leaf( "x2", INT )
            .subnode( "B" )
        .node( "B" )
            .leaf( "y", INT )
        .beginReusableBlock( "r0" )
            .single( "B" )
        .endReusableBlock()
        .root( "A", "r0" )
        .build()
    ;

    final WireMill.PivotNode pivotNode = apply( molder,
        "<A x1='1' x2='2' >",
        "  <B y='3' />",
        "</A>"
    ) ;

    final ImmutableCollection< WireMill.PivotNode > subnodesOfA =
        pivotNode.subnodes.get( molder.nodeByName( "B" ) ) ;
    assertThat( subnodesOfA ).hasSize( 1 ) ;
    final WireMill.PivotNode bNode = subnodesOfA.iterator().next() ;
    assertThat( bNode.valuedLeaves.get( molder.leafByName( "y" ) ) ).isEqualTo( 3 ) ;
  }




  @Test
  public void complexStructure() {
    final WireMill.NodeDeclarationStep builder = WireMill.newBuilder() ;

    final WireMill.Molder molder = builder
        .node( "A" )
            .leaf( "x", INT )
            .subnode( "A" )
            .subnode( "B" )
        .node( "B" )
            .leaf( "y", INT )
            .subnode( "A" )
            .subnode( "B" )
        .node( "C" )
            .leaf( "z1", INT )
        .leaf( "z2", INT )
        .beginReusableBlock( "rB" )
            .shallowSequence( "A" )
            .reuseAsSequence( "B", "rB" )
        .endReusableBlock()
        .root( "A", "rB" )
        .build()
    ;

    final String xml = Joiner.on( "\n" ).join( ImmutableList.of(
        "<A x='1' >",
        "  <A x='2' />",
        "  <A x='3' />",
        "  <A x='4' />",
        "  <A x='5' />",
        "  <B y='6' >",
        "    <A x='7' />",
        "    <A x='8' />",
        "    <B y='9' >",
        "      <A x='10' />",
        "      <B y='11' />",
        "      <B y='12' />",
        "    </B>",
        "    <B y='13' >",
        "      <A x='14' />",
        "      <A x='15' />",
        "    </B>",
        "  </B>",
        "</A>",
        ""
    ) ) ;
  }


// =======
// Fixture
// =======

  private static final Converter< Integer, String > INT = Ints.stringConverter().reverse() ;

  private static final Logger LOGGER = LoggerFactory.getLogger( WireMillTest.class ) ;

  private static String xml( final String... lines ) {
    return Joiner.on( "\n" ).join( lines ) ;
  }

  private static WireMill.PivotNode apply(
      final WireMill.Molder molder,
      final String... xmlLines
  )
      throws XMLStreamException, WireException
  {
    return apply( molder, xml( xmlLines ) ) ;
  }

  private static WireMill.PivotNode apply( final WireMill.Molder molder, final String xml )
      throws XMLStreamException, WireException
  {
    LOGGER.info( "Original XML: \n" + xml + "\n" ) ;

    final WireMill.PivotNode pivotNode = read( molder, xml ) ;
    LOGGER.info( "Did read: \n  " + pivotNode ) ;

    final String rewrittenXml = write( molder, pivotNode ) ;
    LOGGER.info( "Rewrote: \n" + rewrittenXml ) ;

    final WireMill.PivotNode reread = read( molder, xml ) ;
    LOGGER.info( "Reread: \n  " + reread ) ;

    assertThat( rewrittenXml ).isEqualTo( xml + "\n" ) ;

    return pivotNode ;
  }

  private static String write(
      final WireMill.Molder molder,
      final WireMill.PivotNode pivotNode
  ) throws WireException {
    final StringWriter stringWriter = new StringWriter() ;
    final XmlNodeWriter< WireMill.PlasticNodeToken, WireMill.PlasticLeafToken > xmlNodeWriter =
        new XmlNodeWriter<>( stringWriter ) ;
    xmlNodeWriter.singleNode(
        molder.rootNodeDeclaration(), pivotNode, molder.rootWritingAction() ) ;
    return stringWriter.toString() ;
  }

  private static WireMill.PivotNode read(
      final WireMill.Molder molder,
      final String xml
  ) throws XMLStreamException, WireException {
    final XmlNodeReader< WireMill.PlasticNodeToken, WireMill.PlasticLeafToken > xmlNodeReader =
        new XmlNodeReader<>(
            WireFixture.newStreamReader( xml ),
            molder.nodeDeclarations(),
            molder.leafDeclarations()
        )
    ;
    return xmlNodeReader.singleNode( molder.rootNodeDeclaration(), molder.rootReadingAction() ) ;
  }
}