package com.otcdlink.chiron.upend.tier;

import com.otcdlink.chiron.middle.ChannelTools;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.otcdlink.chiron.upend.UpendConnector;
import com.otcdlink.chiron.upend.session.OutwardSessionSupervisor;
import com.otcdlink.chiron.upend.session.SessionSupervisor;
import com.otcdlink.chiron.upend.session.SessionSupervisor.ChannelCloser;
import com.otcdlink.chiron.upend.session.SignableUser;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Wires {@link SessionLifecycle.Phase}s with {@link OutwardSessionSupervisor}.
 *
 * @param <ADDRESS> Needed for tests with {@link io.netty.channel.embedded.EmbeddedChannel}
 *     for which {@link Channel#remoteAddress()} returns a special flavor of
 *     {@link java.net.SocketAddress}. For normal use, should be {@link java.net.InetAddress}.
 */
public class SessionEnforcerTier< ADDRESS >
    extends ChannelInboundHandlerAdapter
{

  private static final Logger LOGGER = LoggerFactory.getLogger( SessionEnforcerTier.class ) ;

  private final OutwardSessionSupervisor< Channel, ADDRESS > sessionSupervisor ;

  private final UpendConnector.ChannelRegistrar channelRegistrar ;


  private final Function< Channel, ADDRESS > addressExtractor ;

  /**
   * Default constructor, supposes {@link ADDRESS} to be {@code InetSocketAddress}.
   */
  public SessionEnforcerTier(
      final OutwardSessionSupervisor< Channel, ADDRESS > sessionSupervisor,
      final UpendConnector.ChannelRegistrar channelRegistrar
  ) {
    this(
        sessionSupervisor,
        channelRegistrar,
        channel -> ( ADDRESS ) ( ( InetSocketAddress ) channel.remoteAddress() ).getAddress()
    ) ;
  }

  /**
   * For tests only.
   */
  SessionEnforcerTier(
      final OutwardSessionSupervisor< Channel, ADDRESS > sessionSupervisor,
      final UpendConnector.ChannelRegistrar channelRegistrar,
      final Function< Channel, ADDRESS > addressExtractor
  ) {
    this.sessionSupervisor = checkNotNull( sessionSupervisor ) ;
    this.channelRegistrar = checkNotNull( channelRegistrar ) ;
    this.addressExtractor = checkNotNull( addressExtractor ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + "{}" ;
  }

  @Override
  public void channelActive( final ChannelHandlerContext channelHandlerContext ) {
    channelHandlerContext.attr( UpendChannelTools.SHOULD_NOTIFIY_ON_CHANNEL_INACTIVE ).set( true ) ;
  }

  /**
   * Netty calls this method when the {@link Channel} is closing for whatever reason.
   * This can be a connection loss, a call to {@link EventLoop#shutdownGracefully()}, or an
   * explicit call to {@link Channel#close()} from a {@link ChannelCloser} called by
   * {@link SessionSupervisor}.
   *
   * <h1>Notify once only</h1>
   * <p>
   * Thid method may call {@link SessionSupervisor#closed(Object, SessionIdentifier, boolean)}, which in turns calls
   * {@link ChannelCloser#close(Object)}, which looks like a cross recursion, but setting the
   * {@link UpendChannelTools#SHOULD_NOTIFIY_ON_CHANNEL_INACTIVE} attribute avoids this,
   * so finally we don't have to care about what initiated the closing, since we have the
   * guarantee that appropriate notifications will happen once.
   * <p>
   * Netty enforces thread-safety by calling {@link #channelInactive(ChannelHandlerContext)} from
   * the {@link Channel#eventLoop()}.
   *
   * <h1>Sidenote</h1>
   * <p>
   * Note: we don't care about
   * {@link io.netty.channel.ChannelOutboundHandler#close(ChannelHandlerContext, ChannelPromise)}
   * which only makes sense in a write (outbound) context.
   */
  @Override
  public void channelInactive( final ChannelHandlerContext channelHandlerContext )
      throws Exception
  {
    final Boolean shouldNotify = channelHandlerContext.channel()
        .attr( UpendChannelTools.SHOULD_NOTIFIY_ON_CHANNEL_INACTIVE ).getAndSet( false ) ;
    if( shouldNotify ) {
      final SessionIdentifier formerSessionIdentifier =
          ChannelTools.sessionIdentifier( channelHandlerContext ) ;
      if( formerSessionIdentifier != null ) {
        final Channel channel = channelHandlerContext.channel() ;
        sessionSupervisor.closed( channel, formerSessionIdentifier, false ) ;
      }
    }
    channelRegistrar.unregisterChannel( channelHandlerContext.channel() ) ;
    super.channelInactive( channelHandlerContext ) ;
  }

  @Override
  public void channelRead(
      final ChannelHandlerContext channelHandlerContext,
      final Object inbound
  ) throws Exception {
    if( inbound instanceof WebSocketFrame || inbound instanceof FullHttpRequest ) {
      sendUpwardIfSessionValid( channelHandlerContext, inbound ) ;
      return ;
    } else if( inbound instanceof SessionLifecycle.Phase ) {
      final SessionLifecycle.Kind kind = ( ( SessionLifecycle.Phase ) inbound ).kind() ;
      switch( kind ) {
        case PRIMARY_SIGNON :
          primarySignon(
              channelHandlerContext,
              ( SessionLifecycle.PrimarySignon ) inbound
          ) ;
          return ;
        case SECONDARY_SIGNON :
          secondarySignon( channelHandlerContext, ( SessionLifecycle.SecondarySignon ) inbound ) ;
          return ;
        case RESIGNON :
          resignon(
              channelHandlerContext,
              ( SessionLifecycle.Resignon ) inbound
          ) ;
          return ;
        case SIGNOFF :
          signoff( channelHandlerContext ) ;
          return ;
        case TIMEOUT :
          timeout( channelHandlerContext ) ;
          return ;
        default :
          break ;
      }
    }
    throw new UnsupportedOperationException( "Unsupported: " + inbound ) ;
  }




// ================
// SessionLifecycle
// ================

  private void resignon(
      final ChannelHandlerContext channelHandlerContext,
      final SessionLifecycle.Resignon resignon
  ) {
    final SessionIdentifier sessionIdentifier = resignon.sessionIdentifier() ;
    final Channel channel = channelHandlerContext.channel() ;
    final SessionSupervisor.ReuseCallback reuseCallback = signonFailureNotice -> {
          if( signonFailureNotice == null ) {
            new SignonCallback( channelHandlerContext ).sessionAttributed(
            sessionIdentifier ) ;
          } else {
            new SignonCallback( channelHandlerContext ).signonResult( signonFailureNotice ) ;
          }
    } ;
    sessionSupervisor.tryReuse( sessionIdentifier, channel, reuseCallback ) ;
  }

  private void secondarySignon(
      final ChannelHandlerContext channelHandlerContext,
      final SessionLifecycle.SecondarySignon secondarySignon
  ) {
    sessionSupervisor.attemptSecondarySignon(
        channelHandlerContext.channel(),
        addressExtractor.apply( channelHandlerContext.channel() ),
        secondarySignon.secondaryToken(),
        secondarySignon.secondaryCode(),
        new SignonCallback( channelHandlerContext )
    ) ;
  }

  private void primarySignon(
      final ChannelHandlerContext channelHandlerContext,
      final SessionLifecycle.PrimarySignon primarySignon
  ) {
    sessionSupervisor.attemptPrimarySignon(
        primarySignon.login(),
        primarySignon.password(),
        channelHandlerContext.channel(),
        addressExtractor.apply( channelHandlerContext.channel() ),
        new SignonCallback( channelHandlerContext )
    ) ;

  }

  private void signoff( final ChannelHandlerContext channelHandlerContext ) {
    final SessionIdentifier sessionIdentifier =
        channelRegistrar.unregisterChannel( channelHandlerContext.channel() ) ;
    if(sessionIdentifier != null ) {
      sessionSupervisor.closed( channelHandlerContext.channel(), sessionIdentifier, true ) ;
    }
  }

  private void timeout( final ChannelHandlerContext channelHandlerContext ) {
    final SessionIdentifier sessionIdentifier =
        channelRegistrar.unregisterChannel( channelHandlerContext.channel() ) ;
    sessionSupervisor.closed( channelHandlerContext.channel(), sessionIdentifier, false ) ;
    channelHandlerContext.close() ;
  }

  private class SignonCallback
      implements
      SessionSupervisor.PrimarySignonAttemptCallback,
      SessionSupervisor.SecondarySignonAttemptCallback
  {
    /**
     * Documentation says the {@link ChannelHandlerContext} remains the same between calls to
     * {@link ChannelHandler#handlerAdded(io.netty.channel.ChannelHandlerContext)} and
     * {@link ChannelHandler#handlerRemoved(ChannelHandlerContext)}.
     */
    private final ChannelHandlerContext channelHandlerContext ;

    private SignonCallback( final ChannelHandlerContext channelHandlerContext ) {
      this.channelHandlerContext = checkNotNull( channelHandlerContext ) ;
    }


    @Override
    public void sessionAttributed(
        final SessionIdentifier sessionIdentifier
    ) {
      checkNotNull( sessionIdentifier ) ;
      LOGGER.debug( "Associated " + sessionIdentifier + " to " +
          channelHandlerContext.channel() + "." ) ;

      // Channel could have get closed the time Session attribution occured elsewhere.
      if( channelHandlerContext.channel().isActive() ) {
        final SessionLifecycle.Phase signonPhase ;
        channelRegistrar.registerChannel( sessionIdentifier, channelHandlerContext.channel() ) ;
        signonPhase = SessionLifecycle.SessionValid.create( sessionIdentifier ) ;
        sendDownward(
            channelHandlerContext,
            signonPhase
        ) ;
        LOGGER.debug( "Sent " + signonPhase + " to " + channelHandlerContext.channel() + "." ) ;
      }
    }

    @Override
    public void signonResult( final SignonFailureNotice signonFailureNotice ) {
      checkNotNull( signonFailureNotice,
          "Internal error, signonFailureNotice should not be null here" ) ;
      if( ! channelHandlerContext.isRemoved() ) {
        sendDownward( channelHandlerContext,
            SessionLifecycle.SignonFailed.create( signonFailureNotice ) ) ;
      }
    }

    @Override
    public void needSecondarySignon(
        final SignableUser userIdentity,
        final SecondaryToken secondaryToken
    ) {
      if( ! channelHandlerContext.isRemoved() ) {
        sendDownward( channelHandlerContext,
            SessionLifecycle.SecondarySignonNeeded.create( secondaryToken ) ) ;
      }
    }
  }


// ==================================
// Check session validity and forward
// ==================================

  private void sendUpwardIfSessionValid(
      final ChannelHandlerContext channelHandlerContext,
      final Object inbound
  ) {
    if( ChannelTools.hasSession( channelHandlerContext ) ) {
      NettyTools.touchMaybe( inbound, "Sending upward because there is a valid session" ) ;
      channelHandlerContext.fireChannelRead( inbound ) ;
    } else {
      LOGGER.warn( "Preventing " + inbound +
          " to go upward because there is no session for " + this + "." ) ;
      NettyTools.releaseMaybe( inbound );
    }
  }

  private static void sendDownward(
      final ChannelHandlerContext channelHandlerContext,
      final SessionLifecycle.Phase signonPhase
  ) {
    channelHandlerContext.writeAndFlush( signonPhase )/*.addListener( future -> {
      if( ! future.isSuccess() ) {
        LOGGER.error( "Failed to send " + signonPhase + " in " + channelHandlerContext + ".",
            future.cause() ) ;
      }
    } )*/ ;
  }

/*
  @Override
  public void exceptionCaught(
      final ChannelHandlerContext channelHandlerContext,
      final Throwable cause
  ) {
    LOGGER.error( "Caught exception in " + channelHandlerContext + ".", cause ) ;
  }
*/
}
