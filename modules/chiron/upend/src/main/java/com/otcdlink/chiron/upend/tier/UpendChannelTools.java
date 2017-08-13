package com.otcdlink.chiron.upend.tier;

import com.otcdlink.chiron.upend.session.SessionSupervisor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

final class UpendChannelTools {
  private UpendChannelTools() { }

  /**
   * @see #closeChannelQuietly(Channel)
   * @see SessionEnforcerTier#channelInactive(ChannelHandlerContext)
   */
  public static final AttributeKey< Boolean > SHOULD_NOTIFIY_ON_CHANNEL_INACTIVE =
      AttributeKey.newInstance( "SHOULD_NOTIFIY_ON_CHANNEL_INACTIVE" ) ;

  /**
   * When all internal notifications have occured, use this method to close the
   * {@link Channel} without triggering any further notification.
   *
   * @see SessionEnforcerTier#channelInactive(ChannelHandlerContext)
   * @see SessionSupervisor.ChannelCloser
   */
  public static void closeChannelQuietly( final Channel channel ) {
    channel.attr( SHOULD_NOTIFIY_ON_CHANNEL_INACTIVE ).set( false ) ;
    channel.close() ;
  }

}
