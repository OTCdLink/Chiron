package io.github.otcdlink.chiron.toolbox.diagnostic;

import mockit.FullVerifications;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DiagnosticUpdateTest {

  @Test
  public void createUpdateRemove(
      @Injectable final DiagnosticUpdate.Listener< Diagnostic > listener1,
      @Injectable final DiagnosticUpdate.Listener< Diagnostic > listener2
  )
      throws Exception
  {
    final DiagnosticUpdate.Default< Diagnostic > diagnosticUpdater =
        new DiagnosticUpdate.Default<>( Diagnostic.NULL ) ;
    assertThat( diagnosticUpdater.current() ).isSameAs( Diagnostic.NULL ) ;
    diagnosticUpdater.addListener( listener1 ) ;
    diagnosticUpdater.addListener( listener2 ) ;

    new StrictExpectations() {{
      listener1.updated( DIAGNOSTIC ) ;
      listener2.updated( DIAGNOSTIC ) ;
    }} ;
    diagnosticUpdater.update( DIAGNOSTIC ) ;
    new FullVerifications() {{ }} ;

    new StrictExpectations() {{
      listener1.updated( DIAGNOSTIC ) ;
    }} ;
    diagnosticUpdater.removeListener( listener2 ) ;
    diagnosticUpdater.update( DIAGNOSTIC ) ;
    new FullVerifications() {{ }} ;

  }

// =======
// Fixture
// =======

  private final Diagnostic DIAGNOSTIC = new AbstractDiagnostic( 0, "  " ) {
    @Override
    public String name() {
      return "Just for test" ;
    }
  } ;
}