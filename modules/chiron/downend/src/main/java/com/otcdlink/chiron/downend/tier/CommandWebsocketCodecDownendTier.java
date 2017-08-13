package com.otcdlink.chiron.downend.tier;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.command.codec.Codec;
import com.otcdlink.chiron.middle.tier.AbstractCommandWebsocketCodecTier;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public class CommandWebsocketCodecDownendTier< ENDPOINT_SPECIFIC, UPWARD_DUTY, DOWNWARD_DUTY >
    extends AbstractCommandWebsocketCodecTier<
        ENDPOINT_SPECIFIC,
        UPWARD_DUTY,
        ENDPOINT_SPECIFIC,
        DOWNWARD_DUTY
    >
{

  private final Codec< ENDPOINT_SPECIFIC > endpointSpecificCodec ;

  public CommandWebsocketCodecDownendTier(
      final Codec< ENDPOINT_SPECIFIC > endpointSpecificCodec,
      final CommandBodyDecoder< ENDPOINT_SPECIFIC, UPWARD_DUTY > commandDecoder
  ) {
    super( commandDecoder ) ;
    this.endpointSpecificCodec = checkNotNull( endpointSpecificCodec ) ;
  }


  @Override
  protected ENDPOINT_SPECIFIC readEndpointSpecific(
      final ChannelHandlerContext channelHandlerContext,
      final PositionalFieldReader fieldReader
  ) throws IOException {
    final ENDPOINT_SPECIFIC endpointSpecific = endpointSpecificCodec.decodeFrom( fieldReader ) ;
    return endpointSpecific ;
  }

  @Override
  protected void writeEndpointSpecific(
      final ChannelHandlerContext channelHandlerContext,
      final PositionalFieldWriter fieldWriter,
      final ENDPOINT_SPECIFIC endpointSpecific
  ) throws IOException {
    endpointSpecificCodec.encodeTo( endpointSpecific, fieldWriter ) ;
  }

}
