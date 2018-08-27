package com.otcdlink.chiron.lab.upend;

import com.google.common.reflect.TypeToken;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.lab.middle.LabDownwardDuty;
import com.otcdlink.chiron.lab.middle.LabMiddleConstants;
import com.otcdlink.chiron.lab.middle.LabUpwardDuty;
import com.otcdlink.chiron.lab.middle.command.LabDownwardCommandCrafter;
import com.otcdlink.chiron.lab.middle.command.LabUpwardCommandResolver;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.toolbox.Delegator;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import com.otcdlink.chiron.toolbox.internet.HostPort;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.otcdlink.chiron.upend.TimeKit;
import com.otcdlink.chiron.upend.UpendConnector;
import com.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import com.otcdlink.chiron.upend.session.SecondaryAuthenticator;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;
import com.otcdlink.chiron.upend.session.SignonOutwardDuty;
import com.otcdlink.chiron.upend.session.implementation.DefaultSessionIdentifierGenerator;
import com.otcdlink.chiron.upend.session.implementation.DefaultSessionSupervisor;
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
      LabDownwardDuty< Designator >,
      Void
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

    final OutwardSessionSupervisor< Channel, InetAddress, Void > outwardSessionSupervisor =
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
