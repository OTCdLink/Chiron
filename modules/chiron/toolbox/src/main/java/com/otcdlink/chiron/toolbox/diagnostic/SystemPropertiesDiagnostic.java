package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

public class SystemPropertiesDiagnostic extends BaseDiagnostic {

  public static final ImmutableSet< String > PATH_PROPERTY_NAMES = ImmutableSet.of(
      "java.class.path",
      "java.library.path",
      "sun.boot.class.path",
      "java.endorsed.dirs",
      "sun.boot.library.path",
      "java.ext.dirs"
  ) ;

  public SystemPropertiesDiagnostic() {
    super( properties() ) ;
  }

  protected static ImmutableMultimap< String, String > properties() {
    final ImmutableMultimap.Builder< String, String > builder = ImmutableMultimap.builder() ;
    final Properties systemProperties = System.getProperties() ;
    final Enumeration propertyNames = systemProperties.propertyNames() ;
    final List< String > propertyNameList = Lists.newArrayList() ;
    while( propertyNames.hasMoreElements() ) {
      propertyNameList.add( ( String ) propertyNames.nextElement() ) ;
    }
    for( final String name : Ordering.natural().sortedCopy( propertyNameList ) ) {
      final String propertyValue = systemProperties.getProperty( name ) ;
      if( ! PATH_PROPERTY_NAMES.contains( name ) ) {
        builder.put( name, ( LINE_SEPARATOR_PROPERTY_NAME.equals( name )
                ? to8byteHex( propertyValue.toCharArray() )
                : sanitize( name, propertyValue )
            )
        ) ;
      }
    }
    return builder.build() ;
  }

  private static String sanitize( final String name, final String value ) {
    final int startOfParameters = value == null ? -1 : value.indexOf( " " ) ;
    if( "sun.java.command".equals( name ) && startOfParameters > 0 ) {
      return "" + value.substring( 0, startOfParameters )
          + " <hiding parameters because of passwords>" ;
    } else {
      return value ;
    }
  }


  private static String to8byteHex( final char[] chars ) {
    final StringBuilder builder = new StringBuilder() ;
    for( final char c : chars ) {
      builder.append( "0x" );
      builder.append( to8byteHex( c ).toUpperCase() );
      builder.append( ' ' ) ;
    }
    return builder.toString() ;
  }

  /**
   * Converts a character to the two-character hexadecimal representation of its most
   * significative one-byte representation.
   * Author: Jon A. Cruz http://www.thescripts.com/forum/thread15875.html
   *
   * @return a two-byte string.
   */
  public static String to8byteHex( final char character ) {
    return Integer.toHexString( 0x100 | ( 0x0ff & ( int ) character ) ).substring( 1 ) ;
  }

  private static final String LINE_SEPARATOR_PROPERTY_NAME = "line.separator" ;

}
