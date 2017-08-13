package com.otcdlink.chiron.toolbox.internet;

/**
 * @deprecated use {@link SchemeHostPort.Scheme}
 */
public class InternetHostNameTools {

  private static int defaultPortForSchemeUnsafe( final String scheme ) {
    switch( scheme ) {
      case "http" :
        return  80 ;
      case "https" :
        return  443 ;
      default :
        return -2 ; // Don't return the same value as HttpHost's default port.
    }
  }
  public static int defaultPortForScheme( final String scheme ) throws HostAccessFormatException {

    final int port = defaultPortForSchemeUnsafe( scheme ) ;
    if( port < 0 ) {
      throw new HostAccessFormatException( "Unsupported scheme: " + scheme ) ;
    } else {
      return port ;
    }
  }

}
