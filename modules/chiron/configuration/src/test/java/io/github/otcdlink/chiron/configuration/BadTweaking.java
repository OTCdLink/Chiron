package io.github.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import static io.github.otcdlink.chiron.configuration.Configuration.Factory;
import static io.github.otcdlink.chiron.configuration.Configuration.Inspector;
import static io.github.otcdlink.chiron.configuration.Configuration.Property;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class BadTweaking {

  public interface Simple extends Configuration {
    int number() ;
  }

  @Test
  public void test() throws Exception {
    final Factory< Simple > factory = new TemplateBasedFactory<Simple>( Simple.class )
    {
      @Override
      protected ImmutableMap< Property< Simple >, TweakedValue> tweak(
          final Simple configuration
      ) {
        final Inspector< Simple > inspector = ConfigurationTools.newInspector( configuration ) ;
        configuration.number() ;
        final Property< Simple > numberProperty = inspector.lastAccessed().get( 0 ) ;
        return ImmutableMap.of( numberProperty, new TweakedValue( "bad", "0" ) ) ;
      }
    } ;


    try {
      factory.create( Sources.newSource( "" ) ) ;
      fail( "Should have thrown an exception" ) ;
    } catch ( final DeclarationException e ) {
      System.out.println( e.getMessage() ) ;
      assertThat( e.getMessage() ).contains(
          "[ number ] Can't assign a value of type java.lang.String to a property of type int" ) ;
    }

  }
}
