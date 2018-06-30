package com.otcdlink.chiron.integration.drill;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.integration.drill.fakeend.FakeDownend;
import com.otcdlink.chiron.integration.drill.fakeend.FakeUpend;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import com.otcdlink.chiron.mockster.Mockster;
import com.otcdlink.chiron.toolbox.CollectingException;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.otcdlink.chiron.integration.drill.FeatureConflictException.checkNo;

/**
 * Describes everything we need to know in order to build a {@link ConnectorDrill}.
 */
public final class DrillBuilder {

  final boolean tls;
  final boolean proxy ;
  final boolean portForwarder ;
  final WebsocketFrameSizer webSocketFrameSizer ;
  final TimeBoundary.ForAll timeBoundary ;
  final int mocksterTimeoutDuration ;
  final TimeUnit mocksterTimeoutUnit ;
  final ForDownend forDownend ;
  final ForUpend forUpend ;


  static final DrillBuilder DEFAULT = new DrillBuilder(
      false,
      false,
      false,
      null,
      null,
      Mockster.DEFAULT_TIMEOUT_MS,
      TimeUnit.MILLISECONDS,
      null,
      null
  ) ;

  private DrillBuilder(
      final Boolean tls,
      final Boolean proxy,
      boolean portForwarder,
      final WebsocketFrameSizer webSocketFrameSizer,
      final TimeBoundary.ForAll timeBoundary,
      final int mocksterTimeoutDuration,
      final TimeUnit mocksterTimeoutUnit,
      final ForDownend forDownend,
      final ForUpend forUpend
  ) {
    this.tls = tls ;
    this.proxy = proxy ;
    this.portForwarder = portForwarder ;
    this.webSocketFrameSizer = webSocketFrameSizer ;
    this.timeBoundary = timeBoundary ;
    this.mocksterTimeoutDuration = mocksterTimeoutDuration ;
    this.mocksterTimeoutUnit = mocksterTimeoutUnit ;
    this.forDownend = forDownend ;
    this.forUpend = forUpend ;
  }

  public DrillBuilder withTls( final boolean tls ) {
    return new DrillBuilder(
        tls,
        proxy,
        portForwarder,
        webSocketFrameSizer,
        timeBoundary,
        mocksterTimeoutDuration,
        mocksterTimeoutUnit,
        forDownend,
        forUpend
    ) ;
  }

  public DrillBuilder withProxy( final boolean proxy ) {
    return new DrillBuilder(
        tls,
        proxy,
        portForwarder,
        webSocketFrameSizer,
        timeBoundary,
        mocksterTimeoutDuration,
        mocksterTimeoutUnit,
        forDownend,
        forUpend
    ) ;
  }

  public DrillBuilder withTimeBoundary( final TimeBoundary.ForAll timeBoundary ) {
    return new DrillBuilder(
        tls,
        proxy,
        portForwarder,
        webSocketFrameSizer,
        timeBoundary,
        mocksterTimeoutDuration,
        mocksterTimeoutUnit,
        forDownend,
        forUpend
    ) ;
  }

  public DrillBuilder withMocksterTimeout( final int duration, final TimeUnit timeUnit ) {
    checkArgument( duration > 0 ) ;
    checkNotNull( timeUnit ) ;
    return new DrillBuilder(
        tls,
        proxy,
        portForwarder,
        webSocketFrameSizer,
        timeBoundary,
        duration,
        timeUnit,
        forDownend,
        forUpend
    ) ;
  }



  public ConnectorDrill build() throws CollectingException {
    return new RealConnectorDrill( this ) ;
  }

  public static abstract class ForAnyEnd {
    final DrillBuilder drillBuilder ;
    final ConnectorDrill.AutomaticLifecycle automaticLifecycle;

    public ForAnyEnd(
        final DrillBuilder drillBuilder,
        final ConnectorDrill.AutomaticLifecycle automaticLifecycle
    ) {
      this.drillBuilder = checkNotNull( drillBuilder ) ;
      this.automaticLifecycle = checkNotNull( automaticLifecycle ) ;
    }

  }

  // =======
// Downend
// =======

  public ForDownendConnector forDownendConnector() {
    return new ForDownendConnector( this, ConnectorDrill.ForDownend.Kind.DOWNEND_CONNECTOR ) ;
  }

  public ForDownendConnector forCommandTransceiver() {
    return new ForDownendConnector( this, ConnectorDrill.ForDownend.Kind.COMMAND_TRANSCEIVER ) ;
  }

  public ForFakeDownend fakeDownend() {
    checkNo( tls, FakeDownend.class.getSimpleName() + " doesn't support TLS" ) ;
    checkNo( proxy, FakeDownend.class.getSimpleName() + " doesn't support a proxy" ) ;
    checkNo( portForwarder,
        FakeDownend.class.getSimpleName() + " doesn't support a port forwarder" ) ;
    return new ForFakeDownend( this ) ;
  }

  public static class ForDownend extends ForAnyEnd {
    final ConnectorDrill.ForDownend.Kind kind ;

    protected ForDownend(
        final DrillBuilder drillBuilder,
        final ConnectorDrill.ForDownend.Kind kind,
        final ConnectorDrill.AutomaticLifecycle automaticLifecycle
    ) {
      super( drillBuilder, automaticLifecycle ) ;
      this.kind = checkNotNull( kind ) ;
    }

  }

  public static class ForDownendConnector extends ForDownend {
    final CommandInterceptor commandInterceptor ;

    private ForDownendConnector(
        final DrillBuilder drillBuilder,
        final ConnectorDrill.ForDownend.Kind kind,
        final ConnectorDrill.AutomaticLifecycle automaticLifecycle,
        final CommandInterceptor commandInterceptor
    ) {
      super( drillBuilder, kind, automaticLifecycle ) ;
      this.commandInterceptor = commandInterceptor ;
    }

    private ForDownendConnector(
        final DrillBuilder drillBuilder,
        final ConnectorDrill.ForDownend.Kind kind
    ) {
      this( drillBuilder, kind, ConnectorDrill.AutomaticLifecycle.BOTH, null ) ;
    }

    public ForDownendConnector withCommandInterceptor(
        final CommandInterceptor commandInterceptor
    ) {
      return new ForDownendConnector( drillBuilder, kind, automaticLifecycle, commandInterceptor ) ;
    }


    public ForDownendConnector automaticLifecycle(
        final ConnectorDrill.AutomaticLifecycle automaticLifecycle
    ) {
      return new ForDownendConnector( drillBuilder, kind, automaticLifecycle, commandInterceptor ) ;
    }

    public DrillBuilder done() {
      return new DrillBuilder(
          drillBuilder.tls,
          drillBuilder.proxy,
          drillBuilder.portForwarder,
          drillBuilder.webSocketFrameSizer,
          drillBuilder.timeBoundary,
          drillBuilder.mocksterTimeoutDuration,
          drillBuilder.mocksterTimeoutUnit,
          this,
          drillBuilder.forUpend
      ) ;
    }
  }

  public class ForFakeDownend extends ForDownend {

    public ForFakeDownend( DrillBuilder drillBuilder ) {
      this( drillBuilder, ConnectorDrill.AutomaticLifecycle.BOTH ) ;
    }

    public ForFakeDownend(
        final DrillBuilder drillBuilder,
        final ConnectorDrill.AutomaticLifecycle automaticLifecycle
    ) {
      super( drillBuilder, ConnectorDrill.ForDownend.Kind.FAKE, automaticLifecycle ) ;
    }

    public ForFakeDownend automaticLifecycle( final ConnectorDrill.AutomaticLifecycle automaticLifecycle ) {
      return new ForFakeDownend( drillBuilder, automaticLifecycle ) ;
    }

    public DrillBuilder done() {
      return new DrillBuilder(
          false,
          false,
          false,
          webSocketFrameSizer,
          timeBoundary,
          mocksterTimeoutDuration,
          mocksterTimeoutUnit,
          this,
          forUpend
      ) ;
    }

  }

// =====
// Upend
// =====

  public ForUpendConnector forUpendConnector() {
    return new ForUpendConnector( this ) ;
  }

  public ForFakeUpend fakeUpend() {
    checkNo( tls, FakeUpend.class.getSimpleName() + " doesn't support TLS" ) ;
    return new ForFakeUpend( this ) ;
  }


  public static class ForUpend extends ForAnyEnd {
    final ConnectorDrill.ForUpend.Kind kind ;

    protected ForUpend(
        final DrillBuilder drillBuilder,
        final ConnectorDrill.ForUpend.Kind kind,
        final ConnectorDrill.AutomaticLifecycle automaticLifecycle
    ) {
      super( drillBuilder, automaticLifecycle ) ;
      this.kind = checkNotNull( kind ) ;
    }

  }

  public static class ForUpendConnector extends ForUpend {

    final ConnectorDrill.Authentication authentication ;
    final CommandInterceptor commandInterceptor ;
    final ConnectorDrill.ForUpendConnector.HttpRequestRelayerKind httpRequestRelayerKind ;

    public ForUpendConnector( final DrillBuilder drillBuilder ) {
      this(
          drillBuilder,
          ConnectorDrill.ForUpend.Kind.REAL,
          ConnectorDrill.AutomaticLifecycle.BOTH,
          ConnectorDrill.Authentication.ONE_FACTOR,
          null,
          ConnectorDrill.ForUpendConnector.HttpRequestRelayerKind.NONE
      ) ;
    }

    public ForUpendConnector(
        final DrillBuilder drillBuilder,
        final ConnectorDrill.ForUpend.Kind kind,
        final ConnectorDrill.AutomaticLifecycle automaticLifecycle,
        final ConnectorDrill.Authentication authentication,
        final CommandInterceptor commandInterceptor,
        final ConnectorDrill.ForUpendConnector.HttpRequestRelayerKind httpRequestRelayerKind
    ) {
      super( drillBuilder, kind, automaticLifecycle ) ;
      this.authentication = authentication ;
      this.commandInterceptor = commandInterceptor ;
      this.httpRequestRelayerKind = checkNotNull( httpRequestRelayerKind ) ;
    }

    public ForUpendConnector automaticLifecycle(
        final ConnectorDrill.AutomaticLifecycle automaticLifecycle
    ) {
      return new ForUpendConnector(
          drillBuilder,
          kind,
          automaticLifecycle,
          authentication,
          commandInterceptor,
          httpRequestRelayerKind
      ) ;
    }

    public ForUpendConnector withCommandInterceptor( final CommandInterceptor commandInterceptor ) {
      return new ForUpendConnector(
          drillBuilder,
          kind,
          automaticLifecycle,
          authentication,
          commandInterceptor,
          httpRequestRelayerKind
      ) ;
    }

    /**
     * Will cause every {@link Designator} to be {@link Designator.Kind#UPWARD}.
     *
     * @see RealConnectorDrill.DefaultForUpendConnector.SessionlessDesignatorFactory
     */
    public ForUpendConnector withAuthentication(
        final ConnectorDrill.Authentication authentication
    ) {
      return new ForUpendConnector(
          drillBuilder,
          kind,
          automaticLifecycle,
          authentication,
          commandInterceptor,
          httpRequestRelayerKind
      ) ;
    }

    public ForUpendConnector withHttpRelayer(
        final ConnectorDrill.ForUpendConnector.HttpRequestRelayerKind httpRequestRelayerKind
    ) {
      return new ForUpendConnector(
          drillBuilder,
          kind,
          automaticLifecycle,
          authentication,
          commandInterceptor,
          httpRequestRelayerKind
      ) ;
    }

    public DrillBuilder done() {
      return new DrillBuilder(
          drillBuilder.tls,
          drillBuilder.proxy,
          drillBuilder.portForwarder,
          drillBuilder.webSocketFrameSizer,
          drillBuilder.timeBoundary,
          drillBuilder.mocksterTimeoutDuration,
          drillBuilder.mocksterTimeoutUnit,
          drillBuilder.forDownend,
          this
      ) ;
    }
  }

  public static class ForFakeUpend extends ForUpend {

    public ForFakeUpend( final DrillBuilder drillBuilder ) {
      super(
          drillBuilder,
          ConnectorDrill.ForUpend.Kind.FAKE,
          ConnectorDrill.AutomaticLifecycle.BOTH
      ) ;
    }

    public DrillBuilder done() {
      return new DrillBuilder(
          false,
          drillBuilder.proxy,
          drillBuilder.portForwarder,
          drillBuilder.webSocketFrameSizer,
          drillBuilder.timeBoundary,
          drillBuilder.mocksterTimeoutDuration,
          drillBuilder.mocksterTimeoutUnit,
          drillBuilder.forDownend,
          this
      ) ;

    }

  }
}
