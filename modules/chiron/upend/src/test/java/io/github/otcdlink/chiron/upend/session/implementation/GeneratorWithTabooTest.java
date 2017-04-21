package io.github.otcdlink.chiron.upend.session.implementation;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratorWithTabooTest {

  @Test
  public void simpleWrap() throws Exception {
    final GeneratorWithTaboo< String > generator = generator( 3, "A", "B", "C", "D" ) ;
    assertThat( generator.generate() ).isEqualTo( "A" ) ;
    assertThat( generator.generate() ).isEqualTo( "B" ) ;
    assertThat( generator.generate() ).isEqualTo( "C" ) ;
    assertThat( generator.generate() ).isEqualTo( "D" ) ;
  }

  @Test
  public void collision() throws Exception {
    final GeneratorWithTaboo< String > generator = generator( 3, "A", "B", "A", "C" ) ;
    assertThat( generator.generate() ).isEqualTo( "A" ) ;
    assertThat( generator.generate() ).isEqualTo( "B" ) ;
    assertThat( generator.generate() ).isEqualTo( "C" ) ;
  }

// =======
// Fixture
// =======

  private static GeneratorWithTaboo< String > generator(
      final int tabooSize,
      final String ... values
  ) {
    return generator( tabooSize, ImmutableList.copyOf( values).iterator() ) ;
  }

  private static GeneratorWithTaboo< String > generator(
      final int tabooSize,
      final Iterator< String > values
  ) {
    return new GeneratorWithTaboo<>(
        tabooSize,
        values::next,
        String::equals
    ) ;
  }

}