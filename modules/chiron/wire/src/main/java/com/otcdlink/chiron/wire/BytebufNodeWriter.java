package com.otcdlink.chiron.wire;

import com.otcdlink.chiron.buffer.BytebufTools;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;

public class BytebufNodeWriter<
    NODE extends Wire.NodeToken< NODE, LEAF >,
    LEAF extends Wire.LeafToken
> implements Wire.NodeWriter< NODE, LEAF > {

  public ByteBuf byteBuf ;

  private final Wire.WireWriter wireWriter = new Wire.WireWriter() {

    @Override
    public void writeIntegerPrimitive( int i ) {
      byteBuf.writeInt( i ) ;
    }

    @Override
    public void writeIntegerObject( Integer integer ) {
      byteBuf.writeInt( integer ) ;  // FIXME
    }

    @Override
    public void writeDelimitedString( String string ) {
      ByteBufUtil.writeUtf8( byteBuf, string ) ;
      byteBuf.writeByte( BytebufTools.FIELD_END_MARKER ) ;
    }
  } ;

  public BytebufNodeWriter( ByteBuf byteBuf ) {
    this.byteBuf = byteBuf ;
  }


  @Override
  public < ITEM_LEAF extends Wire.LeafToken< ITEM >, ITEM > void leaf(
      final ITEM_LEAF leafForItem,
      final ITEM item
  ) throws WireException {
    leafForItem.toWire( item, wireWriter ) ;
  }

  @Override
  public < ITEM > void singleNode(
      final NODE node,
      final ITEM item,
      final WritingAction< NODE, LEAF, ITEM > writingAction
  ) throws WireException {
    writingAction.write( this, item ) ;
  }

  @Override
  public < ITEM > void nodeSequence(
      final NODE node,
      final int count,
      final Iterable< ITEM > items,
      final WritingAction< NODE, LEAF, ITEM > writingAction
  ) throws WireException {
    checkArgument( count >= 0 ) ;
    final Iterator< ITEM > iterator = items.iterator() ;
    wireWriter.writeIntegerPrimitive( count ) ;
    for( int i = 0 ; i < count ; i ++ ) {
      final ITEM next = iterator.next() ;
      writingAction.write( this, next ) ;
    }
  }
}
