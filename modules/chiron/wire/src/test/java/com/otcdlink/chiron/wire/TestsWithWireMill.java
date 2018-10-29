package com.otcdlink.chiron.wire;

import com.google.common.base.Converter;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.otcdlink.chiron.buffer.BytebufCoat;
import com.otcdlink.chiron.buffer.BytebufTools;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.toolbox.text.TextTools;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled( "Work in progress, come later")
class TestsWithWireMill {

  @Test
  void singleEmptyNode() throws XMLStreamException, WireException, IOException {
    final WireMill.NodeDeclarationStep builder = WireMill.newBuilder() ;

    final WireMill.Molder molder = builder
        .node( "A" )
        .beginReusableBlock( "r0" )
        .endReusableBlock()
        .root( "A", "r0" )
        .build()
    ;

    test( molder, "<A/>" );
  }

  @Test
  void singleNodeWithLeaves() throws XMLStreamException, WireException, IOException {
    final WireMill.NodeDeclarationStep builder = WireMill.newBuilder() ;

    final WireMill.Molder molder = builder
        .node( "A" )
            .leaf( "x1", STRING )
        .beginReusableBlock( "r0" )
        .endReusableBlock()
        .root( "A", "r0" )
        .build()
    ;

    test( molder, "<A x1='&quot;' />" ) ;
  }

  @Test
  void singleNodeWithNull() throws XMLStreamException, WireException, IOException {
    final WireMill.NodeDeclarationStep builder = WireMill.newBuilder() ;

    final WireMill.Molder molder = builder
        .node( "A" )
            .leaf( "x1", STRING )
        .beginReusableBlock( "r0" )
        .endReusableBlock()
        .root( "A", "r0" )
        .build()
    ;

    final WireMill.PivotNode pivotNode = test( molder, xml( "<A x1='{null}' />" ) );
    assertThat( pivotNode.valuedLeaves.get( molder.leafByName( "x1" ) ) ).isNull() ;
  }

  @Test
  void singleNodeWithAccoladeEscaping() throws XMLStreamException, WireException {
    final WireMill.NodeDeclarationStep builder = WireMill.newBuilder() ;

    final WireMill.Molder molder = builder
        .node( "A" )
            .leaf( "x1", STRING )
        .beginReusableBlock( "r0" )
        .endReusableBlock()
        .root( "A", "r0" )
        .build()
    ;

    final WireMill.PivotNode pivotNode = readXml( molder, "<A x1='{oa}' />" ) ;
    assertThat( pivotNode.valuedLeaves.get( molder.leafByName( "x1" ) ) ).isEqualTo( "{" ) ;
  }

  @Test
  void singleNestedNode() throws XMLStreamException, WireException, IOException {
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

    final WireMill.PivotNode pivotNode = test(
        molder,
        xml(
            "<A>",
            "  <B/>",
            "</A>"
        )
    ) ;
    assertThat( pivotNode.subnodes.get( molder.nodeByName( "B" ) ) ).hasSize( 1 ) ;
  }

  @Test
  void singleNestedNodeWithAttributes() throws XMLStreamException, WireException, IOException {

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

    final WireMill.PivotNode pivotNode = test(
        molder,
        xml(
            "<A x1='1' x2='2' >",
            "  <B y='3' />",
            "</A>"
        )
    ) ;

    final ImmutableCollection< WireMill.PivotNode > subnodesOfA =
        pivotNode.subnodes.get( molder.nodeByName( "B" ) ) ;
    assertThat( subnodesOfA ).hasSize( 1 ) ;
    final WireMill.PivotNode bNode = subnodesOfA.iterator().next() ;
    assertThat( bNode.valuedLeaves.get( molder.leafByName( "y" ) ) ).isEqualTo( 3 ) ;
  }




  @Test
  void complexStructure() throws WireException, XMLStreamException, IOException {
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

    final String xml = xml(
        "<A x='1' >",
        "  <A x='2' />",
        "  <A x='3' />",
        "  <B y='4' >",
        "    <A x='5' />",
        "    <A x='6' />",
        "    <B y='7' >",
        "      <A x='8' />",
        "      <B y='9' />",
        "      <B y='10' />",
        "    </B>",
        "    <B y='11' >",
        "      <A x='12' />",
        "      <A x='13' />",
        "    </B>",
        "  </B>",
        "</A>",
        ""
    ) ;

    final WireMill.PlasticNodeToken nodeTokenA = molder.nodeByName( "A" ) ;
    final WireMill.PlasticLeafToken leafTokenX = molder.leafByName( "x" ) ;
    final WireMill.PlasticNodeToken nodeTokenB = molder.nodeByName( "B" ) ;
    final WireMill.PlasticLeafToken leafTokenY = molder.leafByName( "y" ) ;

    // Handwriting the correct behavior to study how recursion should happen.
    final WireMill.Molder handcraftedReadingMolder = new WireMill.Molder() {
      @Override
      public ImmutableMap< String, WireMill.PlasticLeafToken > leafDeclarations() {
        return molder.leafDeclarations() ;
      }

      @Override
      public ImmutableMap< String, WireMill.PlasticNodeToken > nodeDeclarations() {
        return molder.nodeDeclarations() ;
      }

      @Override
      public WireMill.PlasticNodeToken rootNodeDeclaration() {
        return molder.rootNodeDeclaration() ;
      }

      @Override
      public Wire.NodeReader.ReadingAction<
          WireMill.PlasticNodeToken,
          WireMill.PlasticLeafToken,
          WireMill.PivotNode
          > rootReadingAction() {
        return newReadingAction( nodeTokenA, leafTokenX ) ;
      }

      public Wire.NodeReader.ReadingAction<
          WireMill.PlasticNodeToken,
          WireMill.PlasticLeafToken,
          WireMill.PivotNode
      > newReadingAction(
          final WireMill.PlasticNodeToken nodeToken,
          final WireMill.PlasticLeafToken leafTokenForUniqueAttribute
      ) {
        return nodeReader -> {
          final Integer leafValue = nodeReader.leaf( leafTokenForUniqueAttribute ) ;
          final ImmutableList< WireMill.PivotNode > listOfA = nodeReader.nodeSequence(
              nodeTokenA,
              toImmutableList(),
              nodeReader1 -> {
                final WireMill.MutablePivotNode mutablePivotNode =
                    new WireMill.MutablePivotNode( nodeTokenA ) ;
                mutablePivotNode.valuedLeaves.put( leafTokenX, nodeReader1.leaf( leafTokenX ) ) ;
                return mutablePivotNode.freeze() ;
              }
          ) ;
          final ImmutableList< WireMill.PivotNode > listOfB = nodeReader.nodeSequence(
              nodeTokenB,
              toImmutableList(),
              // Below is the trick to avoid recursive evaluation to happen immediately.
              nodeReader1 -> newReadingAction( nodeTokenB, leafTokenY ).read( nodeReader1 )
          ) ;
          return new WireMill.PivotNode(
              nodeToken,
              new ImmutableMapWithNullableValue<>( ImmutableMap.of(
                  leafTokenForUniqueAttribute, leafValue ) ),
              ImmutableMultimap.< WireMill.PlasticNodeToken, WireMill.PivotNode >builder()
                  .putAll( nodeTokenA, listOfA )
                  .putAll( nodeTokenB, listOfB )
                  .build()
          ) ;
        } ;
      }

      @Override
      public Wire.NodeWriter.WritingAction<
          WireMill.PlasticNodeToken,
          WireMill.PlasticLeafToken,
          WireMill.PivotNode
      > rootWritingAction() {
        return molder.rootWritingAction() ;
      }
    } ;
    test( handcraftedReadingMolder, xml ) ;

//    test( molder, xml ) ;
  }


// =======
// Fixture
// =======

  private static final Converter< Integer, String > INT = Ints.stringConverter().reverse() ;
  private static final Converter< String, String > STRING = Converter.identity() ;

  private static final Logger LOGGER = LoggerFactory.getLogger( TestsWithWireMill.class ) ;

  private static String xml( final String... lines ) {
    return Joiner.on( "\n" ).join( lines ) ;
  }

  private static WireMill.PivotNode test(
      final WireMill.Molder molder,
      final String xml,
      final Verification... verifications
  ) throws XMLStreamException, WireException, IOException {
    return test( molder, xml, verifications.length == 0 ? Verification.ALL :
        ImmutableSet.copyOf( verifications ) ) ;
  }

  private static WireMill.PivotNode test(
      final WireMill.Molder molder,
      final String xml,
      final ImmutableSet< Verification > verifications
  ) throws XMLStreamException, WireException, IOException {

    LOGGER.info( "Original XML: \n" + TextTools.decorateWithLineNumbers( xml ) + "\n" ) ;

    final WireMill.PivotNode pivotNode = readXml( molder, xml ) ;
    LOGGER.info( "Did read (as basis for further assertions): \n  " + pivotNode ) ;

    if( Verification.Target.XML.in( verifications ) ) {
      final String rewrittenXml = writeXml( molder, pivotNode ) ;
      LOGGER.info( "Rewrote: \n" + rewrittenXml ) ;
      assertThat( rewrittenXml ).isEqualTo( xml + "" ) ;
      if( Verification.XML_REREAD.triggeredBy( verifications ) ) {
        final WireMill.PivotNode rereadPivotNode = readXml( molder, xml ) ;
        LOGGER.info( "Reread: \n  " + rereadPivotNode ) ;
        assertThat( rereadPivotNode ).as( "Reread XML" ).isEqualTo( pivotNode ) ;
        if( ! Verification.Target.POSITIONAL.in( verifications ) ) {
          return rereadPivotNode ;
        }
      }
    }

    if( Verification.Target.POSITIONAL.in( verifications ) ) {
      final ByteBuf byteBuf = writePositional( molder, pivotNode ) ;
      LOGGER.info( "Wrote: \n" + ByteBufUtil.prettyHexDump( byteBuf ) ) ;
      final WireMill.PivotNode readPivotNode = readPositional( molder, byteBuf ) ;
      LOGGER.info( "Read: \n  " + readPivotNode ) ;
      assertThat( readPivotNode ).as( "Read Positional" ).isEqualTo( pivotNode ) ;
      if( Verification.POSITIONAL_REWRITE.triggeredBy( verifications ) ) {
        final ByteBuf rewritten = writePositional( molder, pivotNode ) ;
        LOGGER.info( "Rewrote: \n" + ByteBufUtil.prettyHexDump( rewritten ) ) ;
        byteBuf.resetReaderIndex() ;  // Without that, equality comparison will fail.
        assertThat( rewritten ).as( "Rewrite Positional" ).isEqualTo( byteBuf ) ;
        if( Verification.POSITIONAL_REREAD.triggeredBy( verifications ) ) {
          final WireMill.PivotNode rereadPivotNode = readPositional( molder, rewritten ) ;
          LOGGER.info( "Reread: \n  " + readPivotNode ) ;
          assertThat( rereadPivotNode ).as( "Reread Positional" )
              .isEqualTo( pivotNode ) ;
          return rereadPivotNode ;
        } else {
          return readPivotNode ;
        }
      }
    }
    return null ;
  }

  private static String writeXml(
      final WireMill.Molder molder,
      final WireMill.PivotNode pivotNode
  ) throws WireException {
    final StringWriter stringWriter = new StringWriter() ;
    final XmlNodeWriter< WireMill.PlasticNodeToken, WireMill.PlasticLeafToken > xmlNodeWriter =
        new XmlNodeWriter<>( stringWriter ) ;
    molder.rootWritingAction().write( xmlNodeWriter, pivotNode ) ;
    return stringWriter.toString() ;
  }

  private static WireMill.PivotNode readXml(
      final WireMill.Molder molder,
      final String xml
  ) throws XMLStreamException, WireException {
    final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance() ;
    final XMLStreamReader xmlStreamReader =
        xmlInputFactory.createXMLStreamReader( new StringReader( xml ) ) ;
    final XmlNodeReader< WireMill.PlasticNodeToken, WireMill.PlasticLeafToken > xmlNodeReader =
        new XmlNodeReader<>(
            xmlStreamReader,
            molder.nodeDeclarations(),
            molder.leafDeclarations()
        )
    ;
    return xmlNodeReader.singleNode( molder.rootNodeDeclaration(), molder.rootReadingAction() ) ;
  }

  private static ByteBuf writePositional(
      final WireMill.Molder molder,
      final WireMill.PivotNode pivotNode
  ) throws IOException {
    final ByteBuf byteBuf = Unpooled.buffer() ;
    final NodeEncoder< WireMill.PlasticNodeToken, WireMill.PlasticLeafToken, WireMill.PivotNode >
        nodeEncoder = new NodeEncoder<>(
            molder.nodeDeclarations(),
            molder.leafDeclarations(),
            molder.rootNodeDeclaration(),
            molder.rootWritingAction()
        )
    ;
    final BytebufCoat coat = BytebufTools.threadLocalRecyclableCoating().coat( byteBuf ) ;
    nodeEncoder.encodeTo( pivotNode, coat ) ;
    return byteBuf ;
  }

  private static WireMill.PivotNode readPositional(
      final WireMill.Molder molder,
      final ByteBuf byteBuf
  ) throws DecodeException {
    final BytebufCoat coat = BytebufTools.threadLocalRecyclableCoating().coat( byteBuf ) ;
    final NodeDecoder< WireMill.PlasticNodeToken, WireMill.PlasticLeafToken, WireMill.PivotNode >
        nodeDecoder = new NodeDecoder<>(
        molder.nodeDeclarations(),
        molder.leafDeclarations(),
        molder.rootNodeDeclaration(),
        molder.rootReadingAction()
    ) ;
    final WireMill.PivotNode pivotNode = nodeDecoder.decodeFrom( coat ) ;
    return pivotNode ;
  }


  enum Verification {
    XML_REWRITE( Target.XML ),
    XML_REREAD( Target.XML ),
    POSITIONAL_READ( Target.POSITIONAL ),
    POSITIONAL_REWRITE( Target.POSITIONAL ),
    POSITIONAL_REREAD( Target.POSITIONAL ),
    ;
    public final Target target ;
    public static final ImmutableSet< Verification > ALL =
        ImmutableSet.copyOf( Verification.values() ) ;

    Verification( Target target ) {
      this.target = target ;
    }

    public boolean triggeredBy( final ImmutableSet< Verification > verifications ) {
      for( final Verification verification : verifications ) {
        if( verification.target == this.target && verification.ordinal() >= ordinal() ) {
          return true ;
        }
      }
      return false ;
    }

    public enum Target {
      XML, POSITIONAL, ;
      boolean in( final ImmutableSet< Verification > verifications ) {
        return verifications.stream().anyMatch( v -> v.target == this ) ;
      }
    }
  }
}