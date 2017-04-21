package io.github.otcdlink.chiron.lab.integration;

import io.github.otcdlink.chiron.lab.middle.LabDownwardDuty;
import io.github.otcdlink.chiron.lab.middle.command.LabDownwardCommandCrafter;
import io.github.otcdlink.chiron.lab.middle.command.LabDownwardCommandResolver;
import io.github.otcdlink.chiron.middle.CommandFailureNotice;
import io.github.otcdlink.chiron.middle.shaft.CodecShaft;
import io.github.otcdlink.chiron.middle.shaft.CodecShaft.Uninvolved;
import io.github.otcdlink.chiron.middle.shaft.MethodCallVerifier;
import io.github.otcdlink.chiron.middle.shaft.MethodCaller;
import org.junit.Test;

public class LabShaftTest {

  @Test
  public void codecShaft() throws Exception {
    final CodecShaft< Uninvolved, LabDownwardDuty< Uninvolved > > codecShaft =
        new CodecShaft<>(
            LabDownwardCommandCrafter::new,
            new LabDownwardCommandResolver<>(),
            Uninvolved.INSTANCE
        )
    ;
    codecShaft.submit( DOWNWARD_METHOD_CALLER, new MethodCallVerifier.Skipping( 0 ) ) ;
  }

// =======
// Fixture
// =======

  private static final MethodCaller<LabDownwardDuty<Uninvolved>>
      DOWNWARD_METHOD_CALLER =
      new MethodCaller.Default< LabDownwardDuty< Uninvolved > >() {
        @Override
        public void callMethods( final LabDownwardDuty<Uninvolved> duty ) {
          /** Don't try to test {@link LabDownwardDuty#failure(Object, CommandFailureNotice)}. */
          duty.counter(
              Uninvolved.INSTANCE,
              1
          ) ;
        }
      }
  ;
}