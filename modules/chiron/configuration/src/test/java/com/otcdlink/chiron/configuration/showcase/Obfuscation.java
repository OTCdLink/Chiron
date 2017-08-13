package com.otcdlink.chiron.configuration.showcase;

import com.otcdlink.chiron.configuration.Configuration;
import com.otcdlink.chiron.configuration.ConfigurationTools;
import com.otcdlink.chiron.configuration.Obfuscators;
import com.otcdlink.chiron.configuration.Sources;
import com.otcdlink.chiron.configuration.TemplateBasedFactory;
import org.junit.Test;

import java.util.regex.Pattern;

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
        property( using.credential() ).obfuscator( Obfuscators.from(
            Pattern.compile( "(?<=^.*:).*" )
        ) ) ;
      }
    } ;
    System.out.println( "Properties: " + factory.properties() ) ;

    final Obfuscated obfuscated = factory.create( Sources.newSource( "credential = foo:bar" ) ) ;
    final Configuration.Inspector< Obfuscated > inspector = ConfigurationTools.newInspector( obfuscated ) ;

    assertThat( obfuscated.credential() ).isEqualTo( "foo:bar" ) ;
    assertThat( inspector.safeValueOf( inspector.lastAccessed().get( 0 ), "[undisclosed]" ) )
        .isEqualTo( "foo:[undisclosed]" ) ;


  }
}
