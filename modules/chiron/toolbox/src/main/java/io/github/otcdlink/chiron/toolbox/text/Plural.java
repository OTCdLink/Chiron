package io.github.otcdlink.chiron.toolbox.text;

public final class Plural {

  private Plural() { }

  public static String s( final int cardinality, final String single ) {
    return suffixPlural( cardinality, single, "s" ) ;
  }

  public static String s( final long cardinality, final String single ) {
    return suffixPlural( cardinality, single, "s" ) ;
  }

  public static String es( final int cardinality, final String single ) {
    return suffixPlural( cardinality, single, "es" ) ;
  }

  public static String es( final long cardinality, final String single ) {
    return suffixPlural( cardinality, single, "es" ) ;
  }

  private static String suffixPlural(
      final int cardinality, 
      final String single, 
      final String suffixForPlural 
  ) {
    if( cardinality <= 1 ) {
      return cardinality + " " + single ;
    } else {
      return cardinality + " " + single + suffixForPlural ;
    }
  }
  
  private static String suffixPlural(
      final long cardinality,
      final String single,
      final String suffixForPlural
  ) {
    if( cardinality <= 1 ) {
      return cardinality + " " + single ;
    } else {
      return cardinality + " " + single + suffixForPlural ;
    }
  }

  public static String of( final int cardinality, final String single, final String... more ) {
    if( cardinality <= 1 || more.length == 0 ) {
      return cardinality + " " + single ;
    } else {
      final int normalisedCount = Math.max( 0, cardinality - 2 ) ;
      final int index = Math.min( normalisedCount, more.length ) ;
      return cardinality + " "  + more[ index ] ;
    }
  }

  public static String th( final int index ) {
    final String indexAsString = Integer.toString( index ) ;
    final char lastCharacter = indexAsString.charAt( indexAsString.length() - 1 ) ;
    switch( lastCharacter ) {
      case '1' : return indexAsString + "st" ;
      case '2' : return indexAsString + "nd" ;
      case '3' : return indexAsString + "rd" ;
      default : return indexAsString + "th" ;
    }
  }

  public static String th( final long index ) {
    if( index == 1 ) {
      return "1st" ;
    } else if( index == 2 ) {
      return "2nd" ;
    } else if( index == 3 ) {
      return "3rd" ;
    } else {
      return index + "th" ;
    }
  }

// ============
// Common units
// ============

  public static String bytes( final int count ) {
    return s( count, "byte" ) ;
  }

  public static String bytes( final long count ) {
    return s( count, "byte" ) ;
  }
}
