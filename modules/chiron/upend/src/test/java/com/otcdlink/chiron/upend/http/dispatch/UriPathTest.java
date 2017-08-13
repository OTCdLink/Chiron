package com.otcdlink.chiron.upend.http.dispatch;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UriPathTest {

  @Test
  public void root() throws Exception {
    assertThat( UriPath.ROOT.isRoot() ).isTrue() ;
    assertThat( UriPath.append( null, null ).isRoot() ).isTrue() ;
    assertThat( UriPath.from().isRoot() ).isTrue() ;
    assertThat( UriPath.from( "" ).isRoot() ).isTrue() ;
    assertThat( UriPath.from( "/" ).isRoot() ).isTrue() ;
    assertThat( UriPath.from( "x" ).isRoot() ).isFalse() ;
  }

  @Test
  public void concatenate() throws Exception {
    assertThat( UriPath.ROOT.append( "x" ).append( "y" ).fullPath ).isEqualTo( "/x/y" ) ;

    assertThat( UriPath.ROOT.append( "x" ).append( "/y/" ).append( "z" ).fullPath )
        .isEqualTo( "/x/y/z" ) ;
  }

  @Test
  public void fullPath() {
    assertThat( UriPath.from().fullPath ).isEqualTo( "/" ) ;

    assertThat( UriPath.from( "" ).fullPath ).isEqualTo( "/" ) ;
    assertThat( UriPath.from( "" ).fullPathWithTrailingSlash ).isEqualTo( "/" ) ;

    assertThat( UriPath.from( "xx" ).fullPath ).isEqualTo( "/xx" ) ;
    assertThat( UriPath.from( "xx" ).fullPathWithTrailingSlash ).isEqualTo( "/xx/" ) ;

    assertThat( UriPath.from( "/xx" ).fullPath ).isEqualTo( "/xx" ) ;
    assertThat( UriPath.from( "/xx" ).fullPathWithTrailingSlash ).isEqualTo( "/xx/" ) ;

    assertThat( UriPath.from( "/xx/" ).fullPath ).isEqualTo( "/xx" ) ;
    assertThat( UriPath.from( "/xx/" ).fullPathWithTrailingSlash ).isEqualTo( "/xx/" ) ;

    assertThat( UriPath.from( "xx/" ).fullPath ).isEqualTo( "/xx" ) ;
    assertThat( UriPath.from( "xx/" ).fullPathWithTrailingSlash ).isEqualTo( "/xx/" ) ;

    assertThat( UriPath.from( "x", "y" ).fullPath ).isEqualTo( "/x/y" ) ;
    assertThat( UriPath.from( "x", "y" ).fullPathWithTrailingSlash )
        .isEqualTo( "/x/y/" ) ;

    assertThat( UriPath.from( "xx/x" ).fullPath ).isEqualTo( "/xx/x" ) ;
    assertThat( UriPath.from( "x/y" ).fullPathWithTrailingSlash )
        .isEqualTo( "/x/y/" ) ;
  }

  @Test
  public void rootMatch() {
    assertThat( UriPath.ROOT.pathMatch( "" ) )
        .isEqualTo( UriPath.MatchKind.TOTAL_MATCH ) ;
    assertThat( UriPath.ROOT.pathMatch( "/" ) )
        .isEqualTo( UriPath.MatchKind.TOTAL_MATCH_WITH_TRAILING_SLASH ) ;
    assertThat( UriPath.ROOT.pathMatch( "x" ) )
        .isEqualTo( UriPath.MatchKind.RADIX_MATCH ) ;
    assertThat( UriPath.ROOT.pathMatch( "/x" ) )
        .isEqualTo( UriPath.MatchKind.RADIX_MATCH ) ;
  }

  @Test
  public void radixMatch() {
    assertThat( UriPath.from( "x/y" ).pathMatch( "/x/y/z" ) )
        .isEqualTo( UriPath.MatchKind.RADIX_MATCH_WITH_TRAILING_SLASH ) ;
  }

  @Test
  public void totalMatch() {
    assertThat( UriPath.from( "x/y/z" ).pathMatch( "/x/y/z/" ) )
        .isEqualTo( UriPath.MatchKind.TOTAL_MATCH_WITH_TRAILING_SLASH ) ;
  }

  @Test
  public void trailingSlash() {
    assertThat( UriPath.from( "x/y" ).pathMatch( "/x/y" ) )
        .isEqualTo( UriPath.MatchKind.TOTAL_MATCH ) ;
    assertThat( UriPath.from( "x/y" ).pathMatch( "/x/y/" ) )
        .isEqualTo( UriPath.MatchKind.TOTAL_MATCH_WITH_TRAILING_SLASH ) ;
  }

  @Test
  public void noMatch() {
    assertThat( UriPath.from( "x/y" ).pathMatch( "/x" ) )
        .isEqualTo( UriPath.MatchKind.NO_MATCH ) ;
  }

  @Test
  public void relativize() {
    assertThat( UriPath.from( "x" ).relativizeFromUnslashedPath( "/x/y" ) ).isEqualTo( "/y" ) ;
    assertThat( UriPath.from( "x" ).relativizeFromSlashedPath( "/x/y" ) ).isEqualTo( "y" ) ;

    assertThat( UriPath.from( "x" ).relativizeFromUnslashedPath( "x/y" ) ).isEqualTo( "/y" ) ;
    assertThat( UriPath.from( "x" ).relativizeFromSlashedPath( "x/y" ) ).isEqualTo( "y" ) ;

    assertThat( UriPath.from( "x" ).relativizeFromUnslashedPath( "x" ) ).isEqualTo( "" ) ;
    assertThat( UriPath.from( "x" ).relativizeFromSlashedPath( "x" ) ).isNull() ;

    assertThat( UriPath.from( "x" ).relativizeFromSlashedPath( "x/" ) ).isEqualTo( "" ) ;
    assertThat( UriPath.from( "x" ).relativizeFromSlashedPath( "x/" ) ).isEqualTo( "" ) ;

    assertThat( UriPath.from( "X" ).relativizeFromUnslashedPath( "y" ) ).isNull() ;

    assertThatThrownBy( () ->
        UriPath.from( "x" ).relativize( "x/../y", true )
    ).isInstanceOf( IllegalArgumentException.class ) ;
  }

  @Test
  public void startsWithSegments() throws Exception {
    checkStartsWithSegments( "", "",true ) ;
    checkStartsWithSegments( "/", "/",true ) ;
    checkStartsWithSegments( "/", "",true ) ;
    checkStartsWithSegments( "", "/",false ) ;
    checkStartsWithSegments( "x", "x",true ) ;
    checkStartsWithSegments( "x/", "x",true ) ;
    checkStartsWithSegments( "x", "x/",false ) ;

    checkStartsWithSegments( "x/y", "x/y",true ) ;
    checkStartsWithSegments( "x/y", "x/",true ) ;
    checkStartsWithSegments( "x/y", "x",true ) ;
    checkStartsWithSegments( "x/y", "",true ) ;
    checkStartsWithSegments( "x/y", "z",false ) ;

    checkStartsWithSegments( "x/yz", "x",true ) ;
    checkStartsWithSegments( "x/yz", "x/",true ) ;
    checkStartsWithSegments( "x/yz", "x/yz",true ) ;
    checkStartsWithSegments( "x/yz", "x/y",false ) ;

  }

// =======
// Fixture
// =======

  private static void checkStartsWithSegments( 
      final String path, 
      final String radix, 
      final boolean result 
  ) {
    assertThat( UriPath.startsWithSegments( path, radix ) )
        .describedAs( "path='" + path + "', radix='" + radix + "'" )
        .isEqualTo( result ) 
    ;
    
  }
}