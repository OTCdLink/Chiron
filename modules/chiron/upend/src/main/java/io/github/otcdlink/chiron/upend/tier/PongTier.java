package io.github.otcdlink.chiron.upend.tier;

import io.github.otcdlink.chiron.middle.ChannelTools;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Answers to every {@link PingWebSocketFrame} with a {@link PongWebSocketFrame} and triggers
 * a timeout if there was no ping for a certain period.
 *
 * <h2>Not yet supported</h2>
 * Consider any content frame (which is either a {@link TextWebSocketFrame}, a
 * {@link BinaryWebSocketFrame}, or a {@link ContinuationWebSocketFrame}) as a
 * Ping, and wait for some delay before sending a Pong. During this delay, if there
 * is an outbound content frame, don't send the Pong.
 */
public class PongTier extends SimpleChannelInboundHandler< PingWebSocketFrame > {

  private static final Logger LOGGER = LoggerFactory.getLogger( PongTier.class ) ;

  private final long timeoutIfNoPingMs ;

  private final AtomicReference< ScheduledFuture< ? > > nextTimeoutFutureReference =
      new AtomicReference<>() ;


  public PongTier( final long timeoutIfNoPingMs ) {
    super( true ) ;
    checkArgument( timeoutIfNoPingMs > 0 ) ;
    this.timeoutIfNoPingMs = timeoutIfNoPingMs ;
  }

  @Override
  public void channelActive( final ChannelHandlerContext channelHandlerContext ) throws Exception {
    rescheduleTimeout( channelHandlerContext ) ;
    super.channelActive( channelHandlerContext ) ;
  }

  @Override
  public void channelInactive( final ChannelHandlerContext channelHandlerContext )
      throws Exception
  {
    cancelTimeout() ;
    super.channelInactive( channelHandlerContext ) ;
  }

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final PingWebSocketFrame pingWebSocketFrame
  ) throws Exception {
    final Long pingCounter = ChannelTools.extractLongOrNull( LOGGER, pingWebSocketFrame ) ;
    final ByteBuf buffer = channelHandlerContext.alloc().buffer() ;
    buffer.writeLong( pingCounter ) ;
    buffer.touch( "Buffer for pong " + pingCounter ) ;
    final PongWebSocketFrame pongWebSocketFrame = new PongWebSocketFrame( buffer ) ;
    channelHandlerContext.writeAndFlush( pongWebSocketFrame ).addListener( future -> {
      if( future.isSuccess() ) {
        LOGGER.debug( "Responded with pong to ping #" + pingCounter + " from " +
            channelHandlerContext.channel().remoteAddress() + "." ) ;
      } else {
        LOGGER.error( "Pong failed in response to ping #" + pingCounter + ".", future.cause() ) ;
      }
    } ) ;
    rescheduleTimeout( channelHandlerContext ) ;
  }

  private void cancelTimeout() {
    scheduleNext( null ) ;
  }

  private void scheduleNext( final ScheduledFuture< ? > scheduledFuture ) {
    final ScheduledFuture< ? > previous = nextTimeoutFutureReference.getAndSet( scheduledFuture ) ;
    if( previous != null ) {
      previous.cancel( false ) ;
    }
  }

  private void rescheduleTimeout( final ChannelHandlerContext channelHandlerContext ) {
    final ScheduledFuture< ? > scheduledFuture = channelHandlerContext.executor().schedule(
        () -> closeChannel( channelHandlerContext ),
        timeoutIfNoPingMs,
        TimeUnit.MILLISECONDS
    ) ;
    scheduleNext( scheduledFuture ) ;
    LOGGER.debug( "Rescheduled timeout in " + timeoutIfNoPingMs + " ms." ) ;
  }


  private static void closeChannel( final ChannelHandlerContext channelHandlerContext ) {
    channelHandlerContext.close() ;
  }


}
