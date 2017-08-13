package com.otcdlink.chiron.toolbox.internet;

import com.google.common.base.Splitter;
import com.otcdlink.chiron.toolbox.StringWrapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Encapsulates a host name, with the guarantee that it is a valid single machine name,
 * or a valid domain name. Also works for localhost.
 * This class doesn't call {@link InetAddress} so it doesn't mess with DNS cache and so on.
 *
 * TODO: apply {@link HostPort}'s coding conventions.
 */
public class Hostname extends StringWrapper< Hostname > {

  private Hostname() {
    this( "localhost" ) ;
  }

  private Hostname( final String name ) {
    this( name, true ) ;
  }

  /*package*/ Hostname( final String name, final boolean validate ) {
    super( name ) ;
    if( validate ) {
      checkArgument( InternetAddressValidator.isHostnameValid( name ) ) ;
    }
  }

  public static Hostname parse( final String string ) throws ParseException {
    final Hostname hostname = parseOrNull( string ) ;
    if( hostname == null ) {
      throw new ParseException( string ) ;
    }
    return hostname ;
  }

  public static class ParseException extends RuntimeException {
    public ParseException( final String parsed ) {
      super( "Bad format: '" + parsed + "'" ) ;
    }
  }

  public static Hostname parseOrNull( final String string ) {
    if( InternetAddressValidator.isHostnameValid( string ) ) {
      return new Hostname( string ) ;
    } else {
      return null ;
    }
  }

  public HostPort hostPort( final int port ) {
    return HostPort.create( this, port ) ;
  }

  public String asString() {
    return wrapped ;
  }

  private static final String BYTE_PATTERN_FRAGMENT = "25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]" ;

  private static final Pattern IPV4_ADDRESS_PATTERN = Pattern.compile(
      BYTE_PATTERN_FRAGMENT + "\\." +
      BYTE_PATTERN_FRAGMENT + "\\." +
      BYTE_PATTERN_FRAGMENT + "\\." +
      BYTE_PATTERN_FRAGMENT
  ) ;

  public InetAddress asInetAddress() throws UnknownHostException {
    final Matcher matcher = IPV4_ADDRESS_PATTERN.matcher( wrapped ) ;
    if( matcher.matches() ) {
      final Iterator< String > split = Splitter.on( '.' ).split( wrapped ).iterator() ;
      final byte[] bytes = {
          Byte.parseByte( split.next() ),
          Byte.parseByte( split.next() ),
          Byte.parseByte( split.next() ),
          Byte.parseByte( split.next() )
      } ;
      return InetAddress.getByAddress( bytes ) ;
    } else {
      // TODO: IPv6
      return InetAddress.getByName( wrapped ) ;
    }
  }

  public static final Hostname LOCALHOST = new Hostname() ;
}
