package com.otcdlink.chiron.middle.tier;

import com.otcdlink.chiron.command.Command;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.TypeParameterMatcher;
import org.joda.time.Duration;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Wraps a {@link CommandInterceptor} into a {@link ChannelHandler} that can run upend or downend.
 */
public abstract class CommandInterceptorTier
    extends ChannelDuplexHandler
    implements VisitableInterceptor
{

  private final TypeParameterMatcher commandMatcher ;

  protected final CommandInterceptor commandInterceptor ;

  protected CommandInterceptorTier( final CommandInterceptor commandInterceptor ) {
    this.commandInterceptor = checkNotNull( commandInterceptor ) ;
    commandMatcher = TypeParameterMatcher.get( Command.class ) ;
  }

  /**
   * We can reuse the same instance because {@link ChannelHandlerContext} is the same for a
   * {@link Channel}'s lifetime, and method calls in one given {@link Channel} don't happen
   * concurrently.
   */
  private InboundSink inboundSink = null ;

  protected final CommandInterceptor.Sink inboundSink(
      final ChannelHandlerContext channelHandlerContext
  ) {
    if( inboundSink == null || inboundSink.channelHandlerContext != channelHandlerContext ) {
      inboundSink = new InboundSink( channelHandlerContext ) ;
    }
    return inboundSink ;
  }

  private OutboundSink outboundSink = null ;

  protected final CommandInterceptor.Sink outboundSink(
      final ChannelHandlerContext channelHandlerContext
  ) {
    if( outboundSink == null || outboundSink.channelHandlerContext != channelHandlerContext ) {
      outboundSink = new OutboundSink( channelHandlerContext ) ;
    }
    return outboundSink ;
  }

  @Override
  public final void visit( final Consumer< CommandInterceptor > visitor ) {
    commandInterceptor.visit( visitor ) ;
  }

  @Override
  public final void channelRead(
      final ChannelHandlerContext channelHandlerContext,
      final Object inbound
  ) throws Exception {
    boolean handled = false ;
    if( commandMatcher.match( inbound ) ) {
      @SuppressWarnings( "unchecked" ) final Command inboundCommand = ( Command ) inbound ;
      handled = interceptRead( channelHandlerContext, inboundCommand ) ;
    }
    if( ! handled ) {
      channelHandlerContext.fireChannelRead( inbound ) ;
    }
  }

  protected abstract boolean interceptRead(
      ChannelHandlerContext channelHandlerContext,
      Command inboundCommand
  ) ;


  @Override
  public final void write(
      final ChannelHandlerContext channelHandlerContext,
      final Object outbound,
      final ChannelPromise promise
  ) throws Exception {
    boolean handled = false ;
    if( commandMatcher.match( outbound ) ) {
      @SuppressWarnings( "unchecked" ) final Command outboundCommand = ( Command ) outbound ;
      handled = interceptWrite( channelHandlerContext, outboundCommand );
    }
    if( ! handled ) {
      channelHandlerContext.write( outbound, promise ) ;
    }
  }

  protected abstract boolean interceptWrite(
      final ChannelHandlerContext channelHandlerContext,
      final Command outboundCommand
  ) ;


// =====
// Sinks
// =====

  private static abstract class AbstractSink implements CommandInterceptor.Sink {
    public final ChannelHandlerContext channelHandlerContext ;

    private AbstractSink( final ChannelHandlerContext channelHandlerContext ) {
      this.channelHandlerContext = checkNotNull( channelHandlerContext ) ;
    }
  }


  private static final class InboundSink extends AbstractSink {

    private InboundSink( final ChannelHandlerContext channelHandlerContext ) {
      super( channelHandlerContext ) ;
    }

    @Override
    public void sendForward( final Object message ) {
      channelHandlerContext.fireChannelRead( message ) ;
    }

    @Override
    public void sendBackward( final Object message ) {
      channelHandlerContext.writeAndFlush( message ) ;
    }

    @Override
    public ScheduledFuture< ? > scheduledSend(
        final Object message,
        final org.joda.time.Duration delay
    ) {
      return channelHandlerContext.executor().schedule(
          () -> sendForward( message ), delay.getMillis(), TimeUnit.MILLISECONDS ) ;
    }

  }

  private static final class OutboundSink extends AbstractSink {

    private OutboundSink( final ChannelHandlerContext channelHandlerContext ) {
      super( channelHandlerContext ) ;
    }

    @Override
    public void sendForward( final Object message ) {
      channelHandlerContext.writeAndFlush( message ) ;
    }

    @Override
    public void sendBackward( final Object message ) {
      channelHandlerContext.fireChannelRead( message ) ;
    }

    @Override
    public ScheduledFuture< ? > scheduledSend( final Object message, final Duration delay ) {
      return channelHandlerContext.executor().schedule(
          () -> sendForward( message ), delay.getMillis(), TimeUnit.MILLISECONDS ) ;
    }
  }

}
