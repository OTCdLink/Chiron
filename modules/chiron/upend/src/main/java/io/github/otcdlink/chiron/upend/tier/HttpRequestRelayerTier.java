package io.github.otcdlink.chiron.upend.tier;

import com.google.common.base.Preconditions;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.github.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer;
import io.github.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRequestRelayerTier
    extends SimpleChannelInboundHandler< FullHttpRequest >
{
  private static final Logger LOGGER = LoggerFactory.getLogger( HttpRequestRelayerTier.class ) ;
  private final HttpRequestRelayer httpRequestRelayer ;

  public HttpRequestRelayerTier( final HttpRequestRelayer httpRequestRelayer ) {
    this.httpRequestRelayer = Preconditions.checkNotNull( httpRequestRelayer ) ;
  }

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final FullHttpRequest fullHttpRequest
  ) throws Exception {
    final RichHttpRequest richHttpRequest;
    try {
      richHttpRequest = new RichHttpRequest(
          fullHttpRequest,
          channelHandlerContext.pipeline()
      ) ;
    } catch( final Exception e ) {
      LOGGER.error( "Could not handle " + fullHttpRequest + ".", e ) ;

      new UsualHttpCommands.BadRequest( "Bad request: " + fullHttpRequest.uri() )
          .feed( channelHandlerContext ) ;
      return ;
    }
    if( ! httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ) {
      channelHandlerContext.fireChannelRead( fullHttpRequest ) ;
    }
  }

}
