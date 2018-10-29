package com.otcdlink.chiron.wire;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.CrudeWriter;

import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PositionalNodeWriter<
    NODE extends Wire.NodeToken< NODE, LEAF >,
    LEAF extends Wire.LeafToken
> implements Wire.NodeWriter< NODE, LEAF > {


  protected final ImmutableMap< String, NODE > nodeTokens ;
  protected final ImmutableMap< String, LEAF > leafTokens ;
  protected CrudeWriter crudeWriter = null ;

  protected PositionalNodeWriter(
      final ImmutableMap< String, NODE > nodeTokens,
      final ImmutableMap< String, LEAF > leafTokens
  ) {
    this.nodeTokens = checkNotNull( nodeTokens ) ;
    this.leafTokens = checkNotNull( leafTokens ) ;
  }

  protected PositionalNodeWriter(
      final ImmutableMap< String, NODE > nodeTokens,
      final ImmutableMap< String, LEAF > leafTokens,
      final PositionalNodeWriter delegating
  ) {
    this.nodeTokens = checkNotNull( nodeTokens ) ;
    this.leafTokens = checkNotNull( leafTokens ) ;
    this.crudeWriter = delegating.crudeWriter ;
  }

  @Override
  public final < ITEM_LEAF extends Wire.LeafToken< ITEM >, ITEM > void leaf(
      final ITEM_LEAF leafForItem,
      final ITEM item
  ) throws WireException {
    leafForItem.toWire( item, crudeWriter ) ;
  }

  @Override
  public final < ITEM > void singleNode(
      final NODE node,
      final ITEM item,
      final WritingAction< NODE, LEAF, ITEM > writingAction
  ) throws WireException {
    writingAction.write( this, item ) ;
  }

  @Override
  public final < ITEM > void nodeSequence(
      final NODE node,
      final int count,
      final Iterable< ITEM > items,
      final WritingAction< NODE, LEAF, ITEM > writingAction
  ) throws WireException {
    checkArgument( count >= 0 ) ;
    final Iterator< ITEM > iterator = items.iterator() ;
    crudeWriter.writeIntegerPrimitive( count ) ;
    for( int i = 0 ; i < count ; i ++ ) {
      final ITEM next = iterator.next() ;
      writingAction.write( this, next ) ;
    }
  }

  /**
   * We don't need to create a subclass because the only features needed are
   * {@link #leaf(Wire.LeafToken, Object)},
   * {@link #singleNode(Wire.NodeToken, Object, WritingAction)},
   * {@link #nodeSequence(Wire.NodeToken, int, Iterable, WritingAction)}.
   */
  @Override
  public final <
      NODE2 extends Wire.NodeToken < NODE2, LEAF2 >,
      LEAF2 extends Wire.LeafToken
  > Wire.NodeWriter< NODE2, LEAF2 > redefineWith(
      final ImmutableMap< String, NODE2 > newNodeTokens,
      final ImmutableMap< String, LEAF2 > newLeafTokens
  ) {
    return new PositionalNodeWriter<>(
        newNodeTokens,
        newLeafTokens,
        this
    ) ;
  }

}
