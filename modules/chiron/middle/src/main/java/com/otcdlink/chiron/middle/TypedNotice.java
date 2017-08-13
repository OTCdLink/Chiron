package com.otcdlink.chiron.middle;


import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.toolbox.BadEnumOrdinalException;
import com.otcdlink.chiron.toolbox.ComparatorTools;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Describes why Upend did not process some {@link Command}.
 * This can be seen as a very verbose implementation of Rust's enums.
 */
public class TypedNotice< KIND extends Enum< KIND > & EnumeratedMessageKind > {

  /**
   * Not null.
   */
  public final KIND kind ;

  /**
   * Not null.
   * May be {@code ==} to {@link #kind}'s {@link EnumeratedMessageKind#description()}.
   */
  public final String message ;

  public TypedNotice( final KIND kind ) {
    this.kind = checkNotNull( kind ) ;
    this.message = kind.description() ;
  }

  public TypedNotice( final KIND kind, final String message ) {
    this.kind = checkNotNull( kind ) ;
    this.message = checkNotNull( message ) ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{"
        + kind.name()
        + ( hasMessage() ? ";" + message : "" )
        + "}"
    ;
  }

  @SuppressWarnings( "StringEquality" )
  public boolean hasMessage() {
    return message != kind.description() ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final TypedNotice typedNotice = ( TypedNotice ) other ;

    if( kind != typedNotice.kind ) {
      return false ;
    }
    if( ! message.equals( typedNotice.message ) ) {
      return false ;
    }

    return true ;
  }

  @Override
  public int hashCode() {
    int result = kind.hashCode() ;
    result = 31 * result + message.hashCode() ;
    return result ;
  }



  public static <
      NOTICE extends TypedNotice< KIND >,
      KIND extends Enum< KIND > & EnumeratedMessageKind
  > Comparator< NOTICE > comparator() {
    final Comparator< KIND > kindComparator = new ComparatorTools.ForEnum<>() ;
    return new ComparatorTools.WithNull< NOTICE >() {
      @Override
      protected int compareNoNulls(
          final NOTICE first,
          final NOTICE second
      ) {
        final int signonFailureComparison
            = kindComparator.compare( first.kind, second.kind ) ;
        if( signonFailureComparison == 0 ) {
          final int messageComparison
              = ComparatorTools.STRING_COMPARATOR.compare( first.message, second.message ) ;
          return messageComparison ;
        } else {
          return signonFailureComparison ;
        }
      }
    } ;
  }


  /**
   * Keeps together all specific code for deserializing a concrete
   * {@link TypedNotice}.
   */
  public interface DecodingKit<
      NOTICE extends TypedNotice< KIND >,
      KIND extends Enum< KIND > & EnumeratedMessageKind
  > {
    NOTICE create( KIND kind ) ;
    NOTICE create( KIND kind, String message ) ;

    KIND kind( int ordinal ) throws BadEnumOrdinalException;

    /**
     * Hook to customize thrown exception.
     *
     * @deprecated {@link DecodeException} is fine.
     */
    < ANY > ANY throwException( Exception e ) throws DeserializationException, DecodeException;
  }


}
