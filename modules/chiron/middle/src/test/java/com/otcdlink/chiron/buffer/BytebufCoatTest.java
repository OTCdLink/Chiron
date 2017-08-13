package com.otcdlink.chiron.buffer;

import com.otcdlink.chiron.codec.DecodeException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class BytebufCoatTest {
  
  @Test
  public void MixEscapingAndNullity() throws Exception {
    fieldWriter.writeDelimitedString( "Hello" ) ;
    assertThat( fieldWriter.writeNullityMarkerMaybe( true ) ).isTrue() ;
    fieldWriter.writeNullableString( "World" ) ;
    fieldWriter.writeNullableString( "" ) ;
    fieldWriter.writeNullableString( null ) ;
    fieldWriter.writeDelimitedString( "!" ) ;

    LOGGER.debug( "Coated buffer is:\n" + bytebufCoat.prettyHexDump() ) ;

    assertThat( fieldReader.readDelimitedString() ).isEqualTo( "Hello" ) ;
    assertThat( fieldReader.readNullityMarker() ).isTrue() ;
    assertThat( fieldReader.readNullableString() ).isEqualTo( "World" ) ;
    assertThat( fieldReader.readNullableString() ).isEqualTo( "" ) ;
    assertThat( fieldReader.readNullableString() ).isNull() ;
    assertThat( fieldReader.readDelimitedString() ).isEqualTo( "!" ) ;
  }

  @Test
  public void nonAsciiString() throws Exception {
    writeRead(
        fieldWriter::writeDelimitedString,
        fieldReader::readDelimitedString,
        "@#!§('%£áéíóőúű"
    ) ;

  }

  @Test
  public void integerPrimitive() throws Exception {
    writeRead(
        fieldWriter::writeIntegerPrimitive,
        fieldReader::readIntegerPrimitive,
        0, -1, 123, Integer.MIN_VALUE, Integer.MAX_VALUE
    ) ;
  }

  @Test
  public void integerObject() throws Exception {
    writeRead(
        fieldWriter::writeIntegerObject,
        fieldReader::readIntegerObject,
        0, -1, 123, Integer.MIN_VALUE, Integer.MAX_VALUE
    ) ;
  }

  @Test( expected = IllegalStateException.class )
  public void coatingState() throws Exception {
    coating.coat( byteBuf ) ;
    coating.coat( byteBuf ) ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( BytebufCoatTest.class ) ;

  private final BytebufTools.Coating coating = BytebufTools.threadLocalRecyclableCoating() ;
  private final ByteBuf byteBuf = Unpooled.buffer() ;
  private final BytebufCoat bytebufCoat = coating.coat( byteBuf ) ;
  private final PositionalFieldReader fieldReader = bytebufCoat ;
  private final PositionalFieldWriter fieldWriter = bytebufCoat ;

  @SafeVarargs
  private static< OBJECT > void writeRead(
      final QuietWriter< OBJECT > writer,
      final ReaderWithException< OBJECT > reader,
      final OBJECT... values
  ) throws DecodeException {
    for( final OBJECT value : values ) {
      writer.write( value ) ;
      final OBJECT read = reader.read() ;
      assertThat( read ).isEqualTo( value ) ;
    }
  }

  private interface QuietWriter< OBJECT > {
    void write( OBJECT object ) ;
  }
  
  private interface ReaderWithException< OBJECT > {
    OBJECT read() throws DecodeException ;
  }

}