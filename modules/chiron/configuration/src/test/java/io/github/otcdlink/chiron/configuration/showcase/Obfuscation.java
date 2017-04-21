package io.github.otcdlink.chiron.configuration.showcase;

import io.github.otcdlink.chiron.configuration.Configuration;
import io.github.otcdlink.chiron.configuration.Obfuscators;
import io.github.otcdlink.chiron.configuration.TemplateBasedFactory;
import org.junit.Test;

import java.util.regex.Pattern;

import static io.github.otcdlink.chiron.configuration.ConfigurationTools.newInspector;
import static io.github.otcdlink.chiron.configuration.Sources.newSource;
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

    final Obfuscated obfuscated = factory.create( newSource( "credential = foo:bar" ) ) ;
    final Configuration.Inspector< Obfuscated > inspector = newInspector( obfuscated ) ;

    assertThat( obfuscated.credential() ).isEqualTo( "foo:bar" ) ;
    assertThat( inspector.safeValueOf( inspector.lastAccessed().get( 0 ), "[undisclosed]" ) )
        .isEqualTo( "foo:[undisclosed]" ) ;


  }
}
