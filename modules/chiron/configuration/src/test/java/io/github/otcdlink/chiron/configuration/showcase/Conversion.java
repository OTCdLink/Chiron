package io.github.otcdlink.chiron.configuration.showcase;

import io.github.otcdlink.chiron.configuration.Configuration;
import io.github.otcdlink.chiron.configuration.Converters;
import io.github.otcdlink.chiron.configuration.TemplateBasedFactory;
import org.junit.Test;

import java.util.regex.Pattern;

import static io.github.otcdlink.chiron.configuration.Configuration.Factory;
import static io.github.otcdlink.chiron.configuration.Sources.newSource;
import static org.assertj.core.api.Assertions.assertThat;

public class Conversion {

  public interface Converted extends Configuration {
    Pattern pattern() ;
  }

  @Test
  public void test() throws Exception {
    final Factory< Converted > factory = new TemplateBasedFactory< Converted >( Converted.class ) {
      @Override
      protected void initialize() {
        property( using.pattern() ).converter( Converters.from( Pattern::compile ) ) ;
      }
    } ;
    final Converted configuration = factory.create( newSource( "pattern = .*" ) ) ;

    assertThat( configuration.pattern().pattern() ).isEqualTo( ".*" ) ;
  }
}
