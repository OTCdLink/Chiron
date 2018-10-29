package com.otcdlink.chiron.wire;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.CrudeReader;
import com.otcdlink.chiron.codec.DecodeException;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collector;

import static com.google.common.base.Preconditions.checkNotNull;

public class PositionalNodeReader<
    NODE extends Wire.NodeToken< NODE, LEAF >,
    LEAF extends Wire.LeafToken
> implements Wire.NodeReader< NODE, LEAF > {

  protected final ImmutableMap< String, NODE > nodeTokens ;
  protected final ImmutableMap< String, LEAF > leafTokens ;
  protected CrudeReader crudeReader = null ;

  public PositionalNodeReader(
      final ImmutableMap< String, NODE > nodeTokens,
      final ImmutableMap< String, LEAF > leafTokens
  ) {
    this.nodeTokens = checkNotNull( nodeTokens ) ;
    this.leafTokens = checkNotNull( leafTokens ) ;
  }

  protected PositionalNodeReader(
      final ImmutableMap< String, NODE > nodeTokens,
      final ImmutableMap< String, LEAF > leafTokens,
      final PositionalNodeReader delegating
  ) {
    this.nodeTokens = checkNotNull( nodeTokens ) ;
    this.leafTokens = checkNotNull( leafTokens ) ;
    this.crudeReader = delegating.crudeReader ;
  }

  @Override
  public final < ITEM_LEAF extends Wire.LeafToken< ITEM >, ITEM > ITEM leaf(
      final ITEM_LEAF leafWithTypedItem
  ) throws WireException {
    try {
      return leafWithTypedItem.fromWire( crudeReader ) ;
    } catch( DecodeException e ) {
      throw wireExceptionGenerator.throwWireException( e ) ;
    }
  }

  @Override
  public final < ITEM > ITEM singleNode(
      final NODE node,
      final ReadingAction< NODE, LEAF, ITEM > readingAction
  ) throws WireException {
    return readingAction.read( this ) ;
  }

  @Override
  public final < ITEM > void nodeSequence(
      final NODE node,
      final ReadingAction< NODE, LEAF, ITEM > readingAction
  ) throws WireException {
    final int count;
    try {
      count = crudeReader.readIntegerPrimitive() ;
    } catch( final Exception e ) {
      throw new WireException( "Failed to read " + node, e, new Wire.Location( "", 0, 0, 0, "" ) ) ;
    }
    for( int i = 0 ; i < count ; i ++ ) {
      readingAction.read( this ) ;
    }
  }

  @Override
  public final < ITEM, COLLECTION > COLLECTION nodeSequence(
      final NODE node,
      final Collector< ITEM, ?, COLLECTION > collector,
      final ReadingAction< NODE, LEAF, ITEM > readingAction
  ) throws WireException {
    final Object container = collector.supplier().get() ;
    final BiConsumer< Object, ITEM > accumulator =
        ( BiConsumer< Object, ITEM > ) collector.accumulator() ;
    final Function< ?, COLLECTION > finisher = collector.finisher() ;

    final int count ;
    try {
      count = crudeReader.readIntegerPrimitive() ;
    } catch( final Exception e ) {
      throw new WireException( "Failed to read " + node, e, new Wire.Location( "", 0, 0, 0, "" ) ) ;
    }
    for( int i = 0 ; i < count ; i ++ ) {
      final ITEM item = readingAction.read( this ) ;
      accumulator.accept( container, item ) ;
    }
    final COLLECTION collection = ( COLLECTION ) ( ( Function ) finisher ).apply( container ) ;
    return collection ;
  }

  private Wire.Location location() {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  protected final WireException.Generator wireExceptionGenerator =
      new WireException.Generator( this::location ) ;

  @Override
  public <
      NODE2 extends Wire.NodeToken< NODE2, LEAF2 >,
      LEAF2 extends Wire.LeafToken
  > Wire.NodeReader< NODE2, LEAF2 > redefineWith(
      ImmutableMap< String, NODE2 > nodes,
      ImmutableMap< String, LEAF2 > leaves
  ) {
    return new PositionalNodeReader<>( nodes, leaves, this ) ;
  }


}
