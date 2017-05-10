package io.github.otcdlink.chiron.downend.tier;

import io.github.otcdlink.chiron.middle.ChannelTools;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class PongTier extends SimpleChannelInboundHandler< PongWebSocketFrame > {

  private static final Logger LOGGER = LoggerFactory.getLogger( PongTier.class ) ;

  public interface Claim {
    void pongFrameReceived( Long receivedPongCounter ) ;
  }
  private final Claim claim ;

  public PongTier( final Claim claim ) {
    this.claim = checkNotNull( claim ) ;
  }


  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final PongWebSocketFrame pongWebSocketFrame
  ) {
//    pongWebSocketFrame.retain() ; // Why do we need this?
    final Long pingCounter = ChannelTools.extractLongOrNull( LOGGER, pongWebSocketFrame ) ;
    claim.pongFrameReceived( pingCounter ) ;
  }
}
