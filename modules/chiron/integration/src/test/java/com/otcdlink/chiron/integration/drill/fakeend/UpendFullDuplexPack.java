package com.otcdlink.chiron.integration.drill.fakeend;

import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.RealConnectorDrill;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;

import java.util.function.Function;

public final class UpendFullDuplexPack implements ConnectorDrill.UpendDuplex {

  final NettyDuplex.ForTextWebSocketFrame textWebSocketFrameDuplex;
  final NettyDuplex< PingWebSocketFrame, PongWebSocketFrame > pingPongWebSocketFrameDuplex ;
  final NettyDuplex< CloseWebSocketFrame, CloseWebSocketFrame > closeWebSocketFrameDuplex ;

  public UpendFullDuplexPack( final Function< Object, ChannelFuture > emitter ) {
    textWebSocketFrameDuplex = new NettyDuplex.ForTextWebSocketFrame<>(
        emitter,
        RealConnectorDrill::asDownwardDuty
    ) ;
    pingPongWebSocketFrameDuplex = new NettyDuplex<>( emitter ) ;
    closeWebSocketFrameDuplex = new NettyDuplex<>( emitter ) ;
  }


  @Override
  public FullDuplex< SessionLifecycle.Phase, SessionLifecycle.Phase > phasing() {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public FullDuplex.ForTextWebSocket texting() {
    return textWebSocketFrameDuplex ;
  }

  public FullDuplex< PingWebSocketFrame, PongWebSocketFrame > ponging() {
    return pingPongWebSocketFrameDuplex ;
  }

  public FullDuplex< CloseWebSocketFrame, CloseWebSocketFrame > closing() {
    return closeWebSocketFrameDuplex ;
  }

  public void shutdown() {
    textWebSocketFrameDuplex.shutdown() ;
    pingPongWebSocketFrameDuplex.shutdown() ;
    closeWebSocketFrameDuplex.shutdown() ;
  }

}
