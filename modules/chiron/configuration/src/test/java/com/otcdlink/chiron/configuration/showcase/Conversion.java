package com.otcdlink.chiron.configuration.showcase;

import com.otcdlink.chiron.configuration.Configuration;
import com.otcdlink.chiron.configuration.Converters;
import com.otcdlink.chiron.configuration.Sources;
import com.otcdlink.chiron.configuration.TemplateBasedFactory;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class Conversion {

  public interface Converted extends Configuration {
    Pattern pattern() ;
  }

  @Test
  public void test() throws Exception {
    final Configuration.Factory< Converted > factory = new TemplateBasedFactory< Converted >( Converted.class ) {
      @Override
      protected void initialize() {
        property( using.pattern() ).converter( Converters.from( Pattern::compile ) ) ;
      }
    } ;
    final Converted configuration = factory.create( Sources.newSource( "pattern = .*" ) ) ;

    assertThat( configuration.pattern().pattern() ).isEqualTo( ".*" ) ;
  }
}
