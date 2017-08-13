package com.otcdlink.chiron.designator;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;

/**
 * To be implemented by {@link Designator} objects to get notified that
 * {@link Channel#writeAndFlush(Object, ChannelPromise)} happened.
 */
public interface SendingAware {
  void sent() ;
}
