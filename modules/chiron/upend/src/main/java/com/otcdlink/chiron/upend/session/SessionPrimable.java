package com.otcdlink.chiron.upend.session;

/**
 * Lets a {@link com.otcdlink.chiron.upend.tier.SessionEnforcerTier} set the {@link SESSION_PRIMER}
 * to any {@link io.netty.channel.ChannelHandlerContext} needing it.
 */
public interface SessionPrimable< SESSION_PRIMER > {

  void primeWith( SESSION_PRIMER sessionPrimer ) ;
}
