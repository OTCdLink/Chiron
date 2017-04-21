package io.github.otcdlink.chiron.configuration.showcase;

import io.github.otcdlink.chiron.configuration.Configuration;
import io.github.otcdlink.chiron.configuration.ConfigurationTools;
import io.github.otcdlink.chiron.configuration.Sources;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimplestUsage {

  public interface Simple extends Configuration {
    int myNumber() ;
    String myString() ;
  }

  @Test
  public void test() throws Exception {
    final Configuration.Factory< Simple > factory = ConfigurationTools.newFactory( Simple.class ) ;
    final Simple configuration = factory.create( Sources.newSource(
        "myNumber = 123",
        "myString = foo"
    ) ) ;

    assertThat( configuration.myNumber() ).isEqualTo( 123 ) ;
    assertThat( configuration.myString() ).isEqualTo( "foo" ) ;
    assertThat( configuration.toString() )
        .isEqualTo( "SimplestUsage$Simple{myNumber=123; myString=foo}" ) ;
  }

}
