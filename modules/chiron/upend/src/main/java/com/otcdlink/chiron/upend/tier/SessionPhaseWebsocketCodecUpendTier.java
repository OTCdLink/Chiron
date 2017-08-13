package com.otcdlink.chiron.upend.tier;

import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.tier.SessionPhaseWebsocketCodecTier;
import io.netty.channel.ChannelHandlerContext;

public class SessionPhaseWebsocketCodecUpendTier
    extends SessionPhaseWebsocketCodecTier
{
  @Override
  protected SessionLifecycle.Phase decorateMaybe( final SessionLifecycle.Phase phase ) {
    return phase ;
  }

  @Override
  protected void inboundCloseFrame( final ChannelHandlerContext channelHandlerContext ) {
    forwardInbound( channelHandlerContext, SessionLifecycle.Signoff.create() ) ;
  }
}
