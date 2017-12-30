package com.otcdlink.chiron.toolbox.internet;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public final class LocalAddressTools {
  private LocalAddressTools() { }

  public static final InetAddress LOCAL_ADDRESS ;
  public static final Hostname LOCALHOST_HOSTNAME;

  static {
    try {
      // InetAddress#getByAddress performs no DNS lookup so we won't get disturbed.
      LOCAL_ADDRESS = InetAddress.getByAddress( new byte[]{ 127, 0, 0, 1 } ) ;
    } catch( final UnknownHostException e ) {
      throw new RuntimeException( "Failed to create an InetAddress for localhost", e ) ;
    }
    try {
      LOCALHOST_HOSTNAME = Hostname.parse( LOCAL_ADDRESS.getHostAddress() ) ;
    } catch( final Hostname.ParseException e ) {
      throw new RuntimeException( "Could not create " + Hostname.class.getSimpleName() +
          " using '" + LOCAL_ADDRESS + "'", e ) ;
    }
  }

  public static InetSocketAddress localSocketAddress( final int port ) {
    return new InetSocketAddress( LOCAL_ADDRESS, port ) ;
  }

  private static final Pattern LOCALHOST_NAME_PATTERN = Pattern.compile( "^localhost$" ) ;

  private static final Pattern LOCALHOST_IPV4_PATTERN =
      Pattern.compile( "^127(?:\\.[0-9]+){0,2}\\.[0-9]+$" ) ;

  private static final Pattern LOCALHOST_IPV6_PATTERN = Pattern.compile( "^(?:0*:)*?:?0*1$" ) ;

  /**
   * http://stackoverflow.com/a/8426365/1923328
   */
  private static final Pattern ANY_LOCALHOST_ADDRESS_PATTERN = Pattern.compile(
      LOCALHOST_NAME_PATTERN.pattern() + "|" +
      LOCALHOST_IPV4_PATTERN.pattern() + "|" +
      LOCALHOST_IPV6_PATTERN.pattern()
  ) ;

  public static boolean isLocalhost( final Hostname hostname ) {
    return isLocalhost( hostname.asString() ) ;
  }

  public static boolean isLocalhost( final String hostname ) {
    return ANY_LOCALHOST_ADDRESS_PATTERN.matcher( hostname ).matches() ;
  }

}
