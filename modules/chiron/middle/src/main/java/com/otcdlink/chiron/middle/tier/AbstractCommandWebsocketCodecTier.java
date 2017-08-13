package com.otcdlink.chiron.middle.tier;

import com.otcdlink.chiron.buffer.BytebufTools;
import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractCommandWebsocketCodecTier<
    INBOUND_ENDPOINT_SPECIFIC,
    INBOUND_DUTY,
    OUTBOUND_ENDPOINT_SPECIFIC,
    OUTBOUND_DUTY
>
    extends SelectiveDuplexTier<
                TextWebSocketFrame,
    Command< ? extends OUTBOUND_ENDPOINT_SPECIFIC, OUTBOUND_DUTY >
            >
{
  private static final Logger LOGGER = LoggerFactory.getLogger(
      AbstractCommandWebsocketCodecTier.class ) ;

  private final CommandBodyDecoder< INBOUND_ENDPOINT_SPECIFIC, INBOUND_DUTY > commandDecoder ;

  /**
   * Need one {@link BytebufTools.Coating} for reading and one for writing because a
   * {@link io.netty.channel.Channel} may do both in response to an inbound message.
   */
  private final BytebufTools.Coating readerCoating = BytebufTools.threadLocalRecyclableCoating() ;

  /**
   * Need one {@link BytebufTools.Coating} for reading and one for writing because a
   * {@link io.netty.channel.Channel} may do both in response to an inbound message.
   */
  private final BytebufTools.Coating writerCoating = BytebufTools.threadLocalRecyclableCoating() ;

  protected AbstractCommandWebsocketCodecTier(
      final CommandBodyDecoder< INBOUND_ENDPOINT_SPECIFIC, INBOUND_DUTY > commandDecoder
  ) {
    this.commandDecoder = checkNotNull( commandDecoder ) ;
  }

  @Override
  protected final void inboundMessage(
      final ChannelHandlerContext channelHandlerContext,
      final TextWebSocketFrame textWebSocketFrame
  ) throws Exception {
    try {
      NettyTools.touchMaybe( textWebSocketFrame, "About to decode inbound message" ) ;
      final PositionalFieldReader fieldReader = readerCoating.coat( textWebSocketFrame.content() );
      final String commandName = fieldReader.readDelimitedString();
      final INBOUND_ENDPOINT_SPECIFIC endpointSpecific =
          readEndpointSpecific( channelHandlerContext, fieldReader );
      if( endpointSpecific == null ) {
        LOGGER.error(
            "Obtained null endpointSpecific for '" + commandName + "' " +
                "while reading " + textWebSocketFrame + ":\n" +
                BytebufTools.fullDump( textWebSocketFrame.content() )
        );
        return;
      }
      final Command< INBOUND_ENDPOINT_SPECIFIC, INBOUND_DUTY > command =
          commandDecoder.decodeBody( endpointSpecific, commandName, fieldReader ) ;
      textWebSocketFrame.release() ;
      if( command == null ) {
        LOGGER.error( "Obtained null " + Command.class.getSimpleName() +
            " for '" + commandName + "'." );
        return;
      }
      forwardInbound( channelHandlerContext, command );
    } catch( final Exception e ) {
      LOGGER.error( "Could not decode: \n" +
          BytebufTools.fullDump( textWebSocketFrame.content() ) ) ;
      throw e ;
    } finally {
      readerCoating.recycle() ;
//      textWebSocketFrame.release() ;
    }

  }

  protected abstract INBOUND_ENDPOINT_SPECIFIC readEndpointSpecific(
      ChannelHandlerContext channelHandlerContext,
      PositionalFieldReader fieldReader
  ) throws IOException;

  @Override
  protected final void outboundMessage(
      final ChannelHandlerContext channelHandlerContext,
      final Command< ? extends OUTBOUND_ENDPOINT_SPECIFIC, OUTBOUND_DUTY> command,
      final ChannelPromise promise
  ) throws Exception {
    final ByteBuf byteBuf = channelHandlerContext.alloc().buffer() ;
    try {
      final PositionalFieldWriter fieldWriter = writerCoating.coat( byteBuf ) ;
      fieldWriter.writeDelimitedString( command.description().name() ) ;
      if( command.description().tracked() ) {
        writeEndpointSpecific( channelHandlerContext, fieldWriter, command.endpointSpecific ) ;
      } else {
        writeEndpointSpecific( channelHandlerContext, fieldWriter, null ) ;
      }
      command.encodeBody( fieldWriter ) ;
      forwardOutbound( channelHandlerContext, new TextWebSocketFrame( byteBuf ), promise ) ;
    } finally {
      writerCoating.recycle() ;
    }
  }

  protected abstract void writeEndpointSpecific(
      ChannelHandlerContext channelHandlerContext,
      PositionalFieldWriter fieldWriter,
      OUTBOUND_ENDPOINT_SPECIFIC endpointSpecific
  ) throws IOException;


}
