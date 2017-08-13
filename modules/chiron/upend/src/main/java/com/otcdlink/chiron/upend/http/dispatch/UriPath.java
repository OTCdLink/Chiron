package com.otcdlink.chiron.upend.http.dispatch;

import com.otcdlink.chiron.toolbox.ComparatorTools;
import com.otcdlink.chiron.toolbox.UrxTools;

import java.util.Comparator;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A pre-parsed representation of an absolute URI path zero or more segments, as defined by
 * <a href="https://tools.ietf.org/html/rfc3986#page-22" >RFC 3986</a>.
 * There are some simplifications/normalizations.
 * <ul>
 *   <li>
 *     Every path starts with {@code "/"}.
 *   </li><li>
 *     A path can not contain {@code "//"}.
 *   </li><li>
 *     An {@link UriPath} only contains segments. Within a simple hierarchical filesystem,
 *     this would be like having nested directories on a depth of zero or more but no plain file.
 *   </li><li> 
 *     The root is normalized to {@code "/"} (here sometimes nicknamed by "slash").
 *     When given no segments, {@link #fullPath} and {@link #fullPathWithTrailingSlash}
 *     both return {@code "/"}.
 *   </li>  
 * </ul>
 * <p>
 * Since calling code sometimes expects the path to contain a trailing slash and sometimes not,
 * the two cases are handled explicitely across various methods.
 */
public final class UriPath {

  public static final UriPath ROOT = new UriPath() ;

  public final String fullPathWithTrailingSlash ;
  public final String fullPath ;

  private static String pathString( final UriPath ancestor, final String segment ) {
    verify( segment ) ;
    final String cleanSegment = trimSlashes( segment ) ;
    final String ancestorAsString = ancestor == null ? "" : ancestor.fullPathWithTrailingSlash ;
    if( cleanSegment.isEmpty() ) {
      return ancestorAsString ;
    } else {
      return ancestorAsString + cleanSegment ;
    }
  }

  private UriPath() {
    this.fullPath = "/" ;
    this.fullPathWithTrailingSlash = "/" ;
  }

  private UriPath( final String fullPath ) {
    checkPathWellFormed( fullPath ) ;
    if( fullPath.length() > 1 ) {
      checkArgument(
          ! fullPath.endsWith( "/" ),
          "Can't end with '/' unless it represents " + ROOT + ": '" + fullPath + "'"
      ) ;
    }
    this.fullPath = fullPath ;
    this.fullPathWithTrailingSlash = "/".equals( fullPath ) ? fullPath : fullPath + "/" ;
  }

  static void checkPathWellFormed( final String fullPath ) {
    checkArgument( UrxTools.Parsing.PATH_PATTERN.matcher( fullPath ).matches(),
        "Not a valid URI path: '" + fullPath + "'" ) ;
  }

  private UriPath( final UriPath ancestor, final String segment ) {
    this( pathString( ancestor, segment ) ) ;
  }

  private static final Pattern DOUBLE_SLASH = Pattern.compile( "//" ) ;
  private static final Pattern ALLOWED_CHARACTERS = Pattern.compile( "[a-zA-Z0-9_/\\-]*" ) ;

  private static void verify( final String segment ) {
    if( segment != null ) {
      checkArgument( ! DOUBLE_SLASH.matcher( segment ).find(),
          "Should not contain '" + DOUBLE_SLASH.pattern() + "': '" + segment + "'" ) ;
      checkArgument( ALLOWED_CHARACTERS.matcher( segment ).matches(),
          "Should contain only '" + ALLOWED_CHARACTERS.pattern() + "': '" + segment + "'" ) ;
    }
  }

  private static String trimSlashes( final String segment ) {
    if( segment == null ) {
      return "" ;
    } else if( segment.isEmpty() ) {
      return segment ;
    } else {
      final int start = segment.startsWith( "/" ) ? 1 : 0 ;
      final int end = segment.endsWith( "/" ) ? segment.length() - 1 : segment.length() ;
      final String trimmed = start <= end && start >= 0 ?
          segment.substring( start, end ) :
          ""
      ;
      return trimmed ;
    }
  }

  public static UriPath from( final String... segments ) {
    UriPath uriPath = ROOT ;
    for( final String segment : segments ) {
      uriPath = append( uriPath, segment ) ;
    }
    return uriPath ;
  }

  /**
   * A flavor of {@link #append(String)} that supports {@code null} parent.
   */
  public static UriPath append( final UriPath parent, final String segment ) {
    return new UriPath( parent, segment ) ;
  }

  public UriPath append( final String segment ) {
    return append( this, segment ) ;
  }

  public MatchKind pathMatch( final String otherPath ) {
    checkNotNull( otherPath ) ;
    if( isRoot() && otherPath.isEmpty() ) {
      return MatchKind.TOTAL_MATCH ;
    } else if( isRoot() && "/".equals( otherPath ) ) {
      return MatchKind.TOTAL_MATCH_WITH_TRAILING_SLASH ;
    } else if( isRoot() ) {
      return MatchKind.RADIX_MATCH ;
    } else if( fullPath.equals( otherPath ) ) {
      return MatchKind.TOTAL_MATCH ;
    } else if( fullPathWithTrailingSlash.equals( otherPath ) ) {
      return MatchKind.TOTAL_MATCH_WITH_TRAILING_SLASH ;
    } else if( startsWithSegments( otherPath, fullPathWithTrailingSlash ) ) {
      return MatchKind.RADIX_MATCH_WITH_TRAILING_SLASH ;
    } else if( startsWithSegments( otherPath, fullPath ) ) {
      return MatchKind.RADIX_MATCH ;
    }
    return MatchKind.NO_MATCH ;
  }

  /**
   * Return relative path basing on {@link #fullPathWithTrailingSlash} (which means the
   * result will probably start with a {@code "/"}).
   */
  public String relativizeFromUnslashedPath( final String derived ) {
    return relativize( derived, false ) ;
  }

  /**
   * Return relative path basing on {@link #fullPath} (which means the result won't start
   * with a {@code "/"}).
   */
  public String relativizeFromSlashedPath( final String derived ) {
    return relativize( derived, true ) ;
  }

  public String relativize( final String derived, final boolean useTrailingSlash ) {
    final String normal = derived.startsWith( "/" ) ? derived : "/" + derived ;
    checkPathWellFormed( normal ) ;
    final String radix = useTrailingSlash ? fullPathWithTrailingSlash : fullPath ;
    if( startsWithSegments( normal, radix ) ) {
      final int radixLength = radix.length() ;
      final String relativised = normal.substring( radixLength ) ;
      return relativised ;
    } else {
      return null ;
    }
  }

  public boolean isRoot() {
    return isRoot( fullPathWithTrailingSlash ) ;
  }

  public static boolean isRoot( final String completePath ) {
    return completePath.isEmpty() || "/".equals( completePath ) ;
  }

  public enum MatchKind {
    NO_MATCH( false ),

    /**
     * After normalisation (adding a leading "/" if none), evaluated URI starts with every
     * character of {@link #fullPath}.
     */
    RADIX_MATCH( true ),

    /**
     * After normalization (adding a leading "/" if none), evaluated URI starts with every
     * character in {@link #fullPathWithTrailingSlash}.
     */
    RADIX_MATCH_WITH_TRAILING_SLASH( true ),

    /**
     * After normalisation (adding a leading "/" if none), evaluated URI
     * contains every character of {@link #fullPath}.
     */
    TOTAL_MATCH( true ),

    /**
     * After normalisation (adding a leading "/" if none), evaluated URI
     * contains every character of {@link #fullPathWithTrailingSlash}.
     */
    TOTAL_MATCH_WITH_TRAILING_SLASH( true ),
    ;

    public final boolean segmentsMatched ;

    MatchKind( final boolean segmentsMatched ) {
      this.segmentsMatched = segmentsMatched ;
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + fullPath + "}" ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final UriPath that = ( UriPath ) other ;

    return COMPARATOR.compare( this, that ) == 0 ;
  }

  @Override
  public int hashCode() {
    return fullPath.hashCode() ;
  }

  public static final Comparator< UriPath > COMPARATOR = new ComparatorTools.WithNull< UriPath> () {
    @Override
    protected int compareNoNulls( final UriPath first, final UriPath second ) {
      return first.fullPath.compareTo( second.fullPath ) ;
    }
  } ;

  /**
   * Verifies that a path starts with given one, with the match occuring on entire segments.
   */
  static boolean startsWithSegments( final String path, final String candidateRadix ) {
    checkNotNull( path ) ;
    checkNotNull( candidateRadix ) ;
    if( candidateRadix.isEmpty() ) {
      return true ;
    }
    if( path.startsWith( candidateRadix ) ) {
      if( path.length() == candidateRadix.length() ) {
        return true ;
      } else {
        if( candidateRadix.endsWith( "/" ) ) {
          return true ;
        } else {
          if( '/' == path.charAt( candidateRadix.length() ) ) {
            return true ;
          }

        }
      }


    }
    return false ;
  }

}
