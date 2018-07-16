package com.otcdlink.chiron.integration.drill.fakeend;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.RealConnectorDrill;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;

import java.util.function.Function;

public final class DownendDuplexPack implements ConnectorDrill.DownendDuplex {

  final NettyDuplex.ForTextWebSocketFrame< EchoUpwardDuty< Command.Tag > >
      textWebSocketFrameDuplex;
  final NettyDuplex< PongWebSocketFrame, PingWebSocketFrame > pingPongWebSocketFrameDuplex;
  final NettyDuplex< CloseWebSocketFrame, CloseWebSocketFrame > closeWebSocketFrameDuplex;

  public DownendDuplexPack( final Function< Object, ChannelFuture > emitter ) {
    textWebSocketFrameDuplex =
        new NettyDuplex.ForTextWebSocketFrame<>( emitter, RealConnectorDrill::asUpwardDuty ) ;
    pingPongWebSocketFrameDuplex = new NettyDuplex<>( emitter ) ;
    closeWebSocketFrameDuplex = new NettyDuplex<>( emitter ) ;
  }

  @Override
  public FullDuplex< SessionLifecycle.Phase, SessionLifecycle.Phase > phasing() {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public FullDuplex.ForTextWebSocket< EchoUpwardDuty< Command.Tag > > texting() {
    return textWebSocketFrameDuplex ;
  }

  public FullDuplex< PongWebSocketFrame, PingWebSocketFrame > pinging() {
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
