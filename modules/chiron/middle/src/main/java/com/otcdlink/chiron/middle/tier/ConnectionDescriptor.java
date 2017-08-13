package com.otcdlink.chiron.middle.tier;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.AUTHENTICATED;
import static com.otcdlink.chiron.middle.tier.ConnectionDescriptor.HttpHeaderKeys.VERSION;

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

    final ImmutableMap< TimeBoundary.ForAll.Key, Integer > timeBoundaryAsMap =
        timeBoundary.asMap() ;
    for(
        final Map.Entry< TimeBoundary.ForAll.Key, Integer > entry : timeBoundaryAsMap.entrySet()
    ) {
      httpHeaders.add( TIMEBOUNDARY_KEY_MAP.get( entry.getKey() ), entry.getValue() ) ;
    }
    return httpHeaders ;
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + '{' +
        "upendVersion=" + upendVersion + ';' +
        "authenticated=" + authenticationRequired + ';' +
        "timeBoundary=" + timeBoundary +
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


  public interface HttpHeaderKeys {
    String VERSION = "Chiron-Version" ;
    String AUTHENTICATED = "Chiron-Authenticated" ;
  }

  private static final String TIMEBOUNDARY_HEADER_PREFIX = "Chiron-TimeBoundary-" ;


  private static final ImmutableBiMap< TimeBoundary.ForAll.Key, String > TIMEBOUNDARY_KEY_MAP ;
  static {
    final ImmutableBiMap.Builder< TimeBoundary.ForAll.Key, String > builder =
        ImmutableBiMap.builder() ;
    for( final TimeBoundary.ForAll.Key key : TimeBoundary.ForAll.Key.values() ) {
      final String upperCamel =
          CaseFormat.UPPER_UNDERSCORE.to( CaseFormat.UPPER_CAMEL, key.name() ) ;
      builder.put( key, TIMEBOUNDARY_HEADER_PREFIX + upperCamel ) ;
    }
    TIMEBOUNDARY_KEY_MAP = builder.build() ;
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
    return TimeBoundary.ForAll.parse( key -> extractInteger( httpHeaders, key ) ) ;
  }

  private static Integer extractInteger(
      HttpHeaders httpHeaders,
      TimeBoundary.ForAll.Key timeBoundaryKey
  ) {
    final String stringKey = TIMEBOUNDARY_KEY_MAP.get( timeBoundaryKey ) ;
    final String value = httpHeaders.get( stringKey ) ;
    if( value == null ) {
      throw new ParseException( "No value for " + stringKey + " in " + httpHeaders ) ;
    }
    try {
      return Integer.parseInt( value ) ;
    } catch( NumberFormatException e ) {
      throw new ParseException(
          "Could not parse value '" + value + "' for header '" + stringKey + "'", e ) ;
    }
  }

  public static class ParseException extends RuntimeException {
    ParseException( String message ) {
      super( message ) ;
    }

    ParseException( String message, Throwable cause ) {
      super( message, cause ) ;
    }
  }

}
