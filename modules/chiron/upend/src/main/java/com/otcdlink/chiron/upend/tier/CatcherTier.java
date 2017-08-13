package com.otcdlink.chiron.upend.tier;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CatcherTier
    extends SimpleChannelInboundHandler< IOException >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( CatcherTier.class ) ;

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final IOException ioException
  ) throws Exception {
    throw new UnsupportedOperationException( "Not supposed to be called" ) ;
  }

  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext,
      final Throwable throwable
  ) throws Exception {
    if( throwable instanceof IOException ) {
      LOGGER.info( "Caught in " + channelHandlerContext + ": " +
          throwable.getClass().getName() + ", " + throwable.getMessage() ) ;
    } else {
      super.exceptionCaught( channelHandlerContext, throwable ) ;
    }
  }
}
