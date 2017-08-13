package com.otcdlink.chiron.upend.tier;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.ChannelTools;
import com.otcdlink.chiron.middle.tier.AbstractCommandWebsocketCodecTier;
import io.netty.channel.ChannelHandlerContext;

import static com.google.common.base.Preconditions.checkNotNull;

public class CommandWebsocketCodecUpendTier< UPWARD_DUTY, DOWNWARD_DUTY >
    extends AbstractCommandWebsocketCodecTier<
        Designator,
            UPWARD_DUTY,
            Designator,
            DOWNWARD_DUTY
        >
{
  private final Designator.Factory designatorFactory ;

  public CommandWebsocketCodecUpendTier(
      final CommandBodyDecoder< Designator, UPWARD_DUTY > commandDecoder,
      final Designator.Factory designatorFactory
  ) {
    super( commandDecoder ) ;
    this.designatorFactory = checkNotNull( designatorFactory ) ;
  }


  @Override
  protected Designator readEndpointSpecific(
      final ChannelHandlerContext channelHandlerContext,
      final PositionalFieldReader fieldReader
  )
      throws DecodeException
  {
    final Command.Tag tag = new Command.Tag( fieldReader.readDelimitedString() ) ;
    final Designator designatorUpward = designatorFactory.upward(
        tag, ChannelTools.sessionIdentifier( channelHandlerContext ) ) ;
    return designatorUpward ;
  }

  @Override
  protected void writeEndpointSpecific(
      final ChannelHandlerContext channelHandlerContext,
      final PositionalFieldWriter fieldWriter,
      final Designator downward
  ) {
    fieldWriter.writeNullableString(  Command.Tag.stringOrNull( downward.tag ) ) ;
  }

}
