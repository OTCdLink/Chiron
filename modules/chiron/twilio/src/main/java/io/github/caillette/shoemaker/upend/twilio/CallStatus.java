package io.github.caillette.shoemaker.upend.twilio;

import com.google.common.collect.ImmutableSet;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.upend.session.twilio.TwilioAuthenticationFailure;
import io.github.otcdlink.chiron.upend.twilio.TokenPack;
import io.github.otcdlink.chiron.upend.twilio.TwilioStatus;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Holds the state spread across different calls cascaded to different parties.
 */
public class CallStatus {
  public final DateTime startTime ;
  public final TokenPack tokenPack ;
  private TwilioStatus twilioStatus = null ;
  private AuthenticationProgress authenticationProgress = AuthenticationProgress.STARTED ;

  CallStatus( final TokenPack tokenPack, final DateTime startTime ) {
    this.startTime = checkNotNull( startTime ) ;
    this.tokenPack = checkNotNull( tokenPack ) ;
  }

  public void broken() {
    this.authenticationProgress = AuthenticationProgress.BROKEN ;
  }

  public void setTwilioStatus( final TwilioStatus twilioStatus ) {
    this.twilioStatus = checkNotNull( twilioStatus ) ;
    if( STATUSES_FOR_COMPLETION.contains( twilioStatus ) ) {
      this.authenticationProgress = AuthenticationProgress.CALL_COMPLETE ;
    }
  }

  private static final ImmutableSet< TwilioStatus > STATUSES_FOR_COMPLETION = ImmutableSet.of(
      TwilioStatus.CALL_COMPLETED,
      TwilioStatus.CALL_IN_PROGRESS,
      TwilioStatus.TWIML_REQUESTED // Probably the only one we really need.
  ) ;

  public TwilioAuthenticationFailure computeFailure() {
    return authenticationProgress.getValidity( twilioStatus ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + '{' +
        "startTime=" + startTime + ';' +
        "tokenPack=" + tokenPack + ';' +
        "twilioStatus=" + twilioStatus +
        '}'
    ;
  }
}
