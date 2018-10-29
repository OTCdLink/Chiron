package com.otcdlink.chiron.upend.tier;

import com.google.common.base.Preconditions;
import com.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import com.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer;
import com.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRequestRelayerTier
    extends SimpleChannelInboundHandler< HttpObject >
{
  private static final Logger LOGGER = LoggerFactory.getLogger( HttpRequestRelayerTier.class ) ;

  private final HttpRequestRelayer httpRequestRelayer ;


  public HttpRequestRelayerTier( final HttpRequestRelayer httpRequestRelayer ) {
    this.httpRequestRelayer = Preconditions.checkNotNull( httpRequestRelayer ) ;
  }

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final HttpObject httpObject
  ) throws Exception {
    final RichHttpRequest richHttpRequest ;
    if( httpObject instanceof FullHttpRequest ) {
      final FullHttpRequest fullHttpRequest = ( FullHttpRequest ) httpObject ;
      try {
        richHttpRequest = new RichHttpRequest(
            fullHttpRequest,
            channelHandlerContext.pipeline()
        ) ;
      } catch( final Exception e ) {
        LOGGER.warn( "Could not handle " + fullHttpRequest + ", " + e.getMessage() ) ;

        new UsualHttpCommands.BadRequest( "Bad request: " + fullHttpRequest.uri() )
            .feed( channelHandlerContext, true ) ;
        return ;
      }
      if( ! httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ) {
        channelHandlerContext.fireChannelRead( fullHttpRequest ) ;
      }
    }
  }

}
