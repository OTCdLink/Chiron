package io.github.otcdlink.chiron.configuration.showcase;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.configuration.Configuration;
import io.github.otcdlink.chiron.configuration.ConfigurationTools;
import io.github.otcdlink.chiron.configuration.Sources;
import io.github.otcdlink.chiron.configuration.TemplateBasedFactory;
import org.junit.Test;

import static io.github.otcdlink.chiron.configuration.Configuration.Inspector;
import static io.github.otcdlink.chiron.configuration.Configuration.Property.Origin;
import static org.assertj.core.api.Assertions.assertThat;

public class Inspection {

  public interface Simple extends Configuration {
    Integer number() ;
    String string() ;
  }

  @Test
  public void test() throws Exception {
    final Configuration.Factory< Simple > factory
        = new TemplateBasedFactory<Simple>( Simple.class )
    {
      @Override
      protected void initialize() {
        property( using.number() ).defaultValue( 1 ).documentation( "Some number." ) ;
      }
    } ;
    final Simple configuration = factory.create( Sources.newSource( "string = foo" ) ) ;

    final Inspector< Simple > inspector = ConfigurationTools.newInspector( configuration ) ;

    final ImmutableMap< String, Configuration.Property< Simple > > properties
        = inspector.properties() ;
    assertThat( properties ).hasSize( 2 ) ;
    assertThat( properties.get( "number" ).name() ).isEqualTo( "number" ) ;
    assertThat( properties.get( "string" ).name() ).isEqualTo( "string" ) ;

    configuration.number() ;
    {
      final Configuration.Property< Simple > property = inspector.lastAccessed().get( 0 ) ;
      assertThat( property.name() ).isEqualTo( "number" ) ;
      assertThat( property.documentation() ).isEqualTo( "Some number." ) ;
      assertThat( property.defaultValue() ).isEqualTo( 1 ) ;
      assertThat( inspector.origin( property ) ).isEqualTo( Origin.BUILTIN ) ;
    }
    configuration.string() ; {
      final Configuration.Property< Simple > property = inspector.lastAccessed().get( 0 ) ;
      assertThat( property.name() ).isEqualTo( "string" ) ;
      assertThat( property.documentation() ).isEqualTo( "" ) ;
      assertThat( property.defaultValue() ).isNull() ;
      assertThat( inspector.origin( property ) ).isEqualTo( Origin.EXPLICIT ) ;
    }
  }
}
