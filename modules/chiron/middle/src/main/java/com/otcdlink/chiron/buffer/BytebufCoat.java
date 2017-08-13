package com.otcdlink.chiron.buffer;

import com.google.common.base.Charsets;
import com.google.common.net.PercentEscaper;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.toolbox.clock.Clock;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.LocalDate;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.Charset;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Semantic wrapper around {@link ByteBuf}.
 * TODO: move in some implementation-related package.
 *
 * @see BytebufTools#threadLocalRecyclableCoating() for reusing the same instance (this is the sole mean
 *     to get a {@link BytebufCoat} by the way.
 *
 * @see io.netty.buffer.ByteBufInputStream which does a similar thing around {@code DataInput}.
 */
public class BytebufCoat implements PositionalFieldReader, PositionalFieldWriter {
  private static final Charset CHARSET = Charsets.UTF_8;

// =======
// Coating
// =======


  private ByteBuf coated ;

  BytebufCoat() {
    this( null ) ;
  }

  BytebufCoat( final ByteBuf coated ) {
    this.coated = coated ;
  }

  void recycle() {
    checkState( coated != null, "Uninitialized or already recycled" ) ;
    coated = null ;
  }

  void coat( final ByteBuf byteBuf ) {
    checkState( coated == null, "Currently in use, or forgot to recycle" ) ;
    coated = checkNotNull( byteBuf ) ;
  }

// =========
// Utilities
// =========

  /**
   * Don't encode some characters for aesthetic reasons.
   */
  private static final PercentEscaper ESCAPER = new PercentEscaper( "._$-", true ) ;

  /**
   * TODO: skip {@code String} instantiation and spit directly to the {@link ByteBuf}.
   */
  private static String urlEncodeUtf8( final String string ) {
    return ESCAPER.escape( string ) ;
  }

  private static String urlDecodeUtf8( final ByteBuf byteBuf, final int lastIndex )
      throws DecodeException
  {
    final byte[] bytes = new byte[ lastIndex - byteBuf.readerIndex() ] ;
    byteBuf.readBytes( bytes ) ;
    byteBuf.skipBytes( 1 ) ;
    final String decodable ;
    try {
      decodable = new String( bytes, CHARSET ) ;
    } catch( final Exception e ) {
      throw new DecodeException( "Could not create " + CHARSET + " string", e, byteBuf ) ;
    }

    try {
      return URLDecoder.decode( decodable, CHARSET.name() ) ;
    } catch( final UnsupportedEncodingException e ) {
      throw new DecodeException( "Could not decode '" + decodable + "'", e ) ;
    }
  }

  public String prettyHexDump() {
    return
        ByteBufUtil.prettyHexDump( coated ) + "\n" +
        "The dump above shows readable bytes (readerIndex <= shown < writerIndex).\n" +
        "readerIndex: " + coated.readerIndex() + "    " +
        "writerIndex: " + coated.writerIndex() + "\n"
    ;
  }

// =====================
// PositionalFieldReader
// =====================

  /**
   * Consumes the {@link BytebufTools#NULLITY_MARKER} if there is one, leaves
   * {@link ByteBuf#readerIndex()} unchanged otherwise.
   */
  @Override
  public boolean readNullityMarker() throws DecodeException {
    try {
      final int readerIndex = coated.readerIndex() ;
      if( coated.getByte( readerIndex ) == BytebufTools.NULLITY_MARKER ) {
        if( coated.readableBytes() >= 2 &&
            coated.getByte( readerIndex + 1 ) == BytebufTools.FIELD_END_MARKER
        ) {
          coated.skipBytes( 2 ) ;
          return true ;
        } else {
          throw new DecodeException(
              "Nullity marker at index " + readerIndex + " should be followed by " +
                  "field end marker",
              coated
          ) ;
        }
      }
    } catch( final DecodeException rethrown ) {
      throw rethrown ;
    } catch( final Exception e ) {
      throw new DecodeException( "Something went wrong", e, coated ) ;
    }
    return false ;
  }

  @Override
  public void readExistenceMarker() throws DecodeException {
    final byte separator = coated.readByte() ;
    if( separator != BytebufTools.EXISTENCE_MARKER ) {
      throw new DecodeException( "Missing existence marker '" +
          BytebufTools.EXISTENCE_MARKER + "'" ) ;
    }

  }

  @Override
  public void writeExistenceMark() {
    coated.writeByte( BytebufTools.EXISTENCE_MARKER ) ;
  }

  @Override
  public String readDelimitedString() throws DecodeException {

    final int markerIndex = coated.forEachByte( BytebufTools.FIND_FIELD_END_MARKER ) ;
    if( markerIndex < 0 ) {
      throw new DecodeException( "Missing field end marker at the end", coated ) ;
    } else {
      return urlDecodeUtf8( coated, markerIndex ) ;
    }
  }

  @Override
  public String readNullableString() throws DecodeException {
    if( readNullityMarker() ) {
      return null ;
    } else {
      return readDelimitedString() ;
    }
  }

  @Override
  public int readableBytes() {
    return coated.readableBytes() ;
  }

  @Override
  public byte getByte( final int index ) throws DecodeException {
    try {
      return coated.getByte( index ) ;
    } catch( final Exception e ) {
      throw new DecodeException( "Incorrect index: " + index, e, coated ) ;
    }
  }

  @Override
  public void skipBytes( final int length ) throws DecodeException {
    try {
      coated.skipBytes( length ) ;
    } catch( final Exception e ) {
      throw new DecodeException( "Incorrect length: " + length, e, coated ) ;
    }
  }



// =====================
// PositionalFieldWriter
// =====================

  @Override
  public void writeDelimitedString( final String nonNullString ) {
    writeAsciiUnsafe( urlEncodeUtf8( nonNullString ) ) ;
    coated.writeByte( BytebufTools.FIELD_END_MARKER ) ;
  }

  @Override
  public void writeNullableString( final String string ) {
    if( ! writeNullityMarkerMaybe( string == null ) ) {
      writeDelimitedString( string ) ;
    }
  }

  @Override
  public void writeIntegerPrimitive( final int integerPrimitive ) {
    writeDelimitedString( Integer.toString( integerPrimitive ) ) ;
  }

  @Override
  public void writeIntegerObject( final Integer integerObject ) {
    writeNullableString( integerObject == null ? null : integerObject.toString() ) ;
  }

  @Override
  public void writeLongPrimitive( final long longPrimitive ) {
    writeDelimitedString( Long.toString( longPrimitive ) ) ;
  }

  @Override
  public void writeLongObject( final Long longObject ) {
    writeNullableString( longObject == null ? null : longObject.toString() ) ;
  }

  @Override
  public void writeFloatPrimitive( final float floatPrimitive ) {
    writeDelimitedString( Float.toString( floatPrimitive ) ) ;
  }

  @Override
  public void writeFloatObject( final Float floatObject ) {
    writeNullableString( floatObject == null ? null : floatObject.toString() ) ;
  }

  @Override
  public void writeBooleanPrimitive( final boolean booleanPrimitive ) {
    writeBooleanObject( booleanPrimitive ) ;
  }

  @Override
  public void writeBooleanObject( final Boolean booleanObject ) {
    if( ! writeNullityMarkerMaybe( booleanObject == null ) ) {
      writeDelimitedString( booleanObject ?
          BytebufTools.BOOLEAN_TRUE : BytebufTools.BOOLEAN_FALSE ) ;
    }
  }

  @Override
  public void writeBigDecimal( final BigDecimal bigDecimal ) {
    writeNullableString( bigDecimal == null ? null : bigDecimal.toPlainString() ) ;
  }

  @Override
  public void writeDateTime( final DateTime dateTime ) {
    checkArgument( dateTime == null ||
        dateTime.getZone().equals( DateTimeZone.UTC ), "" + dateTime ) ;
    writeLongObject( dateTime == null ? null : dateTime.getMillis() ) ;
  }

  @Override
  public void writeAsciiUnsafe( final CharSequence charSequence ) {
    ByteBufUtil.writeAscii( coated, charSequence ) ;
  }


// =====================
// PositionalFieldWriter
// =====================

  @Override
  public boolean writeNullityMarkerMaybe( final boolean writeIt ) {
    if( writeIt ) {
      coated.writeByte( BytebufTools.NULLITY_MARKER ) ;
      coated.writeByte( BytebufTools.FIELD_END_MARKER ) ;
    }
    return writeIt ;
  }

  @Override
  public int readIntegerPrimitive() throws DecodeException {
    final String integerAsString = readDelimitedString() ;
    try {
      return Integer.parseInt( integerAsString ) ;
    } catch( final NumberFormatException e ) {
      throw new DecodeException( "Not parseable as an integer: '" + integerAsString + "'", e ) ;
    }
  }

  @Override
  public Integer readIntegerObject() throws DecodeException {
    final String integerAsString = readNullableString() ;
    try {
      return integerAsString == null ? null : Integer.parseInt( integerAsString ) ;
    } catch( final NumberFormatException e ) {
      throw new DecodeException( "Not parseable as an integer: '" + integerAsString + "'", e ) ;
    }
  }

  @Override
  public long readLongPrimitive() throws DecodeException {
    final String longAsString = readDelimitedString() ;
    try {
      return Long.parseLong( longAsString ) ;
    } catch( final NumberFormatException e ) {
      throw new DecodeException( "Not parseable as an long: '" + longAsString + "'", e ) ;
    }
  }

  @Override
  public Long readLongObject() throws DecodeException {
    final String longAsString = readNullableString() ;
    try {
      return longAsString == null ? null : Long.parseLong( longAsString ) ;
    } catch( final NumberFormatException e ) {
      throw new DecodeException( "Not parseable as an long: '" + longAsString + "'", e ) ;
    }
  }

  @Override
  public float readFloatPrimitive() throws DecodeException {
    final String floatAsString = readDelimitedString() ;
    try {
      return Float.parseFloat( floatAsString ) ;
    } catch( final NumberFormatException e ) {
      throw new DecodeException( "Not parseable as an float: '" + floatAsString + "'", e ) ;
    }
  }

  @Override
  public Float readFloatObject() throws DecodeException {
    final String floatAsString = readNullableString() ;
    try {
      return floatAsString == null ? null : Float.parseFloat( floatAsString ) ;
    } catch( final NumberFormatException e ) {
      throw new DecodeException( "Not parseable as an float: '" + floatAsString + "'", e ) ;
    }
  }

  @Override
  public boolean readBooleanPrimitive() throws DecodeException {
    final String booleanAsString = readDelimitedString() ;
    if( BytebufTools.BOOLEAN_TRUE.equals( booleanAsString ) ) {
      return true ;
    } else if( BytebufTools.BOOLEAN_FALSE.equals( booleanAsString ) ) {
      return false ;
    } else {
      throw new DecodeException(
          "Unsupported value when expecting a boolean: '" + booleanAsString + "'" ) ;
    }
  }

  @Override
  public Boolean readBooleanObject() throws DecodeException {
    if( readNullityMarker() ) {
      return null ;
    } else {
      return readBooleanPrimitive() ;
    }
  }

  @Override
  public BigDecimal readBigDecimal() throws DecodeException {
    final String bigDecimalAsString = readNullableString() ;
    try {
      return bigDecimalAsString == null ? null : new BigDecimal( bigDecimalAsString ) ;
    } catch( final NumberFormatException e ) {
      throw new DecodeException(
          "Not parseable as a BigDecimal: '" + bigDecimalAsString + "'", e ) ;
    }
  }

  @Override
  public DateTime readDateTime() throws DecodeException {
    final String millisecondsAsString = readNullableString() ;
    if( millisecondsAsString == null ) {
      return null ;
    } else {
      try {
        final long milliseconds = Long.parseLong( millisecondsAsString ) ;
        return milliseconds <= 0 ? Clock.ZERO : new DateTime( milliseconds, DateTimeZone.UTC ) ;
      } catch( NumberFormatException e ) {
        throw new DecodeException( "Could not read " + DateTime.class.getSimpleName() +
            " from '" + millisecondsAsString + "'", e ) ;
      }
    }
  }

  @Override
  public LocalDate readLocalDate() throws DecodeException {
    final String localDateAsString = readNullableString() ;
    if( localDateAsString == null ) {
      return null ;
    } else {
      try {
        return BytebufTools.LOCALDATE_FORMATTER.parseLocalDate( localDateAsString ) ;
      } catch( final Exception e ) {
        throw new DecodeException( "Can't parse '" + localDateAsString + "'", e ) ;
      }
    }
  }

  @Override
  public Duration readDuration() throws DecodeException {
    final Long longObject = readLongObject() ;
    if( longObject == null ) {
      return null ;
    } else {
      return new Duration( longObject ) ;
    }
  }

  @Override
  public void writeLocalDate( final LocalDate localDate ) {
    writeNullableString(
        localDate == null
            ? null
            : BytebufTools.LOCALDATE_FORMATTER.print( localDate )
    ) ;
  }

  @Override
  public void writeDuration( final Duration duration ) {
    writeLongObject(
        duration == null
            ? null
            : duration.getMillis()
    ) ;
  }
}
