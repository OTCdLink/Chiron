package io.github.otcdlink.chiron.upend.tier;

import io.github.otcdlink.chiron.middle.tier.SelectiveDuplexTier;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings( "TestMethodWithIncorrectSignature" )
/**
 * Let's study how Netty propagates messages inside a {@link io.netty.channel.ChannelPipeline}.
 *
 * <pre>
      |                                        ^
      |-String                                 |-String
      |                                        |
  +---v----------------------------------------|---+
  |   |  Integer codec                         |   |
  |   |                                        |   |
  |  [Non-parseable]---->------->------->-----/|   |
  |   |                                     /  |   |
  |   |                                    |   |   |
  |   |                               [Encode] |   |
  |   |                                    |   |   |
  +---|------------------------------------^---|---+
      |                                    |   |
      |-Integer                    Integer-|   |-String
      |                                    |   |
  +---v--------------------+               |   |
  |   |  Integer verifier  |               |   |
  |   |                    |               |   |
  |  [Odd]-------->------->>>------->------|--/|
  |   |                    |               |   |
  +---|--------------------+               |   |
      |                                    |   |
      |-Integer(even)                      |   |
      |                                    |   |
  +---|------------------------------------|---|---+
  |   |  Logger                            |   |   |
  +---|------------------------------------|---|---+
      |                                    |   |
  +---v--------------------+               |   |
  |   |  Integer divider   |               |   |
  |   |                    |               |   |
  |   [Divide]---->------->>>------->------/   |
  |          \             |                   |
  |           \--[Error]-->>>------->----------/
  |                        |
  +------------------------+

 * </pre>
 */

public class NettyPipelinePlayground {


  /**
   * This test demonstrates {@link ChannelPipeline} traversal .
   * When an {@link ChannelInboundHandler} performs an outbound write, the first
   * {@link ChannelHandler} to get the outbound message is the closest one in the "out" direction.
   */
  @Test
  public void decodeVerifyAndFail() throws Exception {
    writeInbound( "1" ) ;
  }

  @Test
  public void decodeVerifyDivide() throws Exception {
    writeInbound( "2" ) ;
  }

  @Test
  public void decodeFail() throws Exception {
    writeInbound( "Bad" ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( NettyPipelinePlayground.class ) ;

  private static void writeInbound( final String inbound ) throws InterruptedException {
    final EmbeddedChannel embeddedChannel =  new EmbeddedChannel(
        new IntegerCodec(),
        new IntegerVerifier(),
        new LoggingHandler( LogLevel.DEBUG ),
        new IntegerDivider()
    ) ;

    LOGGER.info( "Writing inbound message " + formatMessage( inbound ) + " ..." ) ;
    embeddedChannel.writeInbound( inbound ) ;
    embeddedChannel.flush() ;
    embeddedChannel.close().await() ;
    LOGGER.info( "Outbound message: " +
        formatMessage( embeddedChannel.outboundMessages().remove() ) + "." ) ;
  }

  private static class IntegerCodec extends SelectiveDuplexTier< String, Integer > {

    @Override
    protected void inboundMessage(
        final ChannelHandlerContext channelHandlerContext,
        final String string
    ) throws Exception {
      try {
        final int parsed = Integer.parseInt( string ) ;
        forwardInbound( channelHandlerContext, parsed ) ;
      } catch( final NumberFormatException e ) {
        forwardOutbound(
            channelHandlerContext,
            "Cannot parse " + formatMessage( string ) + "",
            channelHandlerContext.voidPromise()
        ) ;
      }
    }

    @Override
    protected void outboundMessage(
        final ChannelHandlerContext channelHandlerContext,
        final Integer integer,
        final ChannelPromise promise
    ) throws Exception {
      forwardOutbound( channelHandlerContext, Integer.toString( integer ), promise ) ;
    }
  }

  private static class IntegerVerifier extends SimpleChannelInboundHandler< Integer > {

    @Override
    protected void channelRead0(
        final ChannelHandlerContext channelHandlerContext,
        final Integer integer
    ) throws Exception {
      if( integer % 2 == 0 ) {
        channelHandlerContext.fireChannelRead( integer ) ;
      } else {
        channelHandlerContext.write( "Not even: " + integer, channelHandlerContext.voidPromise() ) ;
      }
    }
  }

  private static class IntegerDivider extends SimpleChannelInboundHandler< Integer > {

    @Override
    protected void channelRead0(
        final ChannelHandlerContext channelHandlerContext,
        final Integer integer
    ) throws Exception {
      channelHandlerContext.write( Integer.toString( integer / 2 ) ) ;
    }
  }

  private static String formatMessage( final Object object ) {
    return object == null ? "null" : "'" + object.toString() + "'";
  }


}