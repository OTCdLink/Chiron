package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.UnmodifiableIterator;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.junit.Ignore;
import org.junit.Test;

import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DiagnosticTest {

  @Test
  @Ignore( "Investigative work")
  public void validateMultimapBehavior() throws Exception {
    final ImmutableMultimap.Builder< Integer, String > builder = ImmutableMultimap.builder() ;
    builder.put( 2, "C" ) ;
    builder.put( 2, "B" ) ;
    builder.put( 1, "A" ) ;
    final ImmutableMultimap< Integer, String > multimap = builder.build() ;
    final UnmodifiableIterator< Map.Entry< Integer, String > > iterator =
        multimap.entries().iterator() ;
    final Map.Entry< Integer, String > entry2c = iterator.next() ;
    assertThat( entry2c.getKey() ).isEqualTo( 2 ) ;
    assertThat( entry2c.getValue() ).isEqualTo( "C" ) ;
    final Map.Entry< Integer, String > entry2b = iterator.next() ;
    assertThat( entry2b.getKey() ).isEqualTo( 2 ) ;
    assertThat( entry2b.getValue() ).isEqualTo( "B" ) ;
    final Map.Entry< Integer, String > entry1a = iterator.next() ;
    assertThat( entry1a.getKey() ).isEqualTo( 1 ) ;
    assertThat( entry1a.getValue() ).isEqualTo( "A" ) ;
  }

  @Test
  public void printRecursively() throws Exception {
    final StringWriter stringWriter = new StringWriter() ;
    new Root().print( stringWriter ) ;
    assertThat( stringWriter.toString() ).isEqualTo(
        "Root\n" +
        "  Nested1\n" +
        "    One\n" +
        "  Nested2\n" +
        "    Two\n"
    ) ;
  }
  @Test
  public void wrap() throws Exception {
    final StringWriter stringWriter = new StringWriter() ;
    new Wrapped().print(
        stringWriter,
        2,
        "  ",
        " => ",
        20,
        Diagnostic.WrapStyle.SOFT,
        LineBreak.SYSTEM
    ) ;
    assertThat( stringWriter.toString() ).isEqualTo(
        "    Wrapped\n" +
        "      This is a very long\n" +
        "      line that should be\n" +
        "      wrapped\n"
    ) ;
  }

  @Test
  public void dumpComplete() throws Exception {
    new GeneralDiagnostic().print( new OutputStreamWriter( System.out ) ) ;
  }

  // =======
// Fixture
// =======


  private static class Nested1 extends BaseDiagnostic {
    Nested1() {
      super( ImmutableMultimap.of( NO_KEY, "One" ) ) ;
    }
  }

  private static class Nested2 extends BaseDiagnostic {
    Nested2() {
      super( ImmutableMultimap.of( NO_KEY, "Two" ) ) ;
    }
  }

  private static class Wrapped extends BaseDiagnostic {
    Wrapped() {
      super( ImmutableMultimap.of( NO_KEY, "This is a very long line that should be wrapped" ) ) ;
    }
  }

  private static class Root extends BaseDiagnostic {

    protected Root() {
      super( ImmutableMultimap.of(), ImmutableList.of( new Nested1(), new Nested2() ) ) ;
    }

  }

}
