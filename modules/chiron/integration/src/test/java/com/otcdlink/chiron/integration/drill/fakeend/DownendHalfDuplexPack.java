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

public final class DownendHalfDuplexPack implements ConnectorDrill.DownendHalfDuplex {

  final NettyHalfDuplex.ForTextWebSocketFrame< EchoUpwardDuty< Command.Tag > >
      textWebSocketFrameHalfDuplex ;
  final NettyHalfDuplex< PongWebSocketFrame, PingWebSocketFrame > pingPongWebSocketFrameHalfDuplex ;
  final NettyHalfDuplex< CloseWebSocketFrame, CloseWebSocketFrame > closeWebSocketFrameHalfDuplex ;

  public DownendHalfDuplexPack( final Function< Object, ChannelFuture > emitter ) {
    textWebSocketFrameHalfDuplex =
        new NettyHalfDuplex.ForTextWebSocketFrame<>( emitter, RealConnectorDrill::asUpwardDuty ) ;
    pingPongWebSocketFrameHalfDuplex = new NettyHalfDuplex<>( emitter ) ;
    closeWebSocketFrameHalfDuplex = new NettyHalfDuplex<>( emitter ) ;
  }

  @Override
  public HalfDuplex< SessionLifecycle.Phase, SessionLifecycle.Phase > phasing() {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public HalfDuplex.ForTextWebSocket< EchoUpwardDuty< Command.Tag > > texting() {
    return textWebSocketFrameHalfDuplex ;
  }

  public HalfDuplex< PongWebSocketFrame, PingWebSocketFrame > pinging() {
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
