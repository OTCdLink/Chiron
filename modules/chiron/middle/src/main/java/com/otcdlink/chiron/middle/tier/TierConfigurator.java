package com.otcdlink.chiron.middle.tier;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Convenience for reconfiguring a {@link ChannelPipeline}.
 *
 * @deprecated We probably don't need that thanks to {@link CommandInterceptor}.
 *     Keeping for sentimental reasons.
 */
public final class TierConfigurator< TIERNAME extends TierName > {

  private final ChannelPipeline channelPipeline ;
  private final ImmutableMap< String, TIERNAME > nameMap ;

  public TierConfigurator(
      final ChannelPipeline channelPipeline,
      final ImmutableMap< String, TIERNAME > nameMap
  ) {
    this.channelPipeline = checkNotNull( channelPipeline ) ;
    this.nameMap = checkNotNull( nameMap ) ;
  }

  public ImmutableCollection< TIERNAME > tierNames() {
    return nameMap.values() ;
  }

  public void remove( final TIERNAME tierName ) {
    checkNotNull( tierName ) ;
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public void addBefore( final TIERNAME tierName, final ChannelHandler channelHandler ) {
    checkNotNull( tierName ) ;
    checkNotNull( channelHandler ) ;
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public void addAfter( final TIERNAME tierName, final ChannelHandler channelHandler ) {
    checkNotNull( tierName ) ;
    checkNotNull( channelHandler ) ;
    throw new UnsupportedOperationException( "TODO" ) ;
  }
}
