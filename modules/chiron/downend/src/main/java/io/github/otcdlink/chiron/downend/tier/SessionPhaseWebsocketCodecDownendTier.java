package io.github.otcdlink.chiron.downend.tier;

import io.github.otcdlink.chiron.middle.session.SessionLifecycle;
import io.github.otcdlink.chiron.middle.tier.SessionPhaseWebsocketCodecTier;
import io.netty.channel.ChannelHandlerContext;

public class SessionPhaseWebsocketCodecDownendTier
    extends SessionPhaseWebsocketCodecTier
{
  @Override
  protected void inboundCloseFrame( final ChannelHandlerContext channelHandlerContext ) {
    forwardInbound( channelHandlerContext, SessionLifecycle.Kickout.create() ) ;
  }
}
