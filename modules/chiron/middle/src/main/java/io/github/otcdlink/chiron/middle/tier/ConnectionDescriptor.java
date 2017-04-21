package io.github.otcdlink.chiron.middle.tier;

import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.AUTHENTICATED;
import static io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.TIMEBOUNDARY_PING_INTERVAL_MS;
import static io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.TIMEBOUNDARY_PING_TIMEOUT_MS;
import static io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.TIMEBOUNDARY_PONG_TIMEOUT_MS;
import static io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.TIMEBOUNDARY_RECONNECT_DELAY_MS_LOWERBOUND;
import static io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.TIMEBOUNDARY_RECONNECT_DELAY_MS_UPPERBOUND;
import static io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.TIMEBOUNDARY_SESSION_INACTIVITY_MAXIMUM_MS;
import static io.github.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.VERSION;

/**
 * Describes WebSocket connection characteristics as decided by Upend.
 * Fields of a {@link ConnectionDescriptor} object get serialized in a WebSocket handshake response.
 */
public final class ConnectionDescriptor {

  public final String upendVersion ;

  public final boolean authenticationRequired ;

  public final TimeBoundary.ForAll timeBoundary ;

  public ConnectionDescriptor(
      final String upendVersion,
      final boolean authenticationRequired,
      final TimeBoundary.ForAll timeBoundary
  ) {
    this.upendVersion = checkNotNull( upendVersion ) ;
    this.authenticationRequired = authenticationRequired ;
    this.timeBoundary = checkNotNull( timeBoundary ) ;
  }

  public HttpHeaders httpHeaders() {
    final DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders() ;

    httpHeaders.add( VERSION, upendVersion == null ? "" : upendVersion ) ;

    httpHeaders.add( AUTHENTICATED, Boolean.toString( authenticationRequired ) ) ;

    httpHeaders.add( TIMEBOUNDARY_PING_INTERVAL_MS, timeBoundary.pingIntervalMs ) ;

    httpHeaders.add( TIMEBOUNDARY_PONG_TIMEOUT_MS, timeBoundary.pongTimeoutMs ) ;

    httpHeaders.add( TIMEBOUNDARY_RECONNECT_DELAY_MS_LOWERBOUND,
        timeBoundary.reconnectDelayRangeMs.lowerBound ) ;

    httpHeaders.add( TIMEBOUNDARY_RECONNECT_DELAY_MS_UPPERBOUND,
        timeBoundary.reconnectDelayRangeMs.upperBound ) ;

    httpHeaders.add( TIMEBOUNDARY_SESSION_INACTIVITY_MAXIMUM_MS,
        timeBoundary.sessionInactivityMaximumMs ) ;

    httpHeaders.add( TIMEBOUNDARY_PING_TIMEOUT_MS, timeBoundary.pingTimeoutMs ) ;

    return httpHeaders ;
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + '{' +
        "upendVersion=" + upendVersion + ';' +
        "authenticated=" + authenticationRequired +
    '}' ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final ConnectionDescriptor that = ( ConnectionDescriptor ) other ;

    if( authenticationRequired != that.authenticationRequired ) {
      return false ;
    }
    return upendVersion.equals( that.upendVersion ) ;

  }

  @Override
  public int hashCode() {
    int result = upendVersion.hashCode() ;
    result = 31 * result + ( authenticationRequired ? 1 : 0 ) ;
    return result ;
  }


  interface HttpHeaderKeys {

    String VERSION = "Chiron-Version" ;

    String AUTHENTICATED = "Chiron-Authenticated" ;

    String TIMEBOUNDARY_PING_INTERVAL_MS = "Chiron-TimeBoundary-PingInterval-Ms" ;

    String TIMEBOUNDARY_PONG_TIMEOUT_MS = "Chiron-TimeBoundary-PongTimeout-Ms" ;

    String TIMEBOUNDARY_RECONNECT_DELAY_MS_LOWERBOUND =
        "Chiron-TimeBoundary-ReconnectDelayMsLowerbound" ;

    String TIMEBOUNDARY_RECONNECT_DELAY_MS_UPPERBOUND =
        "Chiron-TimeBoundary-ReconnectDelayMsUpperbound" ;

    String TIMEBOUNDARY_SESSION_INACTIVITY_MAXIMUM_MS =
        "Chiron-TimeBoundary-SessionInactivityMaximumMs" ;

    String TIMEBOUNDARY_PING_TIMEOUT_MS = "Chiron-TimeBoundary-PingTimeoutMs" ;

  }

  public static ConnectionDescriptor from( final HttpHeaders httpHeaders ) {
    final String version = httpHeaders.get( VERSION ) ;
    final String authenticated = httpHeaders.get( AUTHENTICATED ) ;
    final TimeBoundary.ForAll timeBoundary = parseTimeBoundary( httpHeaders ) ;
    return new ConnectionDescriptor(
        version == null ? "" : version,
        authenticated != null && Boolean.parseBoolean( authenticated ),
        timeBoundary
    ) ;
  }

  private static TimeBoundary.ForAll parseTimeBoundary( HttpHeaders httpHeaders ) {
    return TimeBoundary.Builder.createNew()
        .pingInterval( parseInt( httpHeaders, TIMEBOUNDARY_PING_INTERVAL_MS ) )
        .pongTimeoutOnDownend( parseInt( httpHeaders, TIMEBOUNDARY_PONG_TIMEOUT_MS ) )
        .reconnectDelay(
            parseInt( httpHeaders, TIMEBOUNDARY_RECONNECT_DELAY_MS_LOWERBOUND ),
            parseInt( httpHeaders, TIMEBOUNDARY_RECONNECT_DELAY_MS_UPPERBOUND )
        )
        .pingTimeoutOnUpend( parseInt( httpHeaders, TIMEBOUNDARY_PING_TIMEOUT_MS ) )
        .maximumSessionInactivity(
            parseInt( httpHeaders, TIMEBOUNDARY_SESSION_INACTIVITY_MAXIMUM_MS ) )
        .build()
    ;
  }

  private static int parseInt( HttpHeaders httpHeaders, String headerName ) {
    final String value = httpHeaders.get( headerName ) ;
    try {
      return Integer.parseInt( value ) ;
    } catch( NumberFormatException e ) {
      throw new ParseException(
          "Could not parse value '" + value + " for header '" + headerName + "'", e ) ;
    }
  }

  public static class ParseException extends RuntimeException {
    public ParseException( String message, Throwable cause ) {
      super( message, cause ) ;
    }
  }

}
