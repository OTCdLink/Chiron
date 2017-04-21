package io.github.otcdlink.chiron.configuration;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EqualityAndHashCode {


  @SuppressWarnings( "UnusedDeclaration" )
  public interface Simple extends Configuration {
    int myNumber() ;
    String myString() ;
  }

  @Test
  public void test() throws Exception {
    final Configuration.Factory< Simple > factory = ConfigurationTools.newFactory( Simple.class ) ;
    final Configuration.Source source = Sources.newSource(
        "myNumber = 123",
        "myString = foo"
    ) ;
    final Simple configuration1 = factory.create( source ) ;
    final Simple configuration2 = factory.create( source ) ;

    assertThat( configuration1 ).isEqualTo( configuration2 ) ;
  }

}
