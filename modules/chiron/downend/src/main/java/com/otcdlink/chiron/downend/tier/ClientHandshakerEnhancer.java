package com.otcdlink.chiron.downend.tier;

import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ProgressivePromise;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import org.fest.reflect.core.Reflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class is a hack to support custom pipeline reconfiguration during a WebSocket handshake.
 * It works with Netty-4.1.CR7 and {@link WebSocketVersion#V13}.
 * The principle is to leave {@link WebSocketServerHandshaker} unchanged, and pass it a
 * fake {@link Channel} instance that swallows every method call that could cause a change.
 * Only methods known to be used receive a faking implementation, other methods throw an
 * exception. The same principle applies for objects (like {@link ChannelPipeline} or
 * {@link EventLoop}) returned by {@link Channel}'s fake methods.
 * <p>
 * See issue
 * <a href="https://github.com/netty/netty/issues/5201" >WebSocket upgrade wrecks HttpProxyHandler #5201</a>.
 */
public class ClientHandshakerEnhancer {

  private static final Logger LOGGER = LoggerFactory.getLogger( ClientHandshakerEnhancer.class ) ;


  public interface PipelineReconfigurator {

    /**
     * Remove handlers for casual HTTP requests (namely {@link HttpObjectAggregator} and
     * {@link HttpContentCompressor}), and add {@link WebSocketFrameEncoder} and
     * {@link WebSocketFrameDecoder}.
     * This method is convenient for chosing exactly which {@link ChannelHandler}s to remove
     * when using {@code io.netty.handler.proxy.HttpProxyHandler} which has its own HTTP stuff,
     * that should not be removed.
     *
     * @see WebSocketServerHandshaker#handshake(Channel, FullHttpRequest, HttpHeaders, ChannelPromise)
     *     for reference.
     */
    void reconfigure(
        ChannelPipeline channelPipeline,
        ConnectionDescriptor connectionDescriptor,
        WebSocketFrameEncoder webSocketFrameEncoder,
        WebSocketFrameDecoder webSocketFrameDecoder,
        Executor executor
    ) ;

    /**
     * Should do the same as Netty's original.
     */
    PipelineReconfigurator DEFAULT = (
        channelPipeline,
        connectionDescriptor,
        webSocketFrameEncoder,
        webSocketFrameDecoder,
        executor
    ) -> {
      final HttpContentDecompressor decompressor =
          channelPipeline.get( HttpContentDecompressor.class ) ;
      if( decompressor != null ) {
        channelPipeline.remove( decompressor ) ;
      }

      HttpObjectAggregator aggregator = channelPipeline.get( HttpObjectAggregator.class ) ;
      if( aggregator != null ) {
        channelPipeline.remove( aggregator ) ;
      }

      ChannelHandlerContext channelHandlerContext =
           channelPipeline.context( HttpResponseDecoder.class ) ;
      if( channelHandlerContext == null ) {
        channelHandlerContext = channelPipeline.context( HttpClientCodec.class ) ;
        if( channelHandlerContext == null ) {
          throw new IllegalStateException( "ChannelPipeline does not contain " +
              "a HttpRequestEncoder or HttpClientCodec" ) ;
        }
        final HttpClientCodec codec = ( HttpClientCodec ) channelHandlerContext.handler() ;
        // Remove the encoder part of the codec as the user may start writing frames
        // after this method returns.
        codec.removeOutboundHandler() ;

        channelPipeline.addAfter(
            channelHandlerContext.name(), "ws-decoder", webSocketFrameDecoder ) ;

        // Delay the removal of the decoder so the user can setup the pipeline if needed
        // to handle WebSocketFrame messages.
        // See https://github.com/netty/netty/issues/4533
        channelPipeline.channel().eventLoop().execute( () -> channelPipeline.remove( codec ) ) ;
      } else {
        if( channelPipeline.get( HttpRequestEncoder.class ) != null ) {
          // Remove the encoder part of the codec as the user may start writing frames
          // after this method returns.
          channelPipeline.remove( HttpRequestEncoder.class ) ;
        }
        final ChannelHandlerContext context = channelHandlerContext ;
        channelPipeline.addAfter( context.name(), "ws-decoder", webSocketFrameDecoder ) ;

        // Delay the removal of the decoder so the user can setup the pipeline if needed
        // to handle WebSocketFrame messages.
        // See https://github.com/netty/netty/issues/4533
        channelPipeline.channel().eventLoop().execute(
            () -> channelPipeline.remove( context.handler() ) ) ;
      }
    } ;
  }

  private final WebSocketClientHandshaker standardHandshaker ;
  private final PipelineReconfigurator pipelineReconfigurator ;

  public ClientHandshakerEnhancer( final WebSocketClientHandshaker standardHandshaker ) {
    this( standardHandshaker, PipelineReconfigurator.DEFAULT ) ;
  }

  public ClientHandshakerEnhancer(
      final WebSocketClientHandshaker standardHandshaker,
      final PipelineReconfigurator pipelineReconfigurator
  ) {
    this.standardHandshaker = checkNotNull( standardHandshaker ) ;
    this.pipelineReconfigurator = pipelineReconfigurator ;
  }

  public void finishHandshake(
      final Channel channel,
      final ConnectionDescriptor connectionDescriptor,
      final FullHttpResponse fullHttpResponse
  ) {
    final DelegatingChannel delegatingChannel = new DelegatingChannel( channel ) ;
    standardHandshaker.finishHandshake( delegatingChannel, fullHttpResponse ) ;

    LOGGER.debug( "Handshake finished, now apply custom Pipeline reconfiguration ..." ) ;

    pipelineReconfigurator.reconfigure(
        channel.pipeline(),
        connectionDescriptor,
        newEncoder( standardHandshaker ),
        newDecoder( standardHandshaker ),
        channel.eventLoop()
    ) ;
  }

  private static WebSocketFrameDecoder newDecoder( final WebSocketClientHandshaker handshaker ) {
    return Reflection.method( "newWebsocketDecoder" )  // Yes, with small 's'.
        .withReturnType( WebSocketFrameDecoder.class ).in( handshaker ).invoke() ;
  }

  private static WebSocketFrameEncoder newEncoder( final WebSocketClientHandshaker handshaker ) {
    return Reflection.method( "newWebSocketEncoder" ).withReturnType( WebSocketFrameEncoder.class )
        .in( handshaker ).invoke() ;
  }


// =================
// DelegatingChannel
// =================

  /**
   * Prevents from modifying a {@link ChannelPipeline}.
   */
  private static class DelegatingChannel implements Channel {
    private final ChannelPipeline channelPipeline ;

    private DelegatingChannel( final Channel delegate ) {
      channelPipeline = new DelegatingChannelPipeline( delegate.pipeline() );
    }

    private static RuntimeException throwException() {
      throw new UnsupportedOperationException( "Do not call" ) ;
    }

    @Override
    public ChannelId id() {
      throw throwException() ;
    }

    @Override
    public EventLoop eventLoop() {
      return NullEventLoop.INSTANCE ;
    }

    @Override
    public Channel parent() {
      throw throwException() ;
    }

    @Override
    public ChannelConfig config() {
      throw throwException() ;
    }

    @Override
    public boolean isOpen() {
      throw throwException() ;
    }

    @Override
    public boolean isRegistered() {
      throw throwException() ;
    }

    @Override
    public boolean isActive() {
      throw throwException() ;
    }

    @Override
    public ChannelMetadata metadata() {
      throw throwException() ;
    }

    @Override
    public SocketAddress localAddress() {
      throw throwException() ;
    }

    @Override
    public SocketAddress remoteAddress() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture closeFuture() {
      throw throwException() ;
    }

    @Override
    public boolean isWritable() {
      throw throwException() ;
    }

    @Override
    public long bytesBeforeUnwritable() {
      throw throwException() ;
    }

    @Override
    public long bytesBeforeWritable() {
      throw throwException() ;
    }

    @Override
    public Unsafe unsafe() {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline pipeline() {
      return channelPipeline ;
    }

    @Override
    public ByteBufAllocator alloc() {
      throw throwException() ;
    }

    @Override
    public ChannelPromise newPromise() {
      throw throwException() ;
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture newSucceededFuture() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture newFailedFuture( final Throwable cause ) {
      throw throwException() ;
    }

    @Override
    public ChannelPromise voidPromise() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture bind( final SocketAddress localAddress ) {
      throw throwException() ;

    }

    @Override
    public ChannelFuture connect( final SocketAddress remoteAddress ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture connect(
        final SocketAddress remoteAddress,
        final SocketAddress localAddress
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture disconnect() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture close() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture deregister() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture bind( final SocketAddress localAddress, final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture connect(
        final SocketAddress remoteAddress,
        final ChannelPromise promise
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture connect(
        final SocketAddress remoteAddress,
        final SocketAddress localAddress,
        final ChannelPromise promise
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture disconnect( final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture close( final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture deregister( final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public Channel read() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture write( final Object msg ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture write( final Object msg, final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public Channel flush() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture writeAndFlush( final Object msg, final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture writeAndFlush( final Object msg ) {
      throw throwException() ;
    }

    @Override
    public < T > Attribute< T > attr( final AttributeKey< T > key ) {
      throw throwException() ;
    }

    @Override
    public < T > boolean hasAttr( final AttributeKey< T > key ) {
      throw throwException() ;
    }

    @Override
    public int compareTo( final Channel channel ) {
      throw throwException() ;
    }
  }

// =========================
// DelegatingChannelPipeline
// =========================

  /**
   * Prevents from modifying the real {@link ChannelPipeline}.
   */
  private static class DelegatingChannelPipeline implements ChannelPipeline {

    private final ChannelPipeline delegate ;

    private DelegatingChannelPipeline( final ChannelPipeline delegate ) {
      this.delegate = checkNotNull( delegate ) ;
    }

    @Override
    public ChannelPipeline addFirst( final String name, final ChannelHandler handler ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline addFirst( 
        final EventExecutorGroup group, 
        final String name, 
        final ChannelHandler handler 
    ) {
      throw throwException() ;
    }


    @Override
    public ChannelPipeline addLast( final String name, final ChannelHandler handler ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline addLast( 
        final EventExecutorGroup group, 
        final String name, 
        final ChannelHandler handler 
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline addBefore( 
        final String baseName, 
        final String name, 
        final ChannelHandler handler 
    ) {
      return null ; // Don't use the result.
    }

    @Override
    public ChannelPipeline addBefore( 
        final EventExecutorGroup group, 
        final String baseName, 
        final String name, 
        final ChannelHandler handler 
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline addAfter( 
        final String baseName, 
        final String name, 
        final ChannelHandler handler 
    ) {
      return this ;
    }

    @Override
    public ChannelPipeline addAfter( 
        final EventExecutorGroup group, 
        final String baseName, 
        final String name, 
        final ChannelHandler handler 
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline addFirst( final ChannelHandler... handlers ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline addFirst( 
        final EventExecutorGroup group, 
        final ChannelHandler... handlers 
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline addLast( final ChannelHandler... handlers ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline addLast( 
        final EventExecutorGroup group, 
        final ChannelHandler... handlers 
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline remove( final ChannelHandler handler ) {
      return this ;
    }

    @Override
    public ChannelHandler remove( final String name ) {
      throw throwException() ;
    }

    @Override
    public < T extends ChannelHandler > T remove( final Class< T > handlerType ) {
      throw throwException() ;
    }

    @Override
    public ChannelHandler removeFirst() {
      throw throwException() ;
    }

    @Override
    public ChannelHandler removeLast() {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline replace( 
        final ChannelHandler oldHandler, 
        final String newName, 
        final ChannelHandler newHandler 
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelHandler replace( 
        final String oldName, 
        final String newName, 
        final ChannelHandler newHandler 
    ) {
      throw throwException() ;
    }

    @Override
    public < T extends ChannelHandler > T replace( 
        final Class< T > oldHandlerType,
        final String newName, 
        final ChannelHandler newHandler 
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelHandler first() {
      throw throwException() ;
    }

    @Override
    public ChannelHandlerContext firstContext() {
      throw throwException() ;
    }

    @Override
    public ChannelHandler last() {
      throw throwException() ;
    }

    @Override
    public ChannelHandlerContext lastContext() {
      throw throwException() ;
    }

    @Override
    public ChannelHandler get( final String name ) {
      throw throwException() ;
    }

    @Override
    public < T extends ChannelHandler > T get( final Class< T > handlerType ) {
      return delegate.get( handlerType ) ;
    }

    @Override
    public ChannelHandlerContext context( final ChannelHandler handler ) {
      throw throwException() ;
    }

    @Override
    public ChannelHandlerContext context( final String name ) {
      throw throwException() ;
    }

    @Override
    public ChannelHandlerContext context( final Class< ? extends ChannelHandler > handlerType ) {
      return delegate.context( handlerType ) ;
    }

    @Override
    public Channel channel() {
      throw throwException() ;
    }

    @Override
    public List< String > names() {
      throw throwException() ;
    }

    @Override
    public Map< String, ChannelHandler > toMap() {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline fireChannelRegistered() {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline fireChannelUnregistered() {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline fireChannelActive() {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline fireChannelInactive() {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline fireExceptionCaught( final Throwable cause ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline fireUserEventTriggered( final Object event ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline fireChannelRead( final Object msg ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline fireChannelReadComplete() {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline fireChannelWritabilityChanged() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture bind( final SocketAddress localAddress ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture connect( final SocketAddress remoteAddress ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture connect(
        final SocketAddress remoteAddress,
        final SocketAddress localAddress
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture disconnect() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture close() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture deregister() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture bind( final SocketAddress localAddress, final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture connect( final SocketAddress remoteAddress, final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture connect( final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture disconnect( final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture close( final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture deregister( final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline read() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture write( final Object msg ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture write( final Object msg, final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelPipeline flush() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture writeAndFlush( final Object msg, final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture writeAndFlush( final Object msg ) {
      throw throwException() ;
    }

    @Override
    public Iterator< Map.Entry< String, ChannelHandler > > iterator() {
      throw throwException() ;
    }


    @Override
    public ChannelPromise newPromise() {
      throw throwException() ;
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture newSucceededFuture() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture newFailedFuture( final Throwable cause ) {
      throw throwException() ;
    }

    @Override
    public ChannelPromise voidPromise() {
      throw throwException() ;
    }

    private static RuntimeException throwException() {
      throw new UnsupportedOperationException( "Do not call" ) ;
    }


  }

// ==============
// Null EventLoop
// ==============

  private static class NullEventLoop implements EventLoop {

    public static final EventLoop INSTANCE = new NullEventLoop() ;

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "#INSTANCE{}" ;
    }

    private static RuntimeException throwException() {
      throw new UnsupportedOperationException( "Do not call" ) ;
    }

    @Override
    public EventLoopGroup parent() {
      throw throwException() ;
    }

    @Override
    public boolean inEventLoop() {
      throw throwException() ;
    }

    @Override
    public boolean inEventLoop( final Thread thread ) {
      throw throwException() ;
    }

    @Override
    public < V > Promise< V > newPromise() {
      throw throwException() ;
    }

    @Override
    public < V > ProgressivePromise< V > newProgressivePromise() {
      throw throwException() ;
    }

    @Override
    public < V > Future< V > newSucceededFuture( final V result ) {
      throw throwException() ;
    }

    @Override
    public < V > Future< V > newFailedFuture( final Throwable cause ) {
      throw throwException() ;
    }

    @Override
    public boolean isShuttingDown() {
      throw throwException() ;
    }

    @Override
    public Future< ? > shutdownGracefully() {
      throw throwException() ;
    }

    @Override
    public Future< ? > shutdownGracefully(
        final long quietPeriod,
        final long timeout,
        final TimeUnit unit
    ) {
      throw throwException() ;
    }

    @Override
    public Future< ? > terminationFuture() {
      throw throwException() ;
    }

    @Override
    public void shutdown() {
      throw throwException() ;
    }

    @Override
    public List< Runnable > shutdownNow() {
      throw throwException() ;
    }

    @Override
    public boolean isShutdown() {
      throw throwException() ;
    }

    @Override
    public boolean isTerminated() {
      throw throwException() ;
    }

    @Override
    public boolean awaitTermination( final long timeout, final TimeUnit unit ) {
      throw throwException() ;
    }

    @Override
    public EventLoop next() {
      throw throwException() ;
    }

    @Override
    public Iterator< EventExecutor > iterator() {
      throw throwException() ;
    }

    @Override
    public ChannelFuture register( final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public Future< ? > submit( final Runnable task ) {
      throw throwException() ;
    }

    @Override
    public < T > List< java.util.concurrent.Future< T > > invokeAll(
        final Collection< ? extends Callable< T > > tasks
    ) throws InterruptedException {
      throw throwException() ;
    }

    @Override
    public < T > List< java.util.concurrent.Future< T > > invokeAll(
        final Collection< ? extends Callable< T > > tasks,
        final long timeout,
        final TimeUnit unit
    ) throws InterruptedException {
      throw throwException() ;
    }

    @Override
    public < T > T invokeAny( final Collection< ? extends Callable< T > > tasks ) {
      throw throwException() ;
    }

    @Override
    public < T > T invokeAny(
        final Collection< ? extends Callable< T > > tasks,
        final long timeout,
        final TimeUnit unit
    ) throws InterruptedException, ExecutionException, TimeoutException {
      throw throwException() ;
    }

    @Override
    public < T > Future< T > submit( final Runnable task, final T result ) {
      throw throwException() ;
    }

    @Override
    public < T > Future< T > submit( final Callable< T > task ) {
      throw throwException() ;
    }

    @Override
    public ScheduledFuture< ? > schedule(
        final Runnable command,
        final long delay,
        final TimeUnit unit
    ) {
      throw throwException() ;
    }

    @Override
    public < V > ScheduledFuture< V > schedule(
        final Callable< V > callable,
        final long delay,
        final TimeUnit unit
    ) {
      throw throwException() ;
    }

    @Override
    public ScheduledFuture< ? > scheduleAtFixedRate(
        final Runnable command,
        final long initialDelay,
        final long period,
        final TimeUnit unit
    ) {
      throw throwException() ;
    }

    @Override
    public ScheduledFuture< ? > scheduleWithFixedDelay(
        final Runnable command,
        final long initialDelay,
        final long delay,
        final TimeUnit unit
    ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture register( final Channel channel ) {
      throw throwException() ;
    }

    @Override
    public ChannelFuture register( final Channel channel, final ChannelPromise promise ) {
      throw throwException() ;
    }

    @Override
    public void execute( final Runnable command ) {
      LOGGER.debug( "Silently swallowing " + command + " in " + this + "." ) ;
    }

  }

}
