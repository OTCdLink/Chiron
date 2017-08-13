package com.otcdlink.chiron.toolbox.text;

import com.google.common.base.Charsets;

public enum LineBreak {

  /**
   * FIXME: should be 13,10.
   */
  CRLF_WINDOWS( 10, 13 ),

  /**
   * As said in
   * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2.2" >HTTP&nbsp;1.1 specification</a>.
   */
  CRLF_HTTP11( 13, 10 ),

  CR_UNIX( 10 ),
  ;
  public static final LineBreak DEFAULT = CR_UNIX ;

  public final String asString ;

  LineBreak( final int... bytes ) {
    final byte[] realBytes = new byte[ bytes.length ];
    for( int counter = 0 ; counter < bytes.length ; counter++ ) {
      realBytes[ counter ] = ( byte ) bytes[ counter ] ;
    }
    this.asString = new String( realBytes ) ;
  }

  public void append( final StringBuilder stringBuilder ) {
    stringBuilder.append( asString ) ;
  }

  public byte[] asByteArray() {
    return asString.getBytes( Charsets.US_ASCII ) ;
  }
}