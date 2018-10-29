package com.otcdlink.chiron.wire;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.BytebufCoat;
import com.otcdlink.chiron.buffer.BytebufTools;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.codec.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class NodeEncoder<
    NODE extends Wire.NodeToken< NODE, LEAF >,
    LEAF extends Wire.LeafToken,
    OBJECT
>
    extends PositionalNodeWriter< NODE, LEAF >
    implements Encoder< OBJECT >
{

  private final NODE rootNode ;
  private final Wire.NodeWriter.WritingAction< NODE, LEAF, OBJECT > writingAction ;

  public NodeEncoder(
      final ImmutableMap< String, NODE > nodeTokens,
      final ImmutableMap< String, LEAF > leafTokens,
      final NODE rootNode,
      final Wire.NodeWriter.WritingAction< NODE, LEAF, OBJECT > writingAction
  ) {
    super( nodeTokens, leafTokens ) ;
    this.rootNode = checkNotNull( rootNode ) ;
    this.writingAction = checkNotNull( writingAction ) ;
  }

  @Override
  public void encodeTo(
      final OBJECT object,
      final PositionalFieldWriter positionalFieldWriter
  ) throws IOException {
    checkState( crudeWriter == null ) ;
    try {
      crudeWriter = positionalFieldWriter ;
      singleNode( rootNode, object, writingAction ) ;
    } catch( WireException e ) {
      throw new DecodeException( "Should unwrap in some way", e ) ;
    } finally {
      crudeWriter = null ;
    }
  }

  /**
   * Only for rare cases or tests.
   */
  public ByteBuf encodeToUnpooled( final OBJECT object ) throws IOException {
    final ByteBuf byteBuf = Unpooled.buffer() ;
    final BytebufCoat coat = BytebufTools.threadLocalRecyclableCoating().coat( byteBuf ) ;
    encodeTo( object, coat ) ;
    return byteBuf ;
  }

}
