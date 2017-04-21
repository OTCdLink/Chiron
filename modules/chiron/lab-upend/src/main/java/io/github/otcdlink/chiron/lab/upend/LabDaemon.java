package io.github.otcdlink.chiron.lab.upend;

import com.google.common.reflect.TypeToken;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.lab.middle.LabDownwardDuty;
import io.github.otcdlink.chiron.lab.middle.LabMiddleConstants;
import io.github.otcdlink.chiron.lab.middle.LabUpwardDuty;
import io.github.otcdlink.chiron.lab.middle.command.LabDownwardCommandCrafter;
import io.github.otcdlink.chiron.lab.middle.command.LabUpwardCommandResolver;
import io.github.otcdlink.chiron.middle.tier.TimeBoundary;
import io.github.otcdlink.chiron.toolbox.Delegator;
import io.github.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;
import io.github.otcdlink.chiron.upend.TimeKit;
import io.github.otcdlink.chiron.upend.UpendConnector;
import io.github.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import io.github.otcdlink.chiron.upend.session.SecondaryAuthenticator;
import io.github.otcdlink.chiron.upend.session.SignonInwardDuty;
import io.github.otcdlink.chiron.upend.session.SignonOutwardDuty;
import io.github.otcdlink.chiron.upend.session.implementation.DefaultSessionIdentifierGenerator;
import io.github.otcdlink.chiron.upend.session.implementation.DefaultSessionSupervisor;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import org.joda.time.Duration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class is a showcase for wiring different parts or roles: {@link LabUpendLogic},
 * {@link LabUpwardDuty}, {@link LabDownwardDuty}, , {@link SecondaryAuthenticator},
 * {@link DefaultSessionSupervisor}, {@link SignonInwardDuty}, {@link SignonOutwardDuty}.
 */
public class LabDaemon {

  private final UpendConnector<
      LabUpwardDuty< Designator >,
      LabDownwardDuty< Designator >
  > upendConnector ;

  private final HostPort hostPort ;

  public LabDaemon(
      final HostPort hostPort,
      final SecondaryAuthenticator secondaryAuthenticator
  ) throws UnknownHostException {

    this.hostPort = checkNotNull( hostPort ) ;

    final Delegator< SignonInwardDuty > signonInwardDutyDelegator =
        Delegator.create( SignonInwardDuty.class ) ;

    final TimeKit timeKit = TimeKit.fromSystemClock() ;

    final OutwardSessionSupervisor< Channel, InetAddress > outwardSessionSupervisor =
        new DefaultSessionSupervisor<>(
            timeKit,
            new DefaultSessionIdentifierGenerator(),
            signonInwardDutyDelegator.getProxy(),
            secondaryAuthenticator,
            new Duration( 1000 ),
            NettyTools.CHANNEL_REMOTE_ADDRESS_EXTRACTOR,
            Channel::close
        )
    ;

    final Delegator< CommandConsumer< Command<
        Designator,
        LabUpwardDuty< Designator >
    > > > inwardCommandConsumerDelegator = Delegator.create(
        new TypeToken< CommandConsumer<
            Command< Designator, LabUpwardDuty< Designator > > > >() { }
    ) ;


    final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(
        4, ExecutorTools.newCountingDaemonThreadFactory( LabDaemon.class ) ) ;

    upendConnector = new UpendConnector<>( new UpendConnector.Setup<>(
        eventLoopGroup,
        hostPort.asInetSocketAddress(),
        null,
        "/websocket",
        "DEV",
        outwardSessionSupervisor,
        inwardCommandConsumerDelegator.getProxy(),
        timeKit.designatorFactory,
        new LabUpwardCommandResolver<>(),
        null,
        null,
        null,
        TimeBoundary.DEFAULT,
        null,
        null,
        LabMiddleConstants.WEBSOCKET_FRAME_SIZER
    ) ) ;

    final LabUpendLogic labUpendLogic = new LabUpendLogic(
        outwardSessionSupervisor,
        new LabDownwardCommandCrafter<>( upendConnector::sendDownward )
    ) ;

    // The cheapest multi-producer, single-consumer queue.
    final Executor logicExecutor = Executors.newSingleThreadExecutor(
        ExecutorTools.newThreadFactory( LabUpendLogic.class.getSimpleName() ) ) ;

    inwardCommandConsumerDelegator.setDelegate( command -> logicExecutor.execute( () ->
        command.callReceiver( labUpendLogic ) ) ) ;

    // Should happen asynchronously.
    signonInwardDutyDelegator.setDelegate( labUpendLogic ) ;
  }

  public CompletableFuture<?> start() {
    return upendConnector.start() ;
  }

  public CompletableFuture<?> stop() {
    return upendConnector.stop() ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + hostPort.asString() + "}" ;
  }
}
