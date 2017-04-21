package io.github.otcdlink.chiron.upend.tier;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.upend.oldhttp.OldHttpCommandRenderer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

@Deprecated
public class OldCommandHttpRendereTier extends ChannelOutboundHandlerAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      OldCommandHttpRendereTier.class ) ;

  private final OldHttpCommandRenderer httpCommandRenderer;

  public OldCommandHttpRendereTier( final OldHttpCommandRenderer httpCommandRenderer ) {
    this.httpCommandRenderer = checkNotNull( httpCommandRenderer ) ;
  }

  @Override
  public void write(
      final ChannelHandlerContext channelHandlerContext,
      final Object outbound,
      final ChannelPromise promise
  ) throws Exception {
    if( outbound instanceof Command ) {
      final FullHttpResponse fullHttpResponse = httpCommandRenderer.render(
          channelHandlerContext.alloc(),
          HttpVersion.HTTP_1_1,
          ( Command< ?, ? > ) outbound
      ) ;
      HttpUtil.setContentLength(
          fullHttpResponse, fullHttpResponse.content().readableBytes() ) ;
      channelHandlerContext.writeAndFlush( fullHttpResponse ) ;
    } else {
      channelHandlerContext.write( outbound, promise ) ;
    }

  }


  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext,
      final Throwable cause
  ) {
    LOGGER.error( "Caught Throwable in " + this + ".", cause ) ;
    channelHandlerContext.close() ;
  }
}
