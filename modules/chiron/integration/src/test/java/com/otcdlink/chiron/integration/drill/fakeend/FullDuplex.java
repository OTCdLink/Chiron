package com.otcdlink.chiron.integration.drill.fakeend;

import com.otcdlink.chiron.buffer.BytebufTools;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.fixture.websocket.WebSocketFrameAssert;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.tier.SessionPhaseWebsocketCodecTier;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.assertj.core.api.Assertions;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The contract for emitting {@link OUTBOUND} and receiving {@link INBOUND} objects, and asserting
 * on received ones.
 * "Full duplex" means emission and reception don't block each other. Implementors are likely to
 * use some queuing mechanism.
 */
public interface FullDuplex< INBOUND, OUTBOUND > {

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
  interface ForTextWebSocket< DUTY > extends FullDuplex< TextWebSocketFrame, TextWebSocketFrame > {

    default void emitPhase( final SessionLifecycle.Phase phase ) {
      final UnpooledHeapByteBuf byteBuf = new UnpooledHeapByteBuf(
          UnpooledByteBufAllocator.DEFAULT, 1000, 1000 ) ;
      SessionPhaseWebsocketCodecTier.serializePhaseTo( phase, BytebufTools.coat( byteBuf ) ) ;
      emit( new TextWebSocketFrame( byteBuf ) ) ;
    }

    DUTY emitWithDutyCall() ;

    WebSocketFrameAssert.TextWebSocketFrameAssert assertThatNext() ;
  }
}
