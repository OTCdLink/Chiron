package com.otcdlink.chiron.integration.drill.fakeend;

import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.RealConnectorDrill;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;

import java.util.function.Function;

public final class UpendHalfDuplexPack implements ConnectorDrill.UpendHalfDuplex {

  final NettyHalfDuplex.ForTextWebSocketFrame textWebSocketFrameHalfDuplex ;
  final NettyHalfDuplex< PingWebSocketFrame, PongWebSocketFrame > pingPongWebSocketFrameHalfDuplex ;
  final NettyHalfDuplex< CloseWebSocketFrame, CloseWebSocketFrame > closeWebSocketFrameHalfDuplex ;

  public UpendHalfDuplexPack( final Function< Object, ChannelFuture > emitter ) {
    textWebSocketFrameHalfDuplex = new NettyHalfDuplex.ForTextWebSocketFrame<>(
        emitter,
        RealConnectorDrill::asDownwardDuty
    ) ;
    pingPongWebSocketFrameHalfDuplex = new NettyHalfDuplex<>( emitter ) ;
    closeWebSocketFrameHalfDuplex = new NettyHalfDuplex<>( emitter ) ;
  }


  @Override
  public HalfDuplex< SessionLifecycle.Phase, SessionLifecycle.Phase > phasing() {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public HalfDuplex.ForTextWebSocket texting() {
    return textWebSocketFrameHalfDuplex ;
  }

  public HalfDuplex< PingWebSocketFrame, PongWebSocketFrame > ponging() {
    return pingPongWebSocketFrameHalfDuplex ;
  }

  public HalfDuplex< CloseWebSocketFrame, CloseWebSocketFrame > closing() {
    return closeWebSocketFrameHalfDuplex ;
  }

  public void shutdown() {
    textWebSocketFrameHalfDuplex.shutdown() ;
    pingPongWebSocketFrameHalfDuplex.shutdown() ;
    closeWebSocketFrameHalfDuplex.shutdown() ;
  }

}
