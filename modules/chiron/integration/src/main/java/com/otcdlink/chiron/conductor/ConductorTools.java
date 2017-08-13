package com.otcdlink.chiron.conductor;

import com.google.common.base.Charsets;
import com.otcdlink.chiron.buffer.BytebufCoat;
import com.otcdlink.chiron.buffer.BytebufTools;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Command.Tag;
import com.otcdlink.chiron.downend.tier.CommandWebsocketCodecDownendTier;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.tier.SessionPhaseWebsocketCodecTier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.io.IOException;
import java.util.function.Function;

public final class ConductorTools {

  private ConductorTools() { }


  public static class CommandToTextwebsocketframeConductor
      extends Conductor< TextWebSocketFrame, TextWebSocketFrame, Command< Tag, ? >>
  {

    public CommandToTextwebsocketframeConductor() {
      super( command -> new TextWebSocketFrame( fullWireEncode( command ) ) ) ;
    }

    private final Responder< TextWebSocketFrame, TextWebSocketFrame > responder =
        new ReplayingResponder() {
          @Override
          protected TextWebSocketFrame asRecordable( final TextWebSocketFrame textWebSocketFrame ) {
            return safeTextWebSocketFrame( textWebSocketFrame ) ;
          }
        }
    ;

    @Override
    public Responder< TextWebSocketFrame, TextWebSocketFrame > responder() {
      return responder ;
    }

    public class CommandGuide extends RecordingGuide {
      public void waitForMatch( final Command<Command.Tag, ? > command ) {
        final String encoded = fullWireEncode( command ) ;
        waitForInboundMatching( textWebSocketFrame ->
            encoded.equals( textWebSocketFrame.text() ) ) ;
      }
    }

    private final CommandGuide guide = new CommandGuide() ;
    @Override
    public CommandGuide guide() {
      return guide ;
    }
  }

  public static class AutomaticWebsocketframeResponder
      extends Conductor.AutomaticResponder< TextWebSocketFrame, TextWebSocketFrame >
  {

    public AutomaticWebsocketframeResponder(
        final Function< TextWebSocketFrame, TextWebSocketFrame > transformation
    ) {
      super( transformation ) ;
    }

    public static AutomaticWebsocketframeResponder uppercase() {
      return new AutomaticWebsocketframeResponder( textWebSocketFrame ->
          new TextWebSocketFrame( textWebSocketFrame.text().toUpperCase() ) ) ;
    }
  }
  public static class AutomaticPingwebsocketframeResponder
      extends Conductor.AutomaticResponder< PingWebSocketFrame, PongWebSocketFrame >
  {

    public AutomaticPingwebsocketframeResponder(
        final Function< PingWebSocketFrame, PongWebSocketFrame > transformation
    ) {
      super( transformation ) ;
    }

    public static AutomaticPingwebsocketframeResponder justPong() {
      return new AutomaticPingwebsocketframeResponder(
          PingConductor::createEchoingPongWebSocketFrame ) ;
    }
  }

  public static class AutomaticClosewebsocketframeResponder
      extends Conductor.AutomaticResponder< CloseWebSocketFrame, Void >
  {

    public AutomaticClosewebsocketframeResponder() {
      super( frame -> null ) ;
    }

    public static AutomaticClosewebsocketframeResponder doNothing() {
      return new AutomaticClosewebsocketframeResponder() ;
    }
  }

  public static class PingConductor
      extends Conductor.RawConductor< PingWebSocketFrame, PongWebSocketFrame >
  {
    private volatile boolean pongEnabled = true ;

    private final Responder< PingWebSocketFrame, PongWebSocketFrame > responder =
        new AbstractRecordingResponder() {
          @Override
          protected PongWebSocketFrame doRespond( final PingWebSocketFrame pingWebSocketFrame ) {
            return pongEnabled ?
                createEchoingPongWebSocketFrame( pingWebSocketFrame ) : null ;
          }
        }
    ;

    private static PongWebSocketFrame createEchoingPongWebSocketFrame(
        final PingWebSocketFrame pingWebSocketFrame
    ) {
      return new PongWebSocketFrame( pingWebSocketFrame.content().copy() ) ;
    }

    @Override
    public Responder< PingWebSocketFrame, PongWebSocketFrame > responder() {
      return responder ;
    }

    public class PingGuide extends RecordingGuide {
      public void pongEnabled( final boolean enabled ) {
        pongEnabled = enabled ;
      }

    }

    private final PingGuide pingGuide = new PingGuide() ;

    @Override
    public PingGuide guide() {
      return pingGuide ;
    }
  }

  public static class SessionLifecyclePhaseConductor
      extends Conductor< TextWebSocketFrame, TextWebSocketFrame, SessionLifecycle.Phase>
  {

    private volatile boolean responseEnabled ;

    public SessionLifecyclePhaseConductor() {
      this( true ) ;
    }

    public SessionLifecyclePhaseConductor( final boolean responseEnabled ) {
      super( phase -> {
        final ByteBuf byteBuf = Unpooled.buffer() ;
        final BytebufCoat coat = BytebufTools.coat( byteBuf ) ;
        coat.writeAsciiUnsafe( "SessionLifecycle$Phase " ) ;
        SessionLifecycle.serialize( coat, phase ) ;
        final TextWebSocketFrame textWebSocketFrame = new TextWebSocketFrame( byteBuf ) ;
        return textWebSocketFrame ;
      } ) ;
      this.responseEnabled = responseEnabled ;
    }

    private final Responder< TextWebSocketFrame, TextWebSocketFrame > responder =
        new ReplayingResponder() {
          @Override
          protected TextWebSocketFrame doRespond( final TextWebSocketFrame textWebSocketFrame ) {
            if( responseEnabled ) {
              return super.doRespond( textWebSocketFrame ) ;
            } else {
              return null ;
            }
          }

          @Override
          protected TextWebSocketFrame asRecordable( final TextWebSocketFrame textWebSocketFrame ) {
            final BytebufCoat coat = BytebufTools.coat( textWebSocketFrame.content() ) ;
            try {
              return SessionPhaseWebsocketCodecTier.MAGIC_MATCHER.matches( coat ) ?
                  safeTextWebSocketFrame( textWebSocketFrame ) :
                  null
              ;
            } catch( final DecodeException e ) {
              throw new RuntimeException( e ) ;
            }
          }
        }
    ;


    @Override
    public Responder< TextWebSocketFrame, TextWebSocketFrame > responder() {
      return responder ;
    }

    public final class PhaseGuide extends RecordingGuide {
      public void responseEnabled( final boolean enabled ) {
        responseEnabled = enabled ;
      }
      public boolean responseEnabled() {
        return responseEnabled ;
      }
    }

    private final PhaseGuide phaseGuide = new PhaseGuide() ;

    @Override
    public PhaseGuide guide() {
      return phaseGuide ;
    }
  }

  public static class CloseFrameConductor
      extends Conductor.RawConductor< CloseWebSocketFrame, Void >
  {
    public final class CloseFrameResponder extends ReplayingResponder {
      @Override
      protected Void doRespond( final CloseWebSocketFrame closeWebSocketFrame ) {
        closeWebSocketFrame.retain() ;
        return null ;
      }

      @Override
      protected CloseWebSocketFrame asRecordable( final CloseWebSocketFrame closeWebSocketFrame ) {
        closeWebSocketFrame.retain() ;
        return closeWebSocketFrame ;
      }
    }

    private final CloseFrameResponder responder = new CloseFrameResponder() ;

    @Override
    public Responder< CloseWebSocketFrame, Void > responder() {
      return responder ;
    }

    public final class CloseFrameGuide extends RecordingGuide { }

    private final CloseFrameGuide guide = new CloseFrameGuide() ;

    @Override
    public CloseFrameGuide guide() {
      return guide ;
    }
  }


// ===============
// Other utilities
// ===============

  private static TextWebSocketFrame safeTextWebSocketFrame(
      final TextWebSocketFrame textWebSocketFrame
  ) {
    textWebSocketFrame.retain() ;
    return textWebSocketFrame ;
  }

  /**
   * Duplicates the logic of
   * {@link CommandWebsocketCodecDownendTier}
   * but extracting it would be too much mess for too little.
   */
  public static String fullWireEncode( final Command<Command.Tag, ? > command ) {
    final ByteBuf buffer = Unpooled.buffer() ;
    final BytebufCoat coat = BytebufTools.coat( buffer ) ;
    try {
      coat.writeDelimitedString( command.description().name() ) ;
      coat.writeDelimitedString( command.endpointSpecific.asString() ) ;
      command.encodeBody( coat ) ;
      return buffer.toString( Charsets.US_ASCII ) ;
    } catch( final IOException e ) {
      throw new RuntimeException( e ) ;
    }
  }


}
