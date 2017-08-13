package com.otcdlink.chiron.configuration;

import org.junit.Test;

import static com.otcdlink.chiron.configuration.Configuration.Inspector;
import static org.assertj.core.api.Assertions.assertThat;

public class InspectorKnowsSources {

  public interface Simple extends Configuration {
    int number() ;
    String string() ;
  }

  @Test
  public void test() throws Exception {
    final Configuration.Factory< Simple > factory = ConfigurationTools.newFactory( Simple.class );

    final Configuration.Source source1 = Sources.newSource( "number = 33" ) ;
    final Configuration.Source source2 = Sources.newSource( "string = foo" ) ;
    final Simple configuration = factory.create( source2, source1 ) ;

    final Inspector< Simple > inspector = ConfigurationTools.newInspector( configuration ) ;

    assertThat( inspector.sources() ).contains( source1, source2 ) ;

    System.out.println( "Sources: " ) ;
    for( final Configuration.Source source : inspector.sources() ) {
      System.out.println( "  " + source.sourceName() ) ;
    }
  }
}
