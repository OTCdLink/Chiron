package io.github.otcdlink.chiron.lab.downend;

import io.github.otcdlink.chiron.downend.CommandTransceiver;
import io.github.otcdlink.chiron.downend.SignonMaterializer;
import io.github.otcdlink.chiron.downend.Tracker;
import io.github.otcdlink.chiron.lab.middle.LabDownwardDuty;
import io.github.otcdlink.chiron.lab.middle.LabMiddleConstants;
import io.github.otcdlink.chiron.lab.middle.LabUpwardDuty;
import io.github.otcdlink.chiron.lab.middle.command.LabDownwardCommandResolver;
import io.github.otcdlink.chiron.lab.middle.command.LabUpwardCommandCrafter;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.clock.Clock;
import io.github.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.netty.channel.nio.NioEventLoopGroup;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class LabDownend {

  private final URL websocketUrl ;

  private final CommandTransceiver< LabDownwardDuty< Tracker >, LabUpwardDuty< Tracker > >
      commandTransceiver ;

  private final LabUpwardDuty< Tracker > labUpwardDuty ;

  public LabDownend(
      final HostPort hostPort,
      final SignonMaterializer signonMaterializer,
      final CommandTransceiver.ChangeWatcher changeWatcher,
      final LabDownwardDuty< Tracker > labDownwardDuty
  ) {
    websocketUrl = UrxTools.parseUrlQuiet(
        "http://" + hostPort.asString() + "/websocket" ) ;

    commandTransceiver = new CommandTransceiver<>( new CommandTransceiver.Setup<>(
        Clock.SYSTEM_CLOCK,
        new NioEventLoopGroup( 2, ExecutorTools.newCountingDaemonThreadFactory( LabDownend.class ) ),
        websocketUrl,
        null,
        null,
        TimeBoundary.DEFAULT,
        signonMaterializer,
        changeWatcher,
        new LabDownwardCommandResolver<>(),
        command -> command.callReceiver( labDownwardDuty ),
        null,
        LabMiddleConstants.WEBSOCKET_FRAME_SIZER
    ) ) ;

    labUpwardDuty = new LabUpwardCommandCrafter<>( commandTransceiver::send ) ;
  }

  public CompletableFuture< ? > start() {
    return commandTransceiver.start() ;
  }

  public LabUpwardDuty< Tracker > upwardDuty() {
    return labUpwardDuty ;
  }

  public CompletableFuture< ? > stop() {
    return commandTransceiver.stop() ;
  }

  public CommandTransceiver.Setup setup() {
    return commandTransceiver.setup() ;
  }

  @Override
  public String toString() {
    return LabDownend.class.getSimpleName() + "{" + websocketUrl.toExternalForm() + "}" ;
  }
}
