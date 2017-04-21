package io.github.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableList;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;

public class DiagnosticTest {


  @Test
  public void printRecursively() throws Exception {
    final StringWriter stringWriter = new StringWriter() ;
    new Root( 0, "  " ).print( stringWriter ) ;
    Assertions.assertThat( stringWriter.toString() ).isEqualTo(
        "Root\n" +
        "  Nested1\n" +
        "    One\n" +
        "  Nested2\n" +
        "    Two\n"
    ) ;
  }

  @Test
  public void dumpComplete() throws Exception {
    new GeneralDiagnostic().print( new OutputStreamWriter( System.out ) ) ;
  }

  // =======
// Fixture
// =======


  private static class Nested1 extends AbstractDiagnostic {
    protected Nested1( final int depth, final String indent ) {
      super( depth, indent ) ;
    }
    @Override
    protected void printSelf( final Writer writer ) throws IOException {
      printBodyLine( writer, "One" ) ;
    }
  }

  private static class Nested2 extends AbstractDiagnostic {
    protected Nested2( final int depth, final String indent ) {
      super( depth, indent ) ;
    }
    @Override
    protected void printSelf( final Writer writer ) throws IOException {
      printBodyLine( writer, "Two" ) ;
    }
  }

  private static class Root extends AbstractDiagnostic {

    protected Root( final int depth, final String indent ) {
      super( depth, indent ) ;
    }

    @Override
    public ImmutableList<Diagnostic> subDiagnostics() {
      return ImmutableList.< Diagnostic >of(
          new Nested1( increasedDepth, indent ),
          new Nested2( increasedDepth, indent )
      ) ;
    }
  }

}
