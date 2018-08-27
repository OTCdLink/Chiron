package com.otcdlink.chiron.wire;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.BytebufCoat;
import com.otcdlink.chiron.buffer.BytebufTools;
import com.otcdlink.chiron.codec.DecodeException;
import io.netty.buffer.ByteBuf;

import java.util.stream.Collector;

public class BytebufNodeReader<
    NODE extends Wire.NodeToken< NODE, LEAF >,
    LEAF extends Wire.LeafToken
> implements Wire.NodeReader< NODE, LEAF > {

  private ByteBuf byteBuffer ;

  public BytebufNodeReader( final ByteBuf byteBuffer ) {
    this.byteBuffer = byteBuffer ;
  }

  private final Wire.WireReader wireReader = new Wire.WireReader() {
    @Override
    public int readIntegerPrimitive() {
      return byteBuffer.readInt() ;
    }

    @Override
    public Integer readIntegerObject() {
      return byteBuffer.readInt() ; // FIXME
    }

    @Override
    public String readDelimitedString() throws WireException {
      final int markerIndex = byteBuffer.forEachByte( BytebufTools.FIND_FIELD_END_MARKER ) ;
      if( markerIndex < 0 ) {
        throw wireExceptionGenerator.throwWireException(
            "Missing field end marker at the end of " + byteBuffer ) ;
      } else {
        try {
          return BytebufCoat.urlDecodeUtf8( byteBuffer, markerIndex ) ;
        } catch( DecodeException e ) {
          throw wireExceptionGenerator.throwWireException( e ) ;
        }
      }

    }
  } ;

  @Override
  public <
      NODE2 extends Wire.NodeToken< NODE2, LEAF2 >,
      LEAF2 extends Wire.LeafToken
  > XmlNodeReader< NODE2, LEAF2 > redefineWith(
      ImmutableMap< String, NODE2 > nodes,
      ImmutableMap< String, LEAF2 > leaves
  ) {
    throw new UnsupportedOperationException( "TODO" );
  }

  @Override
  public < ITEM_LEAF extends Wire.LeafToken< ITEM >, ITEM > ITEM leaf(
      final ITEM_LEAF leafWithTypedItem
  ) throws WireException {
    return leafWithTypedItem.fromWire( wireReader ) ;
  }

  @Override
  public < ITEM > ITEM singleNode(
      final NODE node,
      final ReadingAction< NODE, LEAF, ITEM > readingAction
  ) throws WireException {
    return readingAction.read( this ) ;
  }

  @Override
  public < ITEM > void nodeSequence(
      final NODE node,
      final ReadingAction< NODE, LEAF, ITEM > readingAction
  ) throws WireException {
    final int count = wireReader.readIntegerPrimitive() ;
    for( int i = 0 ; i < count ; i ++ ) {
      readingAction.read( this ) ;
    }
  }

  @Override
  public < ITEM, COLLECTION > COLLECTION nodeSequence(
      final NODE node,
      final Collector< ITEM, ?, COLLECTION > collector,
      final ReadingAction< NODE, LEAF, ITEM > readingAction
  ) throws WireException {
    throw new UnsupportedOperationException( "TODO" );
  }

  private Wire.Location location() {
    return new Wire.Location( null, -1, -1, byteBuffer.readableBytes(), null ) ;
  }

  private final WireException.Generator wireExceptionGenerator =
      new WireException.Generator( this::location ) ;

}
