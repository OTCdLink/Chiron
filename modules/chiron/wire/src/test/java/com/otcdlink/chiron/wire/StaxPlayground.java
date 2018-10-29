package com.otcdlink.chiron.wire;

import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.fail;

public class StaxPlayground {

  /**
   * Code sample for
   * https://github.com/FasterXML/aalto-xml/issues/64.
   */
  @Disabled( "Test case for an unresolved issue in Aalto-xml project" )
  @Test
  void entityReplacement() throws XMLStreamException {
    final XMLInputFactory xmlInputFactory =
//        new com.fasterxml.aalto.stax.InputFactoryImpl() ;
        javax.xml.stream.XMLInputFactory.newInstance() ;

    xmlInputFactory.setProperty( XMLInputFactory.SUPPORT_DTD, true ) ;
    xmlInputFactory.setProperty( XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false ) ;
    xmlInputFactory.setProperty( XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false ) ;
    xmlInputFactory.setProperty( XMLInputFactory.IS_COALESCING, false ) ;

    final String xml =
//        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<whatever x='&en;' />"
//        "<whatever x='&quot;' />"
//        "<whatever x='no-entity' />"
//        "<whatever>&replace-me;</whatever>"
    ;

    final XMLStreamReader xmlStreamReader =
        xmlInputFactory.createXMLStreamReader( new StringReader( xml ) ) ;

    lookup : {
      while( xmlStreamReader.hasNext() ) {
        final int staxEvent = xmlStreamReader.next() ;
        if( staxEvent == XMLEvent.ENTITY_REFERENCE ) {
          LOGGER.info( "Got entity reference '" + xmlStreamReader.getLocalName() + "'." ) ;
          break lookup ;
        }
      }
      fail( "Found no entity reference." ) ;
    }
  }


  @Test
  @Disabled( "Reproduction code of a (now fixed) issue: https://github.com/FasterXML/aalto-xml/issues/48" )
  public void asynchronousParseSmallestDocument() throws Exception {
    final String xml =
//        "<?xml version=\"1.0\" encoding=\"US-ASCII\"?>" +
        "<foo><bar/></foo>" ;

    final AsyncXMLInputFactory asyncXmlInputFactory = new InputFactoryImpl() ;
    final AsyncXMLStreamReader< AsyncByteArrayFeeder > xmlStreamReader =
        asyncXmlInputFactory.createAsyncForByteArray() ;
    final AsyncByteArrayFeeder inputFeeder = xmlStreamReader.getInputFeeder() ;

    byte[] xmlBytes = xml.getBytes() ;
    int bufferFeedLength = 1 ;
    int currentByteOffset = 0 ;
    int type ;
    do{
      while( ( type = xmlStreamReader.next() ) == AsyncXMLStreamReader.EVENT_INCOMPLETE ) {
        byte[] buffer = new byte[]{ xmlBytes[ currentByteOffset ] } ;
        currentByteOffset ++ ;
        inputFeeder.feedInput( buffer, 0, bufferFeedLength ) ;
        if( currentByteOffset >= xmlBytes.length ) {
          inputFeeder.endOfInput() ;
        }
      }
      switch( type ) {
        case XMLEvent.START_DOCUMENT :
          LOGGER.debug( "start document" ) ;
          break ;
        case XMLEvent.START_ELEMENT :
          LOGGER.debug( "start element: " + xmlStreamReader.getName() ) ;
          break ;
        case XMLEvent.CHARACTERS :
          LOGGER.debug( "characters: " + xmlStreamReader.getText() ) ;
          break ;
        case XMLEvent.END_ELEMENT :
          LOGGER.debug( "end element: " + xmlStreamReader.getName() ) ;
          break ;
        case XMLEvent.END_DOCUMENT :
          LOGGER.debug( "end document" ) ;
          break ;
        default :
          break ;
      }
    } while( type != XMLEvent.END_DOCUMENT ) ;

    xmlStreamReader.close() ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( StaxPlayground.class ) ;
}
