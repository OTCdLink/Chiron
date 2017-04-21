package io.github.otcdlink.chiron.downend.babyupend;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.conductor.ConductorTools;
import io.github.otcdlink.chiron.integration.echo.DownwardEchoCommand;
import io.github.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandToTextwebsocketframeConductorTest {

  @Test
  public void justRespond() throws Exception {
    final ConductorTools.CommandToTextwebsocketframeConductor responderGuide =
        new ConductorTools.CommandToTextwebsocketframeConductor() ;
    responderGuide.guide().record( DOWNWARD_ECHO_COMMAND ) ;
    final TextWebSocketFrame responded =
        responderGuide.responder().respond( TEXTWEBSOCKETFRAME_UPWARD ) ;
    assertThat( responded.text() ).isEqualTo( TEXTWEBSOCKETFRAME_DOWNWARD.text() ) ;
    responderGuide.guide().waitForMatch( UPWARD_ECHO_COMMAND ) ;
  }


// =======
// Fixture
// =======

  private static final UpwardEchoCommand< Command.Tag > UPWARD_ECHO_COMMAND =
      new UpwardEchoCommand<>( new Command.Tag( "T1" ), "World" ) ;

  private static final DownwardEchoCommand< Command.Tag > DOWNWARD_ECHO_COMMAND =
      new DownwardEchoCommand<>( new Command.Tag( "T1" ), "Hello World" ) ;

  private static final TextWebSocketFrame TEXTWEBSOCKETFRAME_UPWARD =
      new TextWebSocketFrame( ConductorTools.fullWireEncode( UPWARD_ECHO_COMMAND ) ) ;

  private static final TextWebSocketFrame TEXTWEBSOCKETFRAME_DOWNWARD =
      new TextWebSocketFrame( ConductorTools.fullWireEncode( DOWNWARD_ECHO_COMMAND ) ) ;


}