package com.otcdlink.chiron.wire;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.CrudeReader;
import com.otcdlink.chiron.codec.DecodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamReader;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractXmlNodeReader<
    NODE extends Wire.NodeToken< NODE, LEAF >,
    LEAF extends Wire.LeafToken
> implements Wire.NodeReader< NODE, LEAF > {

  private static final Logger LOGGER = LoggerFactory.getLogger( AbstractXmlNodeReader.class ) ;

  protected final XMLStreamReader xmlStreamReader ;
  protected final ImmutableMap< String, NODE > nodeNames ;
  protected final ImmutableMap< String, LEAF > leafNames ;

  public AbstractXmlNodeReader(
      final XMLStreamReader xmlStreamReader,
      final ImmutableCollection< NODE > nodes,
      final ImmutableCollection< LEAF > leaves
  ) {
    this(
        xmlStreamReader,
        Wire.Token.rekeyWithXmlNames( nodes ),
        Wire.Token.rekeyWithXmlNames( leaves )
    ) ;
  }

  public AbstractXmlNodeReader(
      final XMLStreamReader xmlStreamReader,
      final ImmutableMap< String, NODE > nodeNames,
      final ImmutableMap< String, LEAF > leafNames
  ) {
    this.xmlStreamReader = checkNotNull( xmlStreamReader ) ;
    this.nodeNames = checkNotNull( nodeNames ) ;
    this.leafNames = checkNotNull( leafNames ) ;
  }


  /**
   * To be filled with a {@link LEAF} value as {@code String} before using {@link #crudeReader}.
   */
  protected String attributeAsString = null ;

  protected final CrudeReader crudeReader = new CrudeReader() {
    @Override
    public int readIntegerPrimitive() {
      return Integer.parseInt( attributeAsString ) ;
    }

    @Override
    public Integer readIntegerObject() {
      return attributeAsString.isEmpty() ? null : readIntegerPrimitive() ;
    }

    @Override
    public String readDelimitedString() {
      return attributeAsString ;
    }

    @Override
    public String readNullableString() throws DecodeException {
      return attributeAsString ;
    }

    @Override
    public long readLongPrimitive() throws DecodeException {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public Long readLongObject() throws DecodeException {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public float readFloatPrimitive() throws DecodeException {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public Float readFloatObject() throws DecodeException {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public boolean readBooleanPrimitive() throws DecodeException {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public Boolean readBooleanObject() throws DecodeException {
      throw new UnsupportedOperationException( "TODO" );
    }
  } ;


// ========================
// XmlStreamReader piloting
// ========================



// ==========
// Exceptions
// ==========

  protected Wire.Location location() {
    final javax.xml.stream.Location location = xmlStreamReader.getLocation() ;
    return new Wire.Location(
        location.getSystemId(),
        location.getLineNumber(),
        location.getColumnNumber(),
        location.getCharacterOffset(),
        null
    ) ;
  }

  protected final WireException.Generator wireExceptionGenerator =
      new WireException.Generator( this::location ) ;

// =====
// Debug
// =====

  private static final boolean DEBUG = false ;

  protected void debug( final Supplier< String > lazy ) {
    if( DEBUG ) {
      LOGGER.debug( lazy.get() + " " + location() ) ;
    }
  }
}
