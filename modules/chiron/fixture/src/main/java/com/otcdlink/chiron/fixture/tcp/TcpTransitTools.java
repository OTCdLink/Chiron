package com.otcdlink.chiron.fixture.tcp;

import com.google.common.base.CaseFormat;
import com.otcdlink.chiron.toolbox.ObjectTools;
import com.otcdlink.chiron.toolbox.collection.Autoconstant;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;

public final class TcpTransitTools {

  /**
   * For decorating every {@link TcpTransitServer.Edge#INITIATOR} {@link Channel} with corresponding
   * {@link Junction}.
   */
  public static final AttributeKey< Junction > JUNCTION = AttributeKey.newInstance( "junction" ) ;
  public static final io.netty.util.concurrent.ScheduledFuture< ? > NULL_SCHEDULED_FUTURE =
      ObjectTools.nullObject( io.netty.util.concurrent.ScheduledFuture.class ) ;

  public static final boolean DUMP_PAYLOADS = false ;

  private TcpTransitTools() { }

  public static void removeAllLoggingHandlers( final ChannelPipeline pipeline ) {
    pipeline.forEach( entry -> {
      if ( entry.getValue() instanceof LoggingHandler ) {
        pipeline.remove( entry.getValue() ) ;
      }
    } ) ;
  }

  public static int greatestDelayMs(
      final Collection< ? extends ScheduledFuture > scheduledFutures
  ) {
    long greatestDelay = 0 ;
    for( final ScheduledFuture scheduledFuture : scheduledFutures ) {
      greatestDelay = Math.max( greatestDelay, scheduledFuture.getDelay( TimeUnit.MILLISECONDS ) ) ;
    }
    return Math.toIntExact( greatestDelay ) ;
  }

  public static void releaseIfPossible( final Object message ) {
    if( message instanceof ReferenceCounted ) {
      ( ( ReferenceCounted ) message ).release() ;
    }
  }

  public static byte[] byteArray( final Object byteArrayOrSize ) {
    if( byteArrayOrSize instanceof byte[] ) {
      return ( byte[] ) byteArrayOrSize ;
    } else {
      return null ;
    }
  }

  public static Integer size( final Object byteArrayOrSize ) {
    final byte[] bytes = byteArray( byteArrayOrSize ) ;
    if( bytes == null ) {
      return ( Integer ) byteArrayOrSize ;
    } else {
      return bytes.length ;
    }
  }

  public static Object asByteArrayOrSize( final boolean bytesInDetail, final Object message ) {
    if( message instanceof ByteBuf ) {
      final ByteBuf byteBuf = ( ByteBuf ) message ;
      final int payloadSize = byteBuf.readableBytes() ;
      final byte[] payload ;
      if( bytesInDetail ) {
        payload = new byte[ payloadSize ] ;
        byteBuf.getBytes( 0, payload ) ;
        return payload ;
      } else {
        return payloadSize ;
      }
    } else {
      return null ;
    }


  }

  /**
   * Allow next read, needed because we deactivated {@link ChannelOption#AUTO_READ}.
   */
  static void allowNextRead( final Channel channel ) {
    channel.read() ;
  }

  public static ChannelPipeline addLoggingHandlerFirst(
      final ChannelPipeline pipeline,
      final String name
  ) {
    return pipeline.addFirst( name, new LoggingHandler( name ) ) ;
  }

  public static ChannelPipeline addLoggingHandlerLast(
      final ChannelPipeline pipeline,
      final String name
  ) {
    return pipeline.addLast( name, new LoggingHandler( name ) ) ;
  }

  public enum Direction { INGRESS, EGRESS }

  public static class UnsupportedMessageException extends Exception {
    public UnsupportedMessageException( final Object message ) {
      super( "Unsupported message: " + message ) ;
    }
  }

  public static class ConnectionFailedException extends Exception {
    public ConnectionFailedException(
        final InetSocketAddress targetAddress,
        final Throwable cause
    ) {
      super( "Could not connect to " + targetAddress, cause ) ;
    }
  }

  public static final ChannelInitializer< SocketChannel > VOID_CHANNEL_INITIALIZER =
      new ChannelInitializer< SocketChannel >() {
        @Override
        protected void initChannel( final SocketChannel Ø ) throws Exception {
        }
      }
  ;


  public static final class GenericRoute extends Route {
    public GenericRoute(
        final InetSocketAddress listenAddress,
        final InetSocketAddress targetAddress
    ) {
      super( listenAddress, targetAddress ) ;
    }

    public InetSocketAddress listenAdress() {
      return addresses().get( 0 ) ;
    }

    public InetSocketAddress targetAdress() {
      return addresses().get( 1 ) ;
    }
  }

  /**
   * Giving the full detail along with local addresses and ports greatly helps when debugging
   * with Wireshark.
   */
  public static final class ForwardingRoute extends Route {
    public ForwardingRoute(
        final InetSocketAddress initiatorAddress,
        final InetSocketAddress listenAddress,
        final InetSocketAddress forwarderLocalAddress,
        final InetSocketAddress targetAddress
    ) {
      super( initiatorAddress, listenAddress, forwarderLocalAddress, targetAddress ) ;
    }

    @SuppressWarnings( "unused" )
    public InetSocketAddress initiatorAdress() {
      return addresses().get( 0 ) ;
    }

    @SuppressWarnings( "unused" )
    public InetSocketAddress listenAdress() {
      return addresses().get( 1 ) ;
    }

    @SuppressWarnings( "unused" )
    public InetSocketAddress forwarderLocalAdress() {
      return addresses().get( 2 ) ;
    }

    @SuppressWarnings( "unused" )
    public InetSocketAddress targetAdress() {
      return addresses().get( 3 ) ;
    }
  }

  public static final class ChannelHandlerName extends Autoconstant {

    private static ChannelHandlerName createNew() {
      return new ChannelHandlerName() ;
    }

    public static final ChannelHandlerName INITIAL_CONNECT_TO_PROXY = createNew() ;
    public static final ChannelHandlerName INITIAL_HTTP_CODEC = createNew() ;
    public static final ChannelHandlerName INITIAL_HTTP_AGGREGATOR = createNew() ;
    public static final ChannelHandlerName INGRESS_ENTRY = createNew() ;
    public static final ChannelHandlerName INGRESS_EXIT = createNew() ;
    public static final ChannelHandlerName EGRESS_EXIT = createNew() ;
    public static final ChannelHandlerName INITIATOR_STATE_SUPERVISION = createNew() ;
    public static final ChannelHandlerName TARGET_STATE_SUPERVISION = createNew() ;

    public static final ChannelHandlerName INITIATOR_SIDE_OUT = createNew() ;
    public static final ChannelHandlerName INITIATOR_SIDE_IN = createNew() ;
    public static final ChannelHandlerName TARGET_SIDE_OUT = createNew() ;
    public static final ChannelHandlerName TARGET_SIDE_IN = createNew() ;

    @SuppressWarnings( "unused" )
    public static final Map< String, ChannelHandlerName > MAP =
        valueMap( ChannelHandlerName.class ) ;

    public final String handlerName() {
      return CaseFormat.UPPER_UNDERSCORE.to( CaseFormat.LOWER_HYPHEN, name() ) ;
    }

  }

  public static class DelegatingScheduledFuture< O > implements io.netty.util.concurrent.ScheduledFuture< O > {

    private final io.netty.util.concurrent.ScheduledFuture< O > delegate ;

    public DelegatingScheduledFuture( final io.netty.util.concurrent.ScheduledFuture< O > delegate ) {
      this.delegate = checkNotNull( delegate ) ;
    }

    @Override
    public long getDelay( final TimeUnit unit ) {
      return delegate.getDelay( unit ) ;
    }

    @Override
    public int compareTo( final Delayed o ) {
      return delegate.compareTo( o ) ;
    }

    @Override
    public boolean isSuccess() {
      return delegate.isSuccess() ;
    }

    @Override
    public boolean isCancellable() {
      return delegate.isCancellable() ;
    }

    @Override
    public Throwable cause() {
      return delegate.cause() ;
    }

    @Override
    public Future< O > addListener(
        final GenericFutureListener< ? extends Future< ? super O > > listener
    ) {
      return delegate.addListener( listener ) ;
    }

    @Override
    public Future< O > addListeners(
        final GenericFutureListener< ? extends Future< ? super O > >[] listeners ) {
      return delegate.addListeners( listeners ) ;
    }

    @Override
    public Future< O > removeListener(
        final GenericFutureListener< ? extends Future< ? super O > > listener ) {
      return delegate.removeListener( listener ) ;
    }

    @Override
    public Future< O > removeListeners(
        final GenericFutureListener< ? extends Future< ? super O > >[] listeners
    ) {
      return delegate.removeListeners( listeners ) ;
    }

    @Override
    public Future< O > sync() throws InterruptedException {
      return delegate.sync() ;
    }

    @Override
    public Future< O > syncUninterruptibly() {
      return delegate.syncUninterruptibly() ;
    }

    @Override
    public Future< O > await() throws InterruptedException {
      return delegate.await() ;
    }

    @Override
    public Future< O > awaitUninterruptibly() {
      return delegate.awaitUninterruptibly() ;
    }

    @Override
    public boolean await( final long timeout, final TimeUnit unit ) throws InterruptedException {
      return delegate.await( timeout, unit ) ;
    }

    @Override
    public boolean await( final long timeoutMillis ) throws InterruptedException {
      return delegate.await( timeoutMillis ) ;
    }

    @Override
    public boolean awaitUninterruptibly( final long timeout, final TimeUnit unit ) {
      return delegate.awaitUninterruptibly( timeout, unit ) ;
    }

    @Override
    public boolean awaitUninterruptibly( final long timeoutMillis ) {
      return delegate.awaitUninterruptibly( timeoutMillis ) ;
    }

    @Override
    public O getNow() {
      return delegate.getNow() ;
    }

    @Override
    public boolean cancel( final boolean mayInterruptIfRunning ) {
      return delegate.cancel( mayInterruptIfRunning ) ;
    }

    @Override
    public boolean isCancelled() {
      return delegate.isCancelled() ;
    }

    @Override
    public boolean isDone() {
      return delegate.isDone() ;
    }

    @Override
    public O get() throws InterruptedException, ExecutionException {
      return delegate.get() ;
    }

    @Override
    public O get( final long timeout, final TimeUnit unit )
        throws InterruptedException, ExecutionException, TimeoutException
    {
      return delegate.get( timeout, unit ) ;
    }
  }
}
