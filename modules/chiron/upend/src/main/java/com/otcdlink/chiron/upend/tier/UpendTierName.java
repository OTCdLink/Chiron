package com.otcdlink.chiron.upend.tier;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.middle.tier.TierName;

public class UpendTierName extends TierName {

  protected UpendTierName() { }
  
  protected static UpendTierName createNew() {
    return new UpendTierName() ;
  }

  public static final UpendTierName TLS = createNew() ;
  public static final UpendTierName HTTP_SERVER_CODEC = createNew() ;
  public static final UpendTierName HTTP_SERVER_AGGREGATOR = createNew() ;
  public static final UpendTierName HTTP_IMMEDIATE_COMMAND_RECOGNIZER_HTTP = createNew() ;
  public static final UpendTierName HTTP_COMMAND_RENDERER = createNew() ;

  public static final UpendTierName WEBSOCKET_PONG = createNew() ;

  /**
   * Name decided by Netty.
   */
  public static final UpendTierName WSENCODER = createNew() ;

  /**
   * Name decided by Netty.
   */
  public static final UpendTierName WSDECODER = createNew() ;

  public static final UpendTierName CHUNKED_WRITER = createNew() ;

  public static final UpendTierName WEBSOCKET_UPGRADER = createNew() ;
  public static final UpendTierName WEBSOCKET_FRAME_AGGREGATOR = createNew() ;
  public static final UpendTierName WEBSOCKET_FRAME_FRAGMENTER = createNew() ;
//    public static final TierName WEBSOCKET_FRAME_SIZING = createNew() ;
  public static final UpendTierName WEBSOCKET_COMMAND_CODEC = createNew() ;
  public static final UpendTierName SESSION_ENFORCER = createNew() ;
  public static final UpendTierName COMMAND_INTERCEPTOR = createNew() ;
  public static final UpendTierName COMMAND_RECEIVER = createNew() ;
  public static final UpendTierName CATCHER = createNew() ;
  public static final UpendTierName WEBSOCKET_PHASE_CODEC = createNew() ;

  @SuppressWarnings( "unused" )
  public static final ImmutableMap< String, UpendTierName > MAP = valueMap( UpendTierName.class ) ;

}
