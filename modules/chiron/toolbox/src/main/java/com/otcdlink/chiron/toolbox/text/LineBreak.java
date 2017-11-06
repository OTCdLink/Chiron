package com.otcdlink.chiron.toolbox.text;

import com.google.common.base.Charsets;
import com.otcdlink.chiron.toolbox.SafeSystemProperty;

import java.util.Arrays;

public enum LineBreak {

  CRLF_WINDOWS( 13, 10 ),

  /**
   * As said in
   * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2.2" >HTTP&nbsp;1.1 specification</a>.
   */
  CRLF_HTTP11( 13, 10 ),

  CR_UNIX( 10 ),
  ;

  public static final LineBreak DEFAULT = CR_UNIX ;

  public static final LineBreak SYSTEM ;


  static {
    final String lineSeparator = SafeSystemProperty.Standard.LINE_SEPARATOR.value ;
    LineBreak match = null ;
    for( LineBreak lineBreak : values() ) {
      if( Arrays.equals( lineSeparator.getBytes( Charsets.UTF_8 ), lineBreak.asByteArray() ) ) {
        match = lineBreak ;
        break ;
      }
    }
    if( match == null ) {
      throw new IllegalStateException( "Unknown line separator" ) ;
    } else {
      SYSTEM = match ;
    }
  }

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