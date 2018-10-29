package com.otcdlink.chiron.configuration.showcase;

import com.google.common.base.Converter;
import com.otcdlink.chiron.configuration.Configuration;
import com.otcdlink.chiron.configuration.ConfigurationTools;
import com.otcdlink.chiron.configuration.Sources;
import com.otcdlink.chiron.configuration.TemplateBasedFactory;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Obfuscation {

  public interface Obfuscated extends Configuration {
    String credential() ;
  }

  @Test
  public void test() throws Exception {
    final Configuration.Factory< Obfuscated > factory
        = new TemplateBasedFactory< Obfuscated >( Obfuscated.class )
    {
      @Override
      protected void initialize() {
        property( using.credential() ).converter( Converter.from( s -> s, s -> "****" ) ) ;
      }
    } ;
    System.out.println( "Properties: " + factory.properties() ) ;

    final Obfuscated obfuscated = factory.create( Sources.newSource( "credential = foo:bar" ) ) ;
    final Configuration.Inspector< Obfuscated > inspector =
        ConfigurationTools.newInspector( obfuscated ) ;

    assertThat( obfuscated.credential() ).isEqualTo( "foo:bar" ) ;
    assertThat( inspector.stringValueOf( inspector.lastAccessed().get( 0 ) ) )
        .isEqualTo( "****" ) ;


  }
}
