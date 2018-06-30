package com.otcdlink.chiron.integration.drill.fakeend;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.fixture.websocket.WebSocketFrameAssert;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.assertj.core.api.Assertions;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface HalfDuplex< INBOUND, OUTBOUND > {

  void emit( final OUTBOUND outbound ) ;

  default void emit( final Supplier< OUTBOUND > messageSupplier ) {
    emit( messageSupplier.get() ) ;
  }

  default void expect( final INBOUND expected ) {
    expect( received -> Assertions.assertThat( received ).isEqualTo( expected ) ) ;
  }

  INBOUND next() ;

  void expect( final Consumer< INBOUND > messageVerifier ) ;

  void checkNoUnverifiedExpectation() ;

  void shutdown() ;

  /**
   * Not sure it works with {@link Command#endpointSpecific} not being a {@link Command.Tag}.
   */
  interface ForTextWebSocket< DUTY > extends HalfDuplex< TextWebSocketFrame, TextWebSocketFrame > {
    DUTY emitWithDutyCall() ;
    WebSocketFrameAssert.TextWebSocketFrameAssert assertThatNext() ;
  }
}
