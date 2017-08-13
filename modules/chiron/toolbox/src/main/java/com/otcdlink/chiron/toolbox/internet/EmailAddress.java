package com.otcdlink.chiron.toolbox.internet;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.otcdlink.chiron.toolbox.ComparatorTools;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * TODO: apply patterns for parsing, etc.
 */
public class EmailAddress {

  private final String string ;

  public EmailAddress( final String string ) throws EmailAddressFormatException {
    if( ! InternetAddressValidator.isEmailAddressValid( string ) ) {
      throw new EmailAddressFormatException( string ) ;
    }
    this.string = string ;
  }

  public String asString() {
    return string ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + asString() + "}" ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final EmailAddress that = ( EmailAddress ) other ;

    if( ! string.equals( that.string ) ) {
      return false ;
    }

    return true ;
  }

  @Override
  public int hashCode() {
    return string.hashCode() ;
  }

  public static final Comparator< EmailAddress > COMPARATOR =
      new ComparatorTools.WithNull< EmailAddress >()
  {
    @Override
    protected int compareNoNulls(
        final EmailAddress first,
        final EmailAddress emailAddress2
    ) {
      return first.asString()
          .compareTo( emailAddress2.asString() ) ;
    }
  } ;

  public static EmailAddress parseQuiet( final String string ) {
    try {
      return new EmailAddress( string ) ;
    } catch( final EmailAddressFormatException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public static String join(
      final Iterable< EmailAddress > emailAddresses
  ) {
    return Joiner.on( ',' ).join( Iterables.transform(
        emailAddresses,
        new Function<EmailAddress, String>() {
          @Nullable
          @Override
          public String apply( final EmailAddress input ) {
            return input.asString() ;
          }
        }
    ) ) ;
  }

  public static ImmutableSet< EmailAddress > parseMultipleAddresses( final String string )
      throws EmailAddressFormatException
  {
    final Iterable< String > split = Splitter.on( ',' ).split( string ) ;
    final ImmutableSet.Builder< EmailAddress > builder = ImmutableSet.builder() ;
    for( final String s : split ) {
      builder.add( new EmailAddress( s ) ) ;
    }
    return builder.build() ;
  }
}
