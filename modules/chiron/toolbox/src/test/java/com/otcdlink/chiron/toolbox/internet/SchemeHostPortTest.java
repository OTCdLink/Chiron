package com.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SchemeHostPortTest {

  @Test
  public void create() throws Exception {
    final SchemeHostPort schemeHostPort = SchemeHostPort.create( SchemeHostPort.Scheme.HTTP, "foo.com", 83 ) ;

    assertThat( schemeHostPort.scheme ).isSameAs( SchemeHostPort.Scheme.HTTP ) ;

    assertThat( schemeHostPort.hostnameAsString() ).isEqualTo( "foo.com" ) ;

    assertThat( schemeHostPort.port() ).isEqualTo( 83 ) ;

    assertThat( schemeHostPort.uriString() ).isEqualTo( "http://foo.com:83" ) ;


    assertThat( SchemeHostPort.create( SchemeHostPort.Scheme.HTTP, "foo", -1 ).uriString() )
        .isEqualTo( "http://foo:80" ) ;

    assertThatThrownBy( () -> SchemeHostPort.create( SchemeHostPort.Scheme.HTTP, "!bad!", 1 ) )
        .isInstanceOf( SchemeHostPort.CreationException.class ) ;
  }


  @Test
  public void canonicalUrl() throws Exception {

    assertThat( SchemeHostPort.create( SchemeHostPort.Scheme.HTTP, "foo.com", 83 ).asCanonicalUrl().toExternalForm() )
        .isEqualTo( "http://foo.com:83" ) ;

    assertThat( SchemeHostPort.create( SchemeHostPort.Scheme.HTTP, "foo.com", 80 ).asCanonicalUrl().toExternalForm() )
        .isEqualTo( "http://foo.com" ) ;

    assertThat( SchemeHostPort.create( SchemeHostPort.Scheme.HTTPS, "foo.com", 443 ).asCanonicalUrl().toExternalForm() )
        .isEqualTo( "https://foo.com" ) ;

  }

  @Test
  public void parse() throws Exception {

    assertThat( SchemeHostPort.parse( "http://foo.com" ) )
        .isEqualTo( SchemeHostPort.create( SchemeHostPort.Scheme.HTTP, "foo.com", 80 ) ) ;

    assertThat( SchemeHostPort.parse( "http://foo.com:8082" ) )
        .isEqualTo( SchemeHostPort.create( SchemeHostPort.Scheme.HTTP, "foo.com", 8082 ) ) ;

    assertThatThrownBy( () -> SchemeHostPort.parse( "!bad!" ) )
        .isInstanceOf( SchemeHostPort.ParseException.class ) ;

  }
}