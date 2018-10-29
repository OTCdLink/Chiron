package com.otcdlink.chiron.configuration.showcase;

import com.google.common.base.Converter;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.configuration.Configuration;
import com.otcdlink.chiron.configuration.ConfigurationTools;
import com.otcdlink.chiron.configuration.NameTransformers;
import com.otcdlink.chiron.configuration.Sources;
import com.otcdlink.chiron.configuration.TemplateBasedFactory;
import com.otcdlink.chiron.toolbox.converter.DefaultStringConverters;
import org.junit.Test;

import static com.otcdlink.chiron.configuration.Configuration.Inspector;
import static com.otcdlink.chiron.configuration.Validation.Accumulator;
import static com.otcdlink.chiron.configuration.Validation.Bad;
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
            .converter( DefaultStringConverters.from( input -> Integer.parseInt( input, 2 ) ) )
            .documentation( "Just a number." )
        ;
        property( using.myString() )
            .defaultValue( "FOO" )
            .converter( Converter.from( s -> s, s -> "***" ) )
            .documentation( "Just a string." )
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
    assertThat( inspector.stringValueOf( inspector.lastAccessed().get( 0 ) ) )
        .isEqualTo( "***" ) ;
  }


}
