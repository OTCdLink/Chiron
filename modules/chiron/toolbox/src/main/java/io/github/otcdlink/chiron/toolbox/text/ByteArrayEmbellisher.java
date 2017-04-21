package io.github.otcdlink.chiron.toolbox.text;

import static com.google.common.base.Preconditions.checkArgument;

public final class ByteArrayEmbellisher {
  public final String humanReadableString ;
  public final String hexadecimalString ;

  public ByteArrayEmbellisher( final byte[] bytes ) {
    this( bytes, -1 ) ;
  }

  public ByteArrayEmbellisher( final byte[] bytes, final int current ) {
    this( bytes, 0, bytes.length, current ) ;
  }

  public ByteArrayEmbellisher( final byte[] bytes, final int start, final int end ) {
    this( bytes, start, end, -1 ) ;
  }

  public ByteArrayEmbellisher(
      final byte[] bytes,
      final int start,
      final int end,
      final int current
  ) {
    checkArgument( start >= 0 ) ;
    checkArgument( end >= start ) ;
    final StringBuilder hexBuilder = new StringBuilder() ;
    final StringBuilder charBuilder = new StringBuilder() ;
    for( int i = start ; i < end ; i ++ ) {
      if( i == current ) {
        hexBuilder.append( "# " ) ;
        charBuilder.append( "  " ) ;
      }
      hexBuilder.append( String.format( "%02X", bytes[ i ] ) ) ;
      hexBuilder.append( ' ' ) ;
      charBuilder.append( ' ' ) ;
      charBuilder.append( TextTools.isAsciiPrintable( ( char ) bytes[ i ] )
          ? ( char ) bytes[ i ] : ' ' ) ;
      charBuilder.append( ' ' ) ;
    }
    humanReadableString = charBuilder.toString() ;
    hexadecimalString = hexBuilder.toString() ;
  }

  public static String byteToHumanReadable( final byte b ) {
    return "" + ( TextTools.isAsciiPrintable( ( char ) b ) ? "'" + ( char ) b + "' " : "" )
        + "$" + String.format( "%02X", b ) ;
  }
}
