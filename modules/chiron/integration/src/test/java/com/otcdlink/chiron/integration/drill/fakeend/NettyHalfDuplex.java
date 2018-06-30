package com.otcdlink.chiron.integration.drill.fakeend;

import com.google.common.util.concurrent.Uninterruptibles;
import com.otcdlink.chiron.fixture.websocket.WebSocketFrameAssert;
import com.otcdlink.chiron.toolbox.text.Plural;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;

class NettyHalfDuplex< INBOUND, OUTBOUND > implements HalfDuplex< INBOUND, OUTBOUND > {

  private static final Logger LOGGER = LoggerFactory.getLogger( NettyHalfDuplex.class ) ;

  private final AtomicBoolean active = new AtomicBoolean( true ) ;

  private final Function< ? super OUTBOUND, ChannelFuture > asynchronousEmitter ;

  private final BlockingQueue< ContextualizedInbound< INBOUND > > inboundQueue =
      new LinkedBlockingQueue<>() ;

  private final List< ReferenceCounted > referenceCountedObjects =
      Collections.synchronizedList( new ArrayList<>(  ) ) ;

  NettyHalfDuplex( final Function< ? super OUTBOUND, ChannelFuture > asynchronousEmitter ) {
    this.asynchronousEmitter = checkNotNull( asynchronousEmitter ) ;
  }

  @Override
  public void emit( OUTBOUND message ) {
    checkState( active.get() ) ;
    asynchronousEmitter.apply( message ).syncUninterruptibly() ;
  }

  /**
   * Called from Netty's {@link io.netty.channel.EventLoop}.
   */
  void receive( final ChannelHandlerContext channelHandlerContext, final INBOUND inbound ) {
    checkState( active.get() ) ;
    if( inbound instanceof ReferenceCounted ) {
      final ReferenceCounted referenceCounted = ( ReferenceCounted ) inbound ;
      referenceCounted.retain() ;
      referenceCountedObjects.add( referenceCounted ) ;
    }
    inboundQueue.add( new ContextualizedInbound<>( channelHandlerContext, inbound ) ) ;
  }

  @Override
  public INBOUND next() {
    checkState( active.get() ) ;
    return Uninterruptibles.takeUninterruptibly( inboundQueue ).message ;
  }

  @Override
  public void expect( Consumer< INBOUND > inboundVerifier ) {
    checkState( active.get() ) ;
    inboundVerifier.accept( next() ) ;
  }

  @Override
  public void checkNoUnverifiedExpectation() {
    checkState( active.get() ) ;
    assertThat( inboundQueue ).isEmpty() ;
  }

  @Override
  public void shutdown() {
    checkNoUnverifiedExpectation() ;
    checkState( active.compareAndSet( true, false ) ) ;
    final int referenceCount = referenceCountedObjects.size() ;
    for( final ReferenceCounted referenceCounted : referenceCountedObjects ) {
      referenceCounted.release() ;
    }
    LOGGER.debug( "Released " +
        Plural.s( referenceCount, ReferenceCounted.class.getSimpleName() + " object" ) ) ;
  }

  public static final class ForTextWebSocketFrame< DUTY >
      extends NettyHalfDuplex< TextWebSocketFrame, TextWebSocketFrame >
      implements HalfDuplex.ForTextWebSocket< DUTY >
  {
    private final Function< Consumer< TextWebSocketFrame >, DUTY > dutyRedirector ;

    ForTextWebSocketFrame(
        final Function< ? super TextWebSocketFrame, ChannelFuture > asynchronousEmitter,
        Function< Consumer< TextWebSocketFrame >, DUTY > dutyRedirector ) {
      super( asynchronousEmitter ) ;
      this.dutyRedirector = checkNotNull( dutyRedirector ) ;
    }

    @Override
    public DUTY emitWithDutyCall() {
      return dutyRedirector.apply( this::emit ) ;
    }

    @Override
    public WebSocketFrameAssert.TextWebSocketFrameAssert assertThatNext() {
      return WebSocketFrameAssert.assertThat( next() ) ;
    }
  }

}
