package io.github.otcdlink.chiron.configuration.showcase;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.configuration.Configuration;
import io.github.otcdlink.chiron.configuration.Sources;
import io.github.otcdlink.chiron.configuration.TemplateBasedFactory;
import io.github.otcdlink.chiron.configuration.TweakedValue;
import org.junit.Test;

import static io.github.otcdlink.chiron.configuration.Configuration.Factory;
import static io.github.otcdlink.chiron.configuration.Configuration.Inspector;
import static io.github.otcdlink.chiron.configuration.Configuration.Property;
import static io.github.otcdlink.chiron.configuration.ConfigurationTools.newInspector;
import static org.assertj.core.api.Assertions.assertThat;

public class Tweaking {

  public interface Simple extends Configuration {
    int number() ;
    Boolean positive() ;
  }

  @Test
  public void testFlat() throws Exception {
    final Factory< Simple > factory = newFactoryWithTweak() ;
    final Simple configuration = factory.create( Sources.newSource( "number = -1" ) ) ;

    final Inspector< Simple > inspector = newInspector( configuration ) ;
    assertThat( configuration.number( ) ).isEqualTo( -1 ) ;
    assertThat( configuration.positive() ).isEqualTo( Boolean.FALSE ) ;

    final Property< Simple > positiveProperty = inspector.last() ;
    assertThat( inspector.stringValueOf( positiveProperty ) ).isEqualTo( "-1" ) ;
    assertThat( inspector.origin( positiveProperty ) ).isEqualTo( Property.Origin.TWEAK ) ;
  }

  @Test
  public void testInherited() throws Exception {
    final Factory< Simple > factory = newFactoryWithTweak() ;
    final Simple configuration = factory.create(
        Sources.newSource( "number = -2" ),
        Sources.newSource( "number = 3" )
    ) ;

    final Inspector< Simple > inspector = newInspector( configuration ) ;
    assertThat( configuration.number( ) ).isEqualTo( 3 ) ;
    assertThat( configuration.positive() ).isEqualTo( Boolean.TRUE ) ;

    final Property< Simple > positiveProperty = inspector.last() ;
    assertThat( inspector.stringValueOf( positiveProperty ) ).isEqualTo( "+1" ) ;
    assertThat( inspector.origin( positiveProperty ) ).isEqualTo( Property.Origin.TWEAK ) ;
  }

  private static TemplateBasedFactory<Simple> newFactoryWithTweak() {
    return new TemplateBasedFactory<Simple>( Simple.class ) {
      @Override
      protected void initialize() {
        property( using.positive() ).defaultValue( null ) ;
      }

      @Override
      protected ImmutableMap< Property< Simple >, TweakedValue > tweak(
          final Simple configuration
      ) {
        final Inspector< Simple > inspector = newInspector( configuration ) ;
        final int number = configuration.number() ;
        final Boolean sign = configuration.positive() ;
        final Property< Simple > positiveProperty = inspector.lastAccessed().get( 0 ) ;
        if ( sign == null ) {
          final TweakedValue tweakedValue ;
          if ( number > 0 ) {
            tweakedValue = new TweakedValue( true, "+1" ) ;
          } else if ( number < 0 ) {
            tweakedValue = new TweakedValue( false, "-1" ) ;
          } else {
            tweakedValue = new TweakedValue( null, "0" ) ;
          }
          return ImmutableMap.of( positiveProperty, tweakedValue ) ;
        }
        return null ;
      }
    };
  }

}
