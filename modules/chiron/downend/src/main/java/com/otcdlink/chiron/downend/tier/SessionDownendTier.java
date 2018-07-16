package com.otcdlink.chiron.downend.tier;

import com.otcdlink.chiron.downend.SignonMaterializer;
import com.otcdlink.chiron.middle.session.SecondaryToken;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.session.SessionLifecycle;
import com.otcdlink.chiron.middle.session.SignonFailure;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Obtains a {@link SessionIdentifier}, interacting with {@link SignonMaterializer}.
 * <p>
 * This {@link ChannelHandler} is sharable because we reuse it across the {@link ChannelPipeline}s
 * created by reconnections.
 */
@ChannelHandler.Sharable
public class SessionDownendTier
    extends SimpleChannelInboundHandler<SessionLifecycle.Phase>
{
  private static final Logger LOGGER =
      LoggerFactory.getLogger( SessionDownendTier.class ) ;
  public interface Claim {

    void sessionValid() ;
    void signonCancelled() ;
  }

  private final Claim claim ;
  private final SignonMaterializer signonMaterializer ;

  private SecondaryToken lastSecondaryTokenReceived = null ;
  private SessionIdentifier sessionIdentifier = null ;


  public SessionDownendTier(
      final Claim claim,
      final SignonMaterializer signonMaterializer
  ) {
    this.signonMaterializer = checkNotNull( signonMaterializer ) ;
    this.claim = checkNotNull( claim ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + '{' + signonMaterializer + '}' ;
  }

  @Override
  public void handlerAdded( final ChannelHandlerContext channelHandlerContext ) throws Exception {
    LOGGER.debug( "Handler added for " + this + "." ) ;
    if( sessionIdentifier == null ) {
      requestCredential( channelHandlerContext ) ;
    } else {
      final SessionLifecycle.Resignon resignon =
          SessionLifecycle.Resignon.create( sessionIdentifier ) ;
      channelHandlerContext.writeAndFlush( resignon ) ;
    }
  }

  @Override
  protected void channelRead0(
      final ChannelHandlerContext channelHandlerContext,
      final SessionLifecycle.Phase phase
  ) throws Exception {
    LOGGER.debug( "Processing " + phase + " ..." ) ;
    switch( phase.kind() ) {
      case SIGNON_FAILED :
        final SessionLifecycle.SignonFailed signonFailed = ( SessionLifecycle.SignonFailed ) phase ;
        final SignonFailureNotice signonFailureNotice = signonFailed.signonFailureNotice() ;

        /** Don't whine if {@link SessionLifecycle.Resignon} failed. */
        if( signonFailureNotice.kind != SignonFailure.UNKNOWN_SESSION ) {
          signonMaterializer.setProblemMessage( signonFailureNotice ) ;
        }

        switch( signonFailureNotice.kind ) {

          case UNKNOWN_SESSION :
            sessionIdentifier = null ;
          case MISSING_CREDENTIAL :
          case INVALID_CREDENTIAL :
            requestCredential( channelHandlerContext ) ;
            break ;
          case MISSING_SECONDARY_CODE :
          case INVALID_SECONDARY_CODE :
            requestSecondaryCode(
                channelHandlerContext, lastSecondaryTokenReceived, signonFailureNotice ) ;
            break ;
          default :
            if( signonFailureNotice.kind.recoverable ) {
              signonMaterializer.setProblemMessage( signonFailureNotice ) ;
              requestCredential( channelHandlerContext ) ;
            } else {
              LOGGER.info( "Giving up, can't recover past " + phase + "." ) ;
              signonMaterializer.waitForCancellation( claim::signonCancelled ) ;
            }
        }
        break ;
      case PRIMARY_SIGNON_NEEDED :
        requestCredential( channelHandlerContext ) ;
        break ;
      case SECONDARY_SIGNON_NEEDED :
        final SessionLifecycle.SecondarySignonNeeded secondarySignonNeeded =
            ( SessionLifecycle.SecondarySignonNeeded ) phase ;
        lastSecondaryTokenReceived = secondarySignonNeeded.secondaryToken() ;
        requestSecondaryCode(
            channelHandlerContext,
            lastSecondaryTokenReceived,
            new SignonFailureNotice( SignonFailure.MISSING_SECONDARY_CODE )
        ) ;
        break ;
      case SESSION_VALID :
        sessionValid( ( SessionLifecycle.SessionValid ) phase ) ;
        break ;
//      case KICKOUT :
//        break ;
//      case TIMEOUT :
//        break ;
      default : throw new IllegalArgumentException( "Unsupported: " + phase ) ;
    }
  }

  private void requestCredential( final ChannelHandlerContext channelHandlerContext ) {
    signonMaterializer.readCredential( credential -> {
      LOGGER.debug( "Finally obtained " + credential + " from " + signonMaterializer + "." ) ;
      if( credential == null ) {
        signonMaterializer.done() ;
        claim.signonCancelled() ;
      } else {
        signonMaterializer.setProgressMessage( "Signing in …" ) ;
        final SessionLifecycle.PrimarySignon primarySignon = SessionLifecycle.PrimarySignon.create(
            credential.getLogin(), credential.getPassword() ) ;
        channelHandlerContext.writeAndFlush( primarySignon ) ;
      }
    } ) ;
  }

  private void requestSecondaryCode(
      final ChannelHandlerContext channelHandlerContext,
      final SecondaryToken secondaryToken,
      final SignonFailureNotice signonFailureNotice
  ) {
    signonMaterializer.setProblemMessage( signonFailureNotice ) ;

    signonMaterializer.readSecondaryCode( secondaryCode -> {
      LOGGER.debug( "Finally obtained " + secondaryCode + " from " + signonMaterializer + "." ) ;
      if( secondaryCode == null ) {
        signonMaterializer.done() ;
        claim.signonCancelled() ;
      } else {
        signonMaterializer.setProgressMessage( "Signing in …" ) ;
        final SessionLifecycle.SecondarySignon secondarySignon =
            SessionLifecycle.SecondarySignon.create( secondaryToken, secondaryCode ) ;
        channelHandlerContext.writeAndFlush( secondarySignon ) ;
      }
    } ) ;
  }

  private void sessionValid( final SessionLifecycle.SessionValid phase ) {
    if( ! phase.sessionIdentifier().equals( this.sessionIdentifier ) ) {
      /** We are not in a {@link SessionLifecycle.Resignon}. */
      signonMaterializer.done() ;
      this.sessionIdentifier = phase.sessionIdentifier() ;
    }
    claim.sessionValid() ;
  }

  public void clearSessionIdentifier() {
    sessionIdentifier = null ;
  }

}
