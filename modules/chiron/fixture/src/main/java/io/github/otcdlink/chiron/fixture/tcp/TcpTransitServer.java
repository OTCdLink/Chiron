package io.github.otcdlink.chiron.fixture.tcp;

import io.github.otcdlink.chiron.fixture.tcp.http.HttpProxy;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.github.otcdlink.chiron.toolbox.netty.SocketServer;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Base contract for a server that forwards TCP connections to another {@link HostPort}.
 */
public interface TcpTransitServer extends SocketServer {


// ===============
// Other contracts
// ===============

  /**
   * Represents what's in contact with a {@link TcpTransitServer}.
   */
  enum Edge {
    INITIATOR,
    TARGET,
    ;

    Edge other() {
      final Edge[] all = values() ;
      return ordinal() == all.length - 1 ? all[ 0 ] : all[ ordinal() + 1 ] ;
    }

    private static final Edge[] cachedValues = values() ;

    public static void forEach( final Consumer< Edge > consumer ) {
      for( final Edge edge : cachedValues ) {
        consumer.accept( edge ) ;
      }
    }
  }

  enum State { STOPPED, STARTING, STARTED, STOPPING }

  interface PipelineConfigurator {

    interface Factory {
      PipelineConfigurator createNew( Logger logger ) ;

      Factory NULL_FACTORY = new Factory() {
        @Override
        public PipelineConfigurator createNew( final Logger Ø ) {
          return PipelineConfigurator.NULL ;
        }

        @Override
        public String toString() {
          return ToStringTools.getNiceClassName( this ) + "{NULL}" ;
        }
      } ;
    }


    /**
     * An {@link TcpTransitServer} calls this method exactly one time for each {@link Edge} so
     * a {@link PipelineConfigurator} can keep state common to the two {@link ChannelPipeline}s.
     */
    void configure(
        final InetSocketAddress targetAddress,
        final Edge edge,
        final ChannelPipeline channelPipeline
    ) ;


    PipelineConfigurator NULL = new PipelineConfigurator() {
      @Override
      public void configure(
          final InetSocketAddress targetAddress,
          final Edge edge,
          final ChannelPipeline channelPipeline
      ) { }

      @Override
      public String toString() {
        return ToStringTools.getNiceClassName( this ) + "{NULL}" ;
      }
    } ;
  }

  /**
   * Tells about what is received, and forwarded or not.
   */
  interface Watcher {

    /**
     * Return {@code true} to receive a copy of forwardable payload.
     * This method should always return the same value during the object's life.
     *
     * @return {@code true} to get a non-null copy.
     */
    default boolean bytesInDetail() {
      return false ;
    }

    enum Progress {

      /**
       * Some (unprocessed) bytes did reach Ingress entry. This should be exactly what
       * {@link Edge#INITIATOR} sent.
       * Associated {@link Transfer.Detail#payload()} may be non-null if {@link #bytesInDetail()}
       * returned {@code true}.
       */
      RECEIVED( false ),

      /**
       * Some content at Ingress exit gets delayed, because {@link HttpProxy#lag(int)} is set
       * to a value greater than zero, or there are previously queued values and we respect queuing
       * order so delay occurs anyways.
       * Associated {@link Transfer.Detail#payload()} may be non-null if the payload is exactly
       * the same as passed along with {@link #RECEIVED}, and if {@link #bytesInDetail()} returned
       * {@code true}.
       */
      DELAYED( true ),

      /**
       * Some bytes did reach Egress exit. They may be the result of internal processings
       * (because of a {@link PipelineConfigurator}), or the same content as notified with
       * previous {@link #RECEIVED}.
       * Associated {@link Transfer.Detail#payload()} may be non-null if {@link #bytesInDetail()}
       * returned {@code true}.
       */
      EMITTED( false ),
      ;

      public final boolean supportsDelay ;

      Progress( final boolean supportsDelay ) {
        this.supportsDelay = supportsDelay ;
      }
    }

    void onTransfer( Transfer.Event transferEvent ) ;

    Watcher NULL = Ø -> { } ;

    Watcher LOGGING = ( transferEvent ) -> LoggerFactory.getLogger( TcpTransitServer.class )
        .debug( transferEvent.toString() ) ;


  }

  /**
   * Represents what's happening to a payload of data going through {@link TcpTransitServer}.
   */
  class Transfer {

    public interface Detail {

      Route forwardingRoute() ;

      Edge origin() ;

      /**
       * @return a non-{@code null} value for {@link Watcher.Progress#DELAYED}.
       */
      Integer delayMs() ;

      /**
       * Return the number of transferred bytes, if available.
       */
      Integer payloadSize() ;

      /**
       * Returns a copy of transferred bytes, if available.
       * @return {@code null} if {@link Watcher#bytesInDetail()} was {@code false}.
       */
      byte[] payload() ;

    }

    public interface WritableToPipeline {
      ChannelFuture writeAndFlushTo( final ChannelHandlerContext channelHandlerContext ) ;
    }

    public interface Event extends Detail {
      default boolean hasPayload() {
        return payload() != null ;
      }

      Watcher.Progress progress() ;
    }

    public interface WritableEvent extends Event, Transfer.WritableToPipeline { }

    private interface TransferDetail extends Detail { }


    private static abstract class AbstractTransfer implements TransferDetail {
      private final Route forwardingRoute ;
      private final Edge edge;
      private final Object byteArrayOrSize ;

      protected AbstractTransfer(
          final Route forwardingRoute,
          final Edge edge,
          final boolean byteArrayDetail,
          final Object message
      ) {
        this(
            TcpTransitTools.asByteArrayOrSize( byteArrayDetail, message ),
            forwardingRoute,
            edge
        ) ;
      }

      private AbstractTransfer(
          final Object byteArrayOrSize,
          final Route forwardingRoute,
          final Edge edge
      ) {
        this.forwardingRoute = checkNotNull( forwardingRoute ) ;
        this.edge = checkNotNull( edge ) ;
        checkArgument(
            byteArrayOrSize == null ||
            byteArrayOrSize instanceof Integer ||
            byteArrayOrSize instanceof byte[]
        ) ;
        if( byteArrayOrSize instanceof Integer ) {
          checkArgument( ( ( Integer ) byteArrayOrSize ) >= 0 ) ;
        }
        this.byteArrayOrSize = byteArrayOrSize ;
      }

      @Override
      public final Route forwardingRoute() {
        return forwardingRoute ;
      }

      @Override
      public final Edge origin() {
        return edge;
      }

      public final byte[] payloadNoCopy() {
        return byteArrayOrSize instanceof byte[] ? ( byte[] ) byteArrayOrSize : null ;
      }

      @Override
      public final byte[] payload() {
        final byte[] payloadNoCopy = payloadNoCopy() ;
        if( payloadNoCopy == null ) {
          return null ;
        } else {
          return Arrays.copyOf( payloadNoCopy, payloadNoCopy.length ) ;
        }
      }

      @Override
      public final Integer payloadSize() {
        if( byteArrayOrSize instanceof Integer ) {
          return ( Integer ) byteArrayOrSize ;
        } else if( byteArrayOrSize instanceof byte[] ) {
          return ( ( byte[] ) byteArrayOrSize ).length ;
        }
        return null ;
      }

      @Override
      public final String toString() {
        return toString( this, toStringMore() ) ;
      }

      protected String toStringMore() {
        return null ;
      }

      protected static String toString( final TransferDetail transfer ) {
        return toString( transfer, null ) ;
      }

      protected static String toString( final TransferDetail transfer, final String specific ) {
        final StringBuilder builder = new StringBuilder() ;
        builder.append( ToStringTools.getNiceClassName( transfer ) ).append( '{' ) ;
        builder.append( "origin=" ).append( transfer.origin().name() ).append( ';' ) ;
        if( transfer instanceof Event ) {
          final Event event = ( Event ) transfer ;
          builder.append( "progress=" ).append( event.progress().name() ).append( ';' ) ;
        }
        if( transfer.delayMs() != null && transfer.delayMs() > 0 ) {
          builder.append( "delayMs=" ).append( transfer.delayMs() ).append( ';' ) ;
        }
        if( transfer.payloadSize() != null ) {
          builder.append( "payloadSize=" ).append( transfer.payloadSize() ).append( ';' ) ;
        }
        if( specific != null ) {
          builder.append( specific ) ;
        }
        builder.append( "route=" ).append( transfer.forwardingRoute() ) ;
        builder.append( '}' ) ;
        return builder.toString() ;
      }

      @Override
      public Integer delayMs() {
        return null ;
      }

    }

    public static final class IngressEntry
        extends AbstractTransfer
        implements Event
    {
      public IngressEntry(
          final Route forwardingRoute,
          final Edge edge,
          final boolean byteArrayDetail,
          final Object message
      ) {
        super( forwardingRoute, edge, byteArrayDetail, message ) ;
      }

      @Override
      public Watcher.Progress progress() {
        return Watcher.Progress.RECEIVED ;
      }
    }

    public static final class IngressExit
        extends AbstractTransfer
        implements WritableToPipeline
    {

      private final Object pipelineMessage ;

      public IngressExit(
          final Route forwardingRoute,
          final Edge edge,
          final boolean byteArrayDetail,
          final Object pipelineMessage
      ) {
        super( forwardingRoute, edge, byteArrayDetail, pipelineMessage ) ;
        this.pipelineMessage = checkNotNull( pipelineMessage ) ;
      }

      @Override
      public ChannelFuture writeAndFlushTo( final ChannelHandlerContext channelHandlerContext ) {
        return channelHandlerContext.writeAndFlush( pipelineMessage ) ;
      }
    }

    public static final class EgressExit
        extends AbstractTransfer
      implements Event, Transfer.WritableToPipeline
    {
      private final Object pipelineMessage ;

      public EgressExit(
          final Route forwardingRoute,
          final Edge onset,
          final boolean byteArrayDetail,
          final Object message
      ) {
        super( forwardingRoute, onset, byteArrayDetail, message ) ;
        this.pipelineMessage = checkNotNull( message ) ;
      }

      @Override
      public ChannelFuture writeAndFlushTo( final ChannelHandlerContext channelHandlerContext ) {
        return channelHandlerContext.writeAndFlush( pipelineMessage ) ;
      }

      @Override
      public Watcher.Progress progress() {
        return Watcher.Progress.EMITTED ;
      }
    }



    /**
     * At the beginning of egress Pipeline, after scheduling.
     */
    public static final class Delayed< O >
        extends TcpTransitTools.DelegatingScheduledFuture< O >
        implements TransferDetail, Transfer.WritableEvent
    {
      private final IngressExit ingressExit ;
      private final Integer delayMs ;

      public Delayed(
          final ScheduledFuture< O > delegate,
          final IngressExit ingressExit,
          final Integer delayMs
      ) {
        super( delegate ) ;
        this.ingressExit = checkNotNull( ingressExit ) ;
        this.delayMs = checkNotNull( delayMs ) ;
      }

      @Override
      public Route forwardingRoute() {
        return ingressExit.forwardingRoute() ;
      }

      @Override
      public Edge origin() {
        return ingressExit.origin() ;
      }

      @Override
      public Integer delayMs() {
        return delayMs ;
      }

      @Override
      public Integer payloadSize() {
        return ingressExit.payloadSize() ;
      }

      @Override
      public byte[] payload() {
        return ingressExit.payload() ;
      }

      @Override
      public Watcher.Progress progress() {
        return Watcher.Progress.DELAYED ;
      }

      @Override
      public String toString() {
        return AbstractTransfer.toString( this ) ;
      }

      @Override
      public ChannelFuture writeAndFlushTo( final ChannelHandlerContext channelHandlerContext ) {
        return channelHandlerContext.writeAndFlush( ingressExit.pipelineMessage ) ;
      }
    }



  }
}
