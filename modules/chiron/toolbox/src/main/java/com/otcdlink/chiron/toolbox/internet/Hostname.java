package com.otcdlink.chiron.toolbox.internet;

import com.google.common.base.Splitter;
import com.otcdlink.chiron.toolbox.SafeSystemProperty;
import com.otcdlink.chiron.toolbox.StringWrapper;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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


  /**
   * Return computer's host name, resolved using a mix of strategies.
   * If all of them fail, returns {@link #LOCALHOST}.
   */
  public static Hostname computerHostname() {
    try {
      return THIS_HOSTNAME.get() ;
    } catch( InterruptedException | ExecutionException e ) {
      return LOCALHOST ;
    }
  }

  private static final Future< Hostname > THIS_HOSTNAME ;

  static {
    final ExecutorService executorService = Executors.newSingleThreadExecutor(
        ExecutorTools.newThreadFactory( "resolve-hostname" ) ) ;
    THIS_HOSTNAME = executorService.submit( () -> {
      try {
        return Hostname.parse( InetAddress.getLocalHost().getHostName() ) ;
      } catch( final ParseException | UnknownHostException fail1 ) {
        final String hostnameAsString = osSpecificHostnameResolution() ;
        if( hostnameAsString != null ) {
          try {
            return Hostname.parse( hostnameAsString ) ;
          } catch( final ParseException ignore ) { }
        }
        return LOCALHOST ;
      }
    } ) ;
  }

  /**
   * https://stackoverflow.com/a/28043703
   *
   * @return {@code null} if everything failed.
   */
  private static String osSpecificHostnameResolution() {
    final String operatingSystemName = SafeSystemProperty.Standard.OS_NAME.value.toLowerCase() ;

    try {
      if( operatingSystemName.contains( "win" ) ) {
        final String computernameInEnv = System.getenv( "COMPUTERNAME" ) ;
        if( computernameInEnv != null ) {
          return computernameInEnv;
        }
        final String hostnameExec = execReadToString( "hostname" ) ;
        if( hostnameExec != null ) {
          return hostnameExec ;
        }
      } else {
        final boolean unixOrLinux = operatingSystemName.contains( "nix" ) ||
            operatingSystemName.contains( "nux" ) ;
        if( unixOrLinux ) {
          final String hostnameInEnv = System.getenv( "HOSTNAME" ) ;
          if( hostnameInEnv != null ) {
            return hostnameInEnv;
          }
        }
        if( unixOrLinux || SafeSystemProperty.Standard.OperatingSystem.isMacOsX() ) {
          final String hostnameExec = execReadToString( "hostname" ) ;
          if( hostnameExec != null ) {
            return hostnameExec ;
          }
        }
        if( unixOrLinux ) {
          final String etcHostname = execReadToString( "cat /etc/hostname" ) ;
          if( etcHostname != null ) {
            return etcHostname ;
          }
        }
      }
    } catch( final IOException ignore ) { }
    return null ;
  }

  public static String execReadToString( final String execCommand ) throws IOException {
    final Process proc = Runtime.getRuntime().exec( execCommand ) ;
    try( InputStream stream = proc.getInputStream() ) {
      try( Scanner s = new Scanner( stream ).useDelimiter( "\\A" ) ) {
        return s.hasNext() ? s.next() : "" ;
      }
    }
  }


  public static void main( final String... arguments ) {
    System.out.println( computerHostname().asString() ) ;
  }
}
