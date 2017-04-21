package io.github.otcdlink.chiron.middle.tier;

import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.number.PositiveIntegerRange;

import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public interface TimeBoundary {

  /**
   * Sensible defaults for production use.
   */
  ForAll SAFE = Builder.createNew()
      .pingInterval( 1000 )
      .pongTimeoutOnDownend( 100 )  // Need this to support a lag of 500 ms induced by HttpProxy.
      .reconnectDelay( 1000, 3000 )
      .pingTimeoutOnUpend( 1500 )    // Need this to support a lag of 500 ms induced by HttpProxy.
      .maximumSessionInactivity( 10_000 )
      .build()
  ;
  /**
   * Timeout used by {@code DownendConnector} for initial connection, at the time
   * {@link ConnectionDescriptor} is not available.
   */
  int DEFAULT_CONNECT_TIMEOUT_MS = 1000 ;

  /**
   * Supports a lag of 500 ms (upstream <i>and</i> downstream).
   */
  ForAll LENIENT_500 = Builder.createNew()
      .pingInterval( 1000 )
      .pongTimeoutOnDownend( 2500 )  // Need this to support a lag of 500 ms induced by HttpProxy.
      .reconnectDelay( DEFAULT_CONNECT_TIMEOUT_MS, 3000 )
      .pingTimeoutOnUpend( 2500 )    // Need this to support a lag of 500 ms induced by HttpProxy.
      .maximumSessionInactivity( 10_000 )
      .build()
  ;
  /**
   * Sensible defaults for production use.
   */
  ForAll DEFAULT = LENIENT_500 ;

  /**
   * Describes the timeout-related value needed by {@code DownendConnector} before it can obtain a
   * {@link ForDownend}.
   */
  interface PrimingForDownend {

    int connectTimeoutMs() ;

    /**
     * Needed for creation of {@code TrackerCurator}.
     */
    int pongTimeoutMs() ;

    static PrimingForDownend createNew( int connectTimeoutMs, int pongTimeoutMs ) {
      checkArgument( connectTimeoutMs > 0 ) ;
      checkArgument( pongTimeoutMs > 0 ) ;
      return new PrimingForDownend( ) {
        @Override
        public int connectTimeoutMs() {
          return connectTimeoutMs ;
        }

        @Override
        public int pongTimeoutMs() {
          return pongTimeoutMs ;
        }

        @Override
        public String toString() {
          return PrimingForDownend.toString( this ) ;
        }
      } ;
    }

    static String toString( final PrimingForDownend primingTimeBoundary ) {
      return ToStringTools.getNiceClassName( primingTimeBoundary ) + "{" +
          "connectTimeoutMs=" + primingTimeBoundary.connectTimeoutMs() + ";" +
          "pongTimeoutMs=" + primingTimeBoundary.pongTimeoutMs() +
          "}"
      ;
    }

  }

  /**
   * Describes what Downend should see/use from a {@link ForAll}.
   */
  interface ForDownend extends PrimingForDownend {

    int pingIntervalMs() ;

    int reconnectDelayMs( final Random random ) ;


    static String toString( final ForDownend downendInitialTimeBoundary ) {
      return ToStringTools.getNiceClassName( downendInitialTimeBoundary ) + "{" +
          "connectTimeoutMs=" + downendInitialTimeBoundary.connectTimeoutMs() + ";" +
          "pingIntervalMs=" + downendInitialTimeBoundary.pingIntervalMs() + ";" +
          "pongTimeoutMs=" + downendInitialTimeBoundary.pongTimeoutMs() +
          "}"
      ;
    }

  }

  interface Builder {
    static PingIntervalStep createNew() {
      return new StepCombinator() ;
    }

    interface PingIntervalStep {
      PongTimeoutStep pingInterval( int ms ) ;
      default PongTimeoutStep pingIntervalNever() {
        return pingInterval( Integer.MAX_VALUE ) ;
      }
    }

    interface PongTimeoutStep {
      ReconnectDelayStep pongTimeoutOnDownend( int ms ) ;
      default ReconnectDelayStep pongTimeoutNever() {
        return pongTimeoutOnDownend( Integer.MAX_VALUE ) ;
      }
    }

    interface ReconnectDelayStep {
      default PingTimeoutStep reconnectImmediately() {
        return reconnectDelay( 0, 0 ) ;
      }
      default PingTimeoutStep reconnectNever() {
        return reconnectDelay( Integer.MAX_VALUE - 1, Integer.MAX_VALUE ) ;
      }
      default PingTimeoutStep reconnectDelayOrDouble( final int delayMs ) {
        return reconnectDelay( delayMs, delayMs * 2 ) ;
      }
      PingTimeoutStep reconnectDelay( int delayLowerBoundMs, final int delayHigherBoundMs ) ;
    }

    interface PingTimeoutStep {
      SessionInactivityStep pingTimeoutOnUpend( int ms ) ;
      default SessionInactivityStep pingTimeoutNever() {
        return pingTimeoutOnUpend( Integer.MAX_VALUE ) ;
      }
    }

    interface SessionInactivityStep {
      BuilderStep maximumSessionInactivity( int ms ) ;
      default BuilderStep sessionInactivityImmediate() {
        return maximumSessionInactivity( 0 ) ;
      }
      default BuilderStep sessionInactivityForever() {
        return maximumSessionInactivity( Integer.MAX_VALUE ) ;
      }
    }

    interface BuilderStep {
      ForAll build() ;
    }

    class StepCombinator
        implements
        PingIntervalStep,
        PongTimeoutStep,
        ReconnectDelayStep,
        PingTimeoutStep,
        SessionInactivityStep,
        BuilderStep
    {
      private static Integer checkPositive( final int i ) {
        checkArgument( i >= 0 ) ;
        return i ;
      }

      private Integer pingInterval = null ;
      private Integer pongTimeout = null ;
      private PositiveIntegerRange reconnectDelayRange = null ;
      private Integer pingTimeout = null ;
      private Integer maximumSessionInactivity = null ;


      @Override
      public PongTimeoutStep pingInterval( final int ms ) {
        pingInterval = checkPositive( ms ) ;
        return this ;
      }

      @Override
      public ReconnectDelayStep pongTimeoutOnDownend( final int ms ) {
        pongTimeout = checkPositive( ms ) ;
        return this ;
      }


      @Override
      public PingTimeoutStep reconnectDelay(
          final int delayLowerBoundMs,
          final int delayHigherBoundMs
      ) {
        checkArgument( delayHigherBoundMs >= 0 ) ;
        reconnectDelayRange = new PositiveIntegerRange( delayLowerBoundMs, delayHigherBoundMs ) ;
        return this ;
      }

      @Override
      public SessionInactivityStep pingTimeoutOnUpend( final int ms ) {
        pingTimeout = ms ;
        return this ;
      }

      @Override
      public BuilderStep maximumSessionInactivity( final int ms ) {
        maximumSessionInactivity = checkPositive( ms ) ;
        return this ;
      }

      @Override
      public ForAll build() {
        return new ForAll(
            pingInterval,
            pongTimeout,
            reconnectDelayRange,
            maximumSessionInactivity,
            pingTimeout
        ) ;
      }
    }

  }

  /**
   * Brings various delays together.
   * Inheriting from {@link PrimingForDownend} makes the tests easier to write when
   * we create a {@code UpendConnector.Setup} from a {@code DownendConnector.Setup}, test code
   * just has to cast {@link PrimingForDownend} into a {@link ForAll}.
   *
   */
  final class ForAll implements ForDownend {

    /**
     * How long the Downend should wait before sending next ping.
     */
    public final int pingIntervalMs ;

    @Override
    public int pingIntervalMs() {
      return pingIntervalMs ;
    }

    /**
     * Maximum duration the Downend can wait for after sending a ping and without receiving a pong,
     * before deciding the Upend got unreachable.
     */
    public final int pongTimeoutMs ;

    /**
     * Range made of minimum and maximum delay to wait for before attempting to reconnect ;
     * wait is randomized to avoid reconnection storm after a general network failure.
     */
    public final PositiveIntegerRange reconnectDelayRangeMs ;

    /**
     * Maximum duration the Upend can wait for receiving no ping from Downend, before deciding the
     * Upend got unreachable.
     */
    public final int pingTimeoutMs ;

    /**
     * Maximum lifetime of a session, after deciding the Downend was unreachable.
     */
    public final int sessionInactivityMaximumMs ;

    private ForAll(
        final int pingIntervalMs,
        final int pongTimeoutMs,
        final PositiveIntegerRange reconnectDelayMs,
        final int sessionInactivityMaximumMs,
        final int pingTimeoutMs
    ) {
      checkArgument( pingIntervalMs > 0 ) ;
      this.pingIntervalMs = pingIntervalMs ;
      checkArgument( pongTimeoutMs >= 0 ) ;
      this.pongTimeoutMs = pongTimeoutMs ;

      this.reconnectDelayRangeMs = checkNotNull( reconnectDelayMs ) ;

      checkArgument( sessionInactivityMaximumMs >= 0 ) ;
      this.sessionInactivityMaximumMs = sessionInactivityMaximumMs ;

      this.pingTimeoutMs = pingTimeoutMs ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + '{' +
          "pingIntervalMs=" + pingIntervalMs + ";" +
          "pongTimeoutMs=" + pongTimeoutMs + ";" +
          "reconnectDelayRangeMs=[" + reconnectDelayRangeMs.lowerBound + "," +
          reconnectDelayRangeMs.upperBound + "];" +
          "pingTimeoutMs=" + pingTimeoutMs + ";" +
          "sessionInactivityMaximumMs=" + sessionInactivityMaximumMs +
          '}'
      ;
    }

    public int reconnectDelayMs( final Random random ) {
      return reconnectDelayRangeMs.random( random ) ;
    }

    @Override
    public int connectTimeoutMs() {
      return reconnectDelayRangeMs.lowerBound ;
    }

    @Override
    public int pongTimeoutMs() {
      return pongTimeoutMs ;
    }


  }
}
