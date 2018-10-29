package com.otcdlink.chiron.wire;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.html.HtmlEscapers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Streaming primitives for fast character escaping/unescaping in XML.
 * {@link SimpleUnescaper} allocates <em>at most</em> one {@code char[]} for buffering
 * the escape code being read.
 */
public final class XmlEscaping {

  public static final char DEFAULT_OPENER = '&';
  public static final char DEFAULT_CLOSER = ';';

  public interface Transformer {

    boolean needsTransformation( final CharSequence charSequence ) ;

    void transform( final CharSequence charSequence, final Appendable charAppendable )
        throws IOException ;
  }

  /**
   * Only 4 characters are mandatory to escape in an XML attribute:
   * https://stackoverflow.com/a/1091953/1923328
   *
   * There is a comment in {@link HtmlEscapers} code telling us that {@code apos}
   * is not defined in HTML 4.01 but we only aim for XML here.
   */
  private static final ImmutableBiMap< Character, String > XML_ATTRIBUTE_ESCAPE_TABLE =
      ImmutableBiMap.< Character, String >builder()
          .put( '<', "lt" )
          .put( '\'', "apos" )
          .put( '"', "quot" )
          .put( '&', "amp" )
          .build()
  ;

  public static final char OPENING_ACCOLADE = '{' ;
  public static final char CLOSING_ACCOLADE = '}' ;
  private static final ImmutableBiMap< Character, String > ACCOLADE_ESCAPE_TABLE =
      ImmutableBiMap.< Character, String >builder()
          .put( OPENING_ACCOLADE, "oa" )
          .put( CLOSING_ACCOLADE, "ca" )
          .build()
  ;

  private static abstract class Scaper {
    protected final char codeOpener ;
    protected final char codeCloser ;

    protected Scaper() {
      this( DEFAULT_OPENER, DEFAULT_CLOSER ) ;
    }

    protected Scaper( final char codeOpener, final char codeCloser ) {
      this.codeOpener = codeOpener ;
      this.codeCloser = codeCloser ;
    }

    protected final void checkCodeSanity( final String code ) {
      checkDoesNotContain( code, this.codeOpener ) ;
      checkDoesNotContain( code, this.codeCloser ) ;

      /** {@link #CHAR_ARRAY_COMPARATOR_NO_ZERO} ends comparison at the first zero
       * which just means the code buffer is not full. So we don't want a zero in the
       * code, that would mess up this comparison. */
      checkDoesNotContain( code, '\0' ) ;
    }

    private static void checkDoesNotContain( String code, char delimiter ) {
      checkArgument( code.indexOf( delimiter ) < 0,
          "Code '" + code + "' must not contain '" + delimiter + "'" ) ;
    }

    protected boolean transformationNeeded( final CharSequence charSequence ) {
      for( int i = 0 ; i < charSequence.length() ; i ++ ) {
        if( charSequence.charAt( i ) == codeOpener ) {
          return true ;
        }
      }
      return false ;
    }
  }

  public static final class SimpleEscaper extends Scaper implements Transformer {

    /**
     * The character to escape is the offset. A value of {@code null} means no escape.
     */
    private final String[] table ;

    private SimpleEscaper( final ImmutableBiMap< Character, String > table ) {
      this( DEFAULT_OPENER, DEFAULT_CLOSER, table ) ;
    }

    public SimpleEscaper(
        final char codeOpener,
        final char codeCloser,
        final ImmutableBiMap<Character, String> table
    ) {
      super( codeOpener, codeCloser ) ;
      int greatestChar = -1 ;
      for( final Map.Entry< Character, String > entry : table.entrySet() ) {
        greatestChar = Math.max( greatestChar, entry.getKey() ) ;
        checkCodeSanity( entry.getValue() ) ;
      }
      this.table = new String[ greatestChar < 0 ? 0 : greatestChar + 1 ] ;
      for( final Map.Entry< Character, String > entry : table.entrySet() ) {
        this.table[ entry.getKey() ] = entry.getValue() ;
      }
    }

    @Override
    public void transform( CharSequence charSequence, Appendable charAppendable )
        throws IOException
    {
      for( int i = 0 ; i < charSequence.length() ; i ++ ) {
        final char c = charSequence.charAt( i ) ;
        final String code = c < table.length ? table[ c ] : null ;
        if( code == null ) {
          charAppendable.append( c ) ;
        } else {
          charAppendable.append( codeOpener ) ;
          charAppendable.append( code ) ;
          charAppendable.append( codeCloser ) ;
        }
      }
    }

    @Override
    public boolean needsTransformation( CharSequence charSequence ) {
      return transformationNeeded( charSequence ) ;
    }
  }

  public static final class SimpleUnescaper extends Scaper implements Transformer {

    /**
     * Contains the escape codes to recognize as an array of {@code char[]}.
     * Having a {@code char[][]} allows comparing elements with the {@code codeBuffer} which is
     * a {@code char[]} using {@link Arrays#binarySearch(Object[], Object)} (which requires
     * a preliminary sort).
     * The offset of each contained {@code char[]} matches the offset of matching char
     * in {@link #charTable}.
     */
    private final char[][] codeTable ;

    /**
     * The offset of each {@code char} (which is a character to escape) matches the offset of
     * its escape code in {@link #codeTable}.
     */
    private final char[] charTable ;

    private final int longestStringInTable ;

    private SimpleUnescaper( final ImmutableBiMap< Character, String > table ) {
      this( DEFAULT_OPENER, DEFAULT_CLOSER, table ) ;
    }

    public SimpleUnescaper(
        final char codeOpener,
        final char codeCloser,
        final ImmutableBiMap< Character, String > table
    ) {
      super( codeOpener, codeCloser ) ;
      int longest = 0 ;
      int index = 0 ;
      codeTable = new char[ table.size() ][] ;
      charTable = new char[ table.size() ] ;
      for( final Map.Entry< Character, String > entry : table.entrySet() ) {
        longest = Math.max( longest, entry.getValue().length() ) ;
        codeTable[ index ++ ] = entry.getValue().toCharArray() ;
        checkCodeSanity( entry.getValue() ) ;
      }
      this.longestStringInTable = longest ;
      Arrays.sort( codeTable, CHAR_ARRAY_COMPARATOR_NO_ZERO ) ;
      for( index = 0 ; index < codeTable.length ; index ++ ) {
        charTable[ index ] = table.inverse().get( new String( codeTable[ index ] ) ) ;
      }
    }

    @Override
    public void transform( final CharSequence charSequence, final Appendable charAppendable )
        throws IOException
    {
      char[] codeBuffer = null ;
      int indexInCodeBuffer = -1 ;
      for( int i = 0 ; i < charSequence.length() ; i ++ ) {
        final char c = charSequence.charAt( i ) ;
        if( indexInCodeBuffer >= 0 ) {
          if( c == codeCloser ) {
            final int codeIndex = binarySearch( codeTable, codeBuffer ) ;
            if( codeIndex >= 0 ) {
              charAppendable.append( charTable[ codeIndex ] ) ;
            } else {
              throw throwEscapeCodeNotFound( new String( codeBuffer, 0, indexInCodeBuffer ) ) ;
            }
            Arrays.fill( codeBuffer, '\0' ) ;  // Not needed by the algorithm.
            indexInCodeBuffer = -1 ;
          } else {
            codeBuffer[ indexInCodeBuffer ++ ] = c ;
          }
        } else if( c == codeOpener ) {
          indexInCodeBuffer = 0 ;
          if( codeBuffer == null ) {
            codeBuffer = new char[ longestStringInTable ] ;
          }
        } else {
          charAppendable.append( c ) ;
        }
      }
      if( indexInCodeBuffer >= 0 ) {
        throw throwEscapeCodeNotFound( "Unterminated escape sequence '" +
            new String( codeBuffer, 0, indexInCodeBuffer ) + "'" ) ;
      }
    }

    @Override
    public boolean needsTransformation( CharSequence charSequence ) {
      return transformationNeeded( charSequence ) ;
    }

    private static IOException throwEscapeCodeNotFound( final String escapeCode )
        throws IOException
    {
      throw new IOException( "Escape code not found: '" + escapeCode + "'" ) ;
    }
  }

  static final Comparator< char[] > CHAR_ARRAY_COMPARATOR_NO_ZERO = ( a1, a2 ) -> {
    final int smallest = Math.min( a1.length, a2.length ) ;
    for( int i = 0 ; i < smallest ; i++ ) {
      char c1 = a1[ i ] ;
      char c2 = a2[ i ] ;
      if( c1 == 0 || c2 == 0 ) {
        // No zero in escape code so we hit the end.
        return 0 ;
      }
      if( c1 != c2 ) {
        return c1 - c2 ;
      }
    }
    return 0 ;
  } ;

  static int binarySearch( final char[][] array, final char[] lookedFor ) {
    return Arrays.binarySearch( array, lookedFor, CHAR_ARRAY_COMPARATOR_NO_ZERO ) ;
  }


  public static final Transformer ATTRIBUTE_ESCAPER = new SimpleEscaper(
      XML_ATTRIBUTE_ESCAPE_TABLE ) ;

  public static final Transformer ATTRIBUTE_UNESCAPER = new SimpleUnescaper(
      XML_ATTRIBUTE_ESCAPE_TABLE ) ;

  public static final Transformer ACCOLADE_ESCAPER = new SimpleEscaper(
      OPENING_ACCOLADE, CLOSING_ACCOLADE, ACCOLADE_ESCAPE_TABLE ) ;

  public static final Transformer ACCOLADE_UNESCAPER = new SimpleUnescaper(
      OPENING_ACCOLADE, CLOSING_ACCOLADE, ACCOLADE_ESCAPE_TABLE ) ;

  /**
   * Can be used safely to represent a {@code null} value, if the non-null value is escaped
   * with {@link #ACCOLADE_ESCAPER}.
   * We can get rid of this after that Aalto-xml supports disabling Entity Reference replacement.
   * https://github.com/FasterXML/aalto-xml/issues/65
   */
  public static final String MAGIC_NULL = OPENING_ACCOLADE + "null" + CLOSING_ACCOLADE ;

}
