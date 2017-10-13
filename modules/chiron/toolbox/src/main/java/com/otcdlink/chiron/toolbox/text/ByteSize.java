package com.otcdlink.chiron.toolbox.text;

import java.util.Locale;

public final class ByteSize {

  private ByteSize() { }

  /**
   * https://stackoverflow.com/a/3758880/1923328
   */
  public static String humanReadableByteCount( long bytes, boolean powerOf10 ) {
    int unit = powerOf10 ? 1000 : 1024 ;
    if( bytes < unit ) {
      return bytes + " B" ;
    }
    int exp = ( int ) ( Math.log( bytes ) / Math.log( unit ) ) ;
    String pre = ( powerOf10 ? "kMGTPE" : "KMGTPE" ).charAt( exp - 1 ) + ( powerOf10 ? "" : "i" ) ;
    return String.format( Locale.US, "%.1f %sB", bytes / Math.pow( unit, exp ), pre ) ;
  }
}
