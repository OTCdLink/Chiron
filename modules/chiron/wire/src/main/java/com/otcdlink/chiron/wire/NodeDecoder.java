package com.otcdlink.chiron.wire;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.BytebufCoat;
import com.otcdlink.chiron.buffer.BytebufTools;
import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.codec.Decoder;
import io.netty.buffer.ByteBuf;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class NodeDecoder<
    NODE extends Wire.NodeToken< NODE, LEAF >,
    LEAF extends Wire.LeafToken,
    OBJECT
>
    extends PositionalNodeReader< NODE, LEAF >
    implements Decoder< OBJECT >
{

  private final NODE rootNode ;
  private final ReadingAction< NODE, LEAF, OBJECT > readingAction ;

  public NodeDecoder(
      final ImmutableMap< String, NODE > nodeTokens,
      final ImmutableMap< String, LEAF > leafTokens,
      final NODE rootNode,
      final ReadingAction< NODE, LEAF, OBJECT > readingAction
  ) {
    super( nodeTokens, leafTokens ) ;
    this.rootNode = checkNotNull( rootNode ) ;
    this.readingAction = checkNotNull( readingAction ) ;
  }

  @Override
  public OBJECT decodeFrom( final PositionalFieldReader positionalFieldReader )
      throws DecodeException
  {
    checkState( crudeReader == null ) ;
    try {
      this.crudeReader = positionalFieldReader ;
      return singleNode( rootNode, readingAction ) ;
    } catch( WireException e ) {
      throw new DecodeException( "Too much wrapping", e ) ;
    } finally {
      this.crudeReader = null ;
    }
  }

  /**
   * Only for rare cases or tests.
   */
  public OBJECT decodeFrom( final ByteBuf byteBuf ) throws DecodeException {
    final BytebufCoat coat = BytebufTools.threadLocalRecyclableCoating().coat( byteBuf ) ;
    return decodeFrom( coat ) ;
  }


}
