package io.github.otcdlink.chiron.toolbox.text;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class PluralTest {

  @Test
  public void single() throws Exception {
    assertThat( Plural.of( 0, "fo" ) ).isEqualTo( "0 fo" ) ;
    assertThat( Plural.of( 0, "fo", "foo" ) ).isEqualTo( "0 fo" ) ;
  }

  @Test
  public void one() throws Exception {
    assertThat( Plural.of( 1, "fo" ) ).isEqualTo( "1 fo" ) ;
    assertThat( Plural.of( 1, "fo", "foo" ) ).isEqualTo( "1 fo" ) ;
    assertThat( Plural.of( 1, "fo", "foo", "fooo" ) ).isEqualTo( "1 fo" ) ;
  }

  @Test
  public void two() throws Exception {
    assertThat( Plural.of( 2, "fo" ) ).isEqualTo( "2 fo" ) ;
    assertThat( Plural.of( 2, "fo", "foo" ) ).isEqualTo( "2 foo" ) ;
    assertThat( Plural.of( 2, "fo", "foo", "fooo" ) ).isEqualTo( "2 foo" ) ;
  }

  @Test
  public void th() throws Exception {
    assertThat( Plural.th( 0 ) ).isEqualTo( "0th" ) ;
    assertThat( Plural.th( 1 ) ).isEqualTo( "1st" ) ;
    assertThat( Plural.th( 2 ) ).isEqualTo( "2nd" ) ;
    assertThat( Plural.th( 3 ) ).isEqualTo( "3rd" ) ;
    assertThat( Plural.th( 4 ) ).isEqualTo( "4th" ) ;
    assertThat( Plural.th( 5 ) ).isEqualTo( "5th" ) ;
    assertThat( Plural.th( 21 ) ).isEqualTo( "21st" ) ;
    assertThat( Plural.th( 22 ) ).isEqualTo( "22nd" ) ;
    assertThat( Plural.th( 23 ) ).isEqualTo( "23rd" ) ;
    assertThat( Plural.th( 24 ) ).isEqualTo( "24th" ) ;
  }
}