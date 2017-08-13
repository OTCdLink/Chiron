package com.otcdlink.chiron.middle.tier;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;

import java.net.SocketAddress;

public class WebsocketFrameSizingTier extends ChannelDuplexHandler {

  private final WebsocketFragmenterTier fragmenterTier ;
  private final WebSocketFrameAggregator aggregatorTier ;

  public WebsocketFrameSizingTier( final int maximumFrameSize ) {
    fragmenterTier = new WebsocketFragmenterTier( maximumFrameSize ) ;
    aggregatorTier = new WebSocketFrameAggregator( maximumFrameSize ) ;
  }

  @Override
  public void channelRead(
      final ChannelHandlerContext channelHandlerContext,
      final Object message
  ) throws Exception {
    aggregatorTier.channelRead( channelHandlerContext, message ) ;
  }

  @Override
  public void write(
      final ChannelHandlerContext channelHandlerContext,
      final Object message,
      final ChannelPromise promise
  ) throws Exception {
    fragmenterTier.write( channelHandlerContext, message, promise ) ;
  }

  @Override
  public void connect(
      final ChannelHandlerContext channelHandlerContext,
      final SocketAddress remoteAddress,
      final SocketAddress localAddress,
      final ChannelPromise future
  ) throws Exception {
    fragmenterTier.connect( channelHandlerContext, remoteAddress, localAddress, future ) ;
  }

  @Override
  public void handlerAdded( final ChannelHandlerContext channelHandlerContext ) throws Exception {
    fragmenterTier.handlerAdded( channelHandlerContext ) ;
    aggregatorTier.handlerAdded( channelHandlerContext ) ;
  }

  @Override
  public void handlerRemoved( final ChannelHandlerContext channelHandlerContext ) throws Exception {
    fragmenterTier.handlerRemoved( channelHandlerContext ); ;
    aggregatorTier.handlerRemoved( channelHandlerContext ); ;
  }
}
