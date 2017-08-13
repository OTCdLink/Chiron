package com.otcdlink.chiron.downend.tier;

import com.otcdlink.chiron.middle.tier.TierName;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.proxy.HttpProxyHandler;

import java.util.Map;

/**
 * We define our own names so we know exactly which {@link ChannelHandler} we remove.
 * Otherwise {@link WebSocketClientHandshaker} messes up {@link HttpProxyHandler}'s
 * {@link ChannelHandler}s.
 */
public final class DownendTierName extends TierName {

  private DownendTierName() { }

  public static final DownendTierName HTTP_PROXY = createNew() ;
  public static final DownendTierName SSL_HANDLER = createNew() ;
  public static final DownendTierName INITIAL_HTTP_CLIENT_CODEC = createNew() ;
  public static final DownendTierName INITIAL_HTTP_OBJECT_AGGREGATOR = createNew() ;
  public static final DownendTierName WS_ENCODER = createNew() ;
  public static final DownendTierName WS_DECODER = createNew() ;
  public static final DownendTierName WS_FRAME_AGGREGATOR = createNew() ;
  public static final DownendTierName WS_FRAME_FRAGMENTER = createNew() ;
  public static final DownendTierName SUPERVISION = createNew() ;
  public static final DownendTierName PING_PONG = createNew() ;
  public static final DownendTierName SESSION_PHASE_CODEC = createNew() ;
  public static final DownendTierName SESSION = createNew() ;
  public static final DownendTierName COMMAND_CODEC = createNew() ;
  public static final DownendTierName COMMAND_RECEIVER = createNew() ;
  public static final DownendTierName COMMAND_OUTBOUND_INTERCEPTOR = createNew() ;
  public static final DownendTierName CATCHER = createNew() ;

  @SuppressWarnings( "unused" )
  public static final Map< String, DownendTierName > MAP = valueMap( DownendTierName.class ) ;


  private static DownendTierName createNew() {
    return new DownendTierName() ;
  }
}
