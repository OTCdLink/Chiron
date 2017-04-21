package io.github.otcdlink.chiron.configuration.showcase;

import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.configuration.Configuration;
import io.github.otcdlink.chiron.configuration.ConfigurationTools;
import io.github.otcdlink.chiron.configuration.Converters;
import io.github.otcdlink.chiron.configuration.NameTransformers;
import io.github.otcdlink.chiron.configuration.Obfuscators;
import io.github.otcdlink.chiron.configuration.Sources;
import io.github.otcdlink.chiron.configuration.TemplateBasedFactory;
import org.junit.Test;

import java.util.regex.Pattern;

import static io.github.otcdlink.chiron.configuration.Configuration.Inspector;
import static io.github.otcdlink.chiron.configuration.Validation.Accumulator;
import static io.github.otcdlink.chiron.configuration.Validation.Bad;
import static org.assertj.core.api.Assertions.assertThat;

public class ComplexUsage {

  public interface Simple extends Configuration {
    Integer myNumber() ;
    String myString() ;
  }

  @Test
  public void test() throws Exception {
    final Configuration.Factory< Simple > factory ;
    factory = new TemplateBasedFactory< Simple >( Simple.class ) {
      @Override
      protected void initialize() {
        property( using.myNumber() )
            .name( "my-binary-number" )
            .maybeNull()
            .converter( Converters.from( input -> Integer.parseInt( input, 2 ) ) )
            .documentation( "Just a number." )
        ;
        property( using.myString() )
            .defaultValue( "FOO" )
            .documentation( "Just a string." )
            .obfuscator( Obfuscators.from( Pattern.compile( "OO" ) ) )
        ;
        setGlobalNameTransformer( NameTransformers.LOWER_HYPHEN ) ;
      }

      @Override
      protected ImmutableList< Bad > validate( final Simple configuration ) {
        final Accumulator< Simple > accumulator = new Accumulator<>( configuration ) ;
        if( configuration.myNumber() != null ) {
          accumulator.verify( configuration.myNumber() > 0, "Must be > 0" ) ;
        }
        accumulator.verify(
            configuration.myString().equals( configuration.myString().toUpperCase() ),
            "Must be upper case"
        ) ;
        return accumulator.done() ;
      }
    } ;

    final Simple configuration = factory.create( Sources.newSource(
        "my-binary-number = 1111011" ) ) ;

    final Inspector< Simple > inspector = ConfigurationTools.newInspector( configuration ) ;
    assertThat( configuration.myNumber() ).isEqualTo( 123 ) ;
    assertThat( inspector.origin( inspector.lastAccessed().get( 0 ) ) )
        .isEqualTo( Configuration.Property.Origin.EXPLICIT ) ;
    assertThat( configuration.myString() ).isEqualTo( "FOO" ) ;
    assertThat( inspector.origin( inspector.lastAccessed().get( 0 ) ) )
        .isEqualTo( Configuration.Property.Origin.BUILTIN ) ;
    assertThat( inspector.lastAccessed().get( 0 ).name() ).isEqualTo( "my-string" ) ;
    assertThat( inspector.safeValueOf( inspector.lastAccessed().get( 0 ), "*" ) )
        .isEqualTo( "F*" ) ;
  }


}
