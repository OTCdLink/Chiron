package com.otcdlink.chiron.wire;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.buffer.BytebufTools;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//@Ignore( "Work in progress" )
public class WireTest {

  @Test
  public void positionalDemo() throws WireException {
    final ByteBuf byteBuf = Unpooled.buffer() ;
    final BytebufNodeWriter<WireFixture.MyNodeToken, WireFixture.MyLeafToken> nodeWriter =
        new BytebufNodeWriter<>( byteBuf ) ;

    final WireFixture.Rake rake = new WireFixture.Rake(
        "Parent",
        ImmutableList.of(
            new WireFixture.Terminal( 1 ),
            new WireFixture.Terminal( 2 ),
            new WireFixture.Terminal( 3 )
        )
    ) ;

    LOGGER.info( "Created: " + rake ) ;

    WireFixture.writeRake( nodeWriter, rake ) ;
    // Also works:
    // nodeWriter.singleNode( WireFixture.MyNode.PARENT, myParent, WireFixture.Demo::writeParent ) ;

    LOGGER.info( "Wrote to wire: \n" + BytebufTools.fullDump( byteBuf ) ) ;

    final BytebufNodeReader<WireFixture.MyNodeToken, WireFixture.MyLeafToken> nodeReader =
        new BytebufNodeReader<>( byteBuf ) ;
    final WireFixture.Rake unwired = WireFixture.readRake( nodeReader ) ;

    LOGGER.info( "Did read from wire: " + unwired ) ;

    assertThat( unwired ).isEqualToComparingFieldByField( rake ) ;

  }

  @Test
  public void xmlDemoWithRake() throws WireException {
    final StringWriter stringWriter = new StringWriter() ;
    final XmlNodeWriter<WireFixture.MyNodeToken, WireFixture.MyLeafToken> nodeWriter =
        new XmlNodeWriter<>( stringWriter ) ;

    final WireFixture.Rake rake = new WireFixture.Rake(
        "Parent",
        ImmutableList.of(
            new WireFixture.Terminal( 1 ),
            new WireFixture.Terminal( 2 ),
            new WireFixture.Terminal( 3 )
        )
    ) ;

    LOGGER.info( "Created: " + rake ) ;

    nodeWriter.singleNode( WireFixture.MyNodeToken.RAKE, rake, WireFixture::writeRake ) ;

    final String xml = stringWriter.toString() ;
    LOGGER.info( "Wrote XML: \n" + xml ) ;

    final XmlNodeReader<WireFixture.MyNodeToken, WireFixture.MyLeafToken> nodeReader =
        newXmlNodeReader( xml ) ;

    final WireFixture.Rake unwired = nodeReader.singleNode(
        WireFixture.MyNodeToken.RAKE, WireFixture::readRake ) ;

    LOGGER.info( "Did read from wire: " + unwired ) ;
    assertThat( unwired ).isEqualToComparingFieldByField( rake ) ;

  }

  @Test
  public void deepTree() throws WireException {
    serializeAndDeserialize( new WireFixture.Tree(
        "Parent",
        ImmutableList.of(
            new WireFixture.Branch( 1, ImmutableList.of(
                new WireFixture.Branch( 11, ImmutableList.of() ),
                new WireFixture.Branch( 12, ImmutableList.of() )
            ) ),
            new WireFixture.Branch( 2, ImmutableList.of() )
        )
    ) ) ;
  }

  @Test
  public void deepTree2() throws WireException {
    serializeAndDeserialize( new WireFixture.Tree(
        "Parent",
        ImmutableList.of(
            new WireFixture.Branch( 1, ImmutableList.of(
                new WireFixture.Branch( 11, ImmutableList.of() )
            ) ),
            new WireFixture.Branch( 2, ImmutableList.of() )
        )
    ) ) ;
  }

  @Test
  public void deepTree3() throws WireException {
    serializeAndDeserialize( new WireFixture.Tree(
        "Parent",
        ImmutableList.of(
            new WireFixture.Branch( 1, ImmutableList.of(
                new WireFixture.Branch( 11, ImmutableList.of() )
            ) ),
            new WireFixture.Branch( 2, ImmutableList.of(
                new WireFixture.Branch( 21, ImmutableList.of() )
            ) )
        )
    ) ) ;
  }



  @Test
  public void parseBadXml() {
    assertThatThrownBy( () ->
        newXmlNodeReader(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<parent string-1='Booom"
        ).singleNode(
            WireFixture.MyNodeToken.RAKE,
            NULL_NODE_READER
        )
    ).isInstanceOf( WireException.class ) ;
  }

  @Test
  public void unexpectedXmlAttribute() {
    assertThatThrownBy( () ->
        newXmlNodeReader(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<parent string-1='111' integer-1='Does not belong to parent' > \n" +
            "</parent>"
        ).singleNode(
            WireFixture.MyNodeToken.RAKE,
            NULL_NODE_READER
        )
    )   .satisfies( t -> LOGGER.info( "This is the exception: ", t ) )
        .isInstanceOf( WireException.class ) ;
  }

  @Test
  public void unknownXmlAttribute() {
    assertThatThrownBy( () ->
        newXmlNodeReader(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<parent string-1='111' bad='Totally unknown' > \n" +
            "</parent>"
        ).singleNode(
            WireFixture.MyNodeToken.RAKE,
            NULL_NODE_READER
        )
    )   .satisfies( t -> LOGGER.info( "This is the exception: ", t ) )
        .isInstanceOf( WireException.class ) ;
  }

  @Test
  public void unexpectedXmlChild() {
    assertThatThrownBy( () ->
        newXmlNodeReader(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<parent string-1='Ssss' > \n" +
            "  <bad-child/> \n" +
            "</parent>"
        ).singleNode(
            WireFixture.MyNodeToken.RAKE,
            nodeReader -> {
              nodeReader.leaf( WireFixture.MyLeafToken.S ) ;
              nodeReader.singleNode( WireFixture.MyNodeToken.TERMINAL, nodeReader1 -> {
                // Force going deeper.
                return null ;
              } ) ;
              return null ;
            }
        )
    )   .satisfies( t -> LOGGER.info( "This is the exception: ", t ) )
        .isInstanceOf( WireException.class )
    ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( WireTest.class ) ;

  private static XmlNodeReader<WireFixture.MyNodeToken, WireFixture.MyLeafToken> newXmlNodeReader(
      final String xml
  ) {
    return new XmlNodeReader<>(
        newXmlStreamReader( xml ),
        WireFixture.MyNodeToken.MAP.values(),
        WireFixture.MyLeafToken.MAP.values()
    ) ;
  }

  public static XMLStreamReader newXmlStreamReader( final String xml ) {
    final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance() ;
    try {
      return xmlInputFactory.createXMLStreamReader( new StringReader( xml ) ) ;
    } catch( XMLStreamException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  private static final Wire.NodeReader.ReadingAction<
      WireFixture.MyNodeToken,
      WireFixture.MyLeafToken,
        WireFixture.Rake
    > NULL_NODE_READER = nodeReader -> null ;


  private static String toXml( final WireFixture.Tree tree ) throws WireException {
    return toXml( tree, WireFixture::writeTree ) ;
  }

  private static String toXml(
      final WireFixture.Tree tree,
      final Wire.NodeWriter.WritingAction<
          WireFixture.MyNodeToken,
          WireFixture.MyLeafToken,
                WireFixture.Tree
            > writeTree
  ) throws WireException {
    final StringWriter stringWriter = new StringWriter() ;
    final XmlNodeWriter<WireFixture.MyNodeToken, WireFixture.MyLeafToken> nodeWriter =
        new XmlNodeWriter<>( stringWriter ) ;
    nodeWriter.singleNode( WireFixture.MyNodeToken.TREE, tree, writeTree ) ;
    return stringWriter.toString() ;
  }

  private static void serializeAndDeserialize( WireFixture.Tree tree ) throws WireException {
    LOGGER.info( "Created: " + tree ) ;

    final String xml = toXml( tree ) ;
    LOGGER.info( "Wrote XML: \n" + xml ) ;

    final XmlNodeReader<WireFixture.MyNodeToken, WireFixture.MyLeafToken> nodeReader =
        newXmlNodeReader( xml ) ;

    final WireFixture.Tree unwired = nodeReader.singleNode(
        WireFixture.MyNodeToken.TREE, WireFixture::readTree ) ;

    final String xml2 = toXml( unwired ) ;
    LOGGER.info( "Rewrote XML: \n" + xml2 ) ;

    LOGGER.info( "Did read from wire: " + unwired ) ;
    assertThat( unwired ).isEqualToComparingFieldByField( tree ) ;
  }


}
